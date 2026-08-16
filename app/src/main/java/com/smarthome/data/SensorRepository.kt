package com.smarthome.data

import kotlinx.coroutines.flow.Flow

// pm25/vocIndex are opportunistic: only an air-quality-capable sensor (e.g.
// an IKEA VINDSTYRKA) reports them - see smarthomeapi's model.Sensor doc
// comment. Defaults to 0f so a server predating this field (or a Pi agent
// that hasn't been redeployed yet) deserializes fine instead of crashing
// Gson, same reasoning as RelaySwitch.schedulable's default below.
data class TempSensor(
    val id: String,
    val name: String,
    val currentTemp: Float,
    val setTemp: Float,
    val humidity: Float,
    val pm25: Float = 0f,
    val vocIndex: Float = 0f,
    val batteryLevel: Int,
    val linkQuality: Int,
    val lastUpdated: Long
)

// A tile's slot on the sensor dashboard grid, expressed as a dense integer
// order (0-based) rather than continuous x/y - every visible sensor always
// occupies exactly one order value in 0 until sensorCount, so tiles can
// never overlap or end up off-grid: dragging one tile onto another's slot
// swaps the two (see DashboardCanvas/DraggableTile in ui/dashboard), it
// doesn't relocate to an arbitrary pixel position between cells. Columns
// are recomputed from the current screen width every composition, so the
// same order always reflows into a sensible position across phone/tablet/
// rotation without needing to store width-relative coordinates at all.
data class SensorTilePosition(
    val sensorId: String,
    val order: Int
)

data class SensorSchedule(
    val id: String,
    val sensorName: String,
    val fromHour: Int,   // 0..23
    val toHour: Int,     // 0..23
    val fromMinute: Int = 0, // 0..59
    val toMinute: Int = 0    // 0..59
)

// schedulable mirrors model.RelaySwitch.Schedulable from the shared Go
// core (smarthomeapi/pkg/core/model) - a client-facing hint only, set from
// the agent's topology.json (SchedulableDevices), never affecting what a
// toggle does. SchedulesScreen's device picker filters on this so the user
// can only create a schedule against a device the agent actually knows how
// to drive. Defaults false so a server that predates this field (or a Pi
// agent that hasn't been redeployed yet) just shows no eligible devices
// instead of crashing Gson's deserialization.
data class RelaySwitch(
    val id: String,
    val label: String,
    val isOn: Boolean,
    val schedulable: Boolean = false
)

// displayInverted is a rendering hint only - it never changes what a toggle
// does or what raw switches[].isOn itself means (still whatever the agent's
// hardware feedback reports). It exists for a board that's actually wired
// normally-closed, where "Tasmota's relay coil energized" (what isOn
// tracks) is the opposite of "the downstream device is really on" - a
// client should show switches[].isOn xor displayInverted, e.g.
// RelaysScreen.kt's SwitchItem, not isOn directly.
data class Relay(
    val id: String,
    val name: String,
    val switches: List<RelaySwitch>,
    val displayInverted: Boolean = false,
    val normalOpen: Boolean = false,
    val sensorName: String? = null
)

data class PumpConfig(
    val relay: String = "",
    val switch: String = "",
    val enabled: Boolean = false
)

data class GardenPort(
    val relay: String = "",
    val switch: String = ""
)

data class GardenConfig(
    val ports: List<GardenPort> = emptyList(),
    val defaultLoopCount: Int = 10,
    val defaultInterval: Int = 180
)

data class GardenStatus(
    val running: Boolean = false,
    val currentZone: String = "",
    val currentLoop: Int = 0,
    val totalLoops: Int = 0,
    val remainingSecs: Int = 0
)

data class GardenResponse(
    val config: GardenConfig = GardenConfig(),
    val status: GardenStatus = GardenStatus()
)

// LocalScheduleConfig is Android's own record of a schedule's
// device/time-window configuration, kept in DataStore (see
// ScheduleConfigStore) independent of whether it currently exists on the
// server - it's the UI's single source of truth for what a schedule is
// (device + time window + on/off), not the raw server-side SensorSchedule
// model (which only exists while enabled).
data class LocalScheduleConfig(
    val localId: String,
    val device: String,
    val fromHour: Int,
    val fromMinute: Int,
    val toHour: Int,
    val toMinute: Int,
    val enabled: Boolean,
    val remoteId: String?
)

interface SensorRepository {
    fun getSensors(): Flow<List<TempSensor>>
    suspend fun updateSetTemp(sensorId: String, newTemp: Float)
    suspend fun updateSensorName(sensorId: String, newName: String)
    suspend fun deleteSensor(sensorId: String)
    fun getRelays(): Flow<List<Relay>>
    suspend fun toggleRelaySwitch(relayId: String, switchId: String)

    // --- relay & switch CRUD ---
    suspend fun createRelay(relayId: String, name: String, displayInverted: Boolean = false, normalOpen: Boolean = false, sensorName: String? = null)
    suspend fun updateRelay(relayId: String, name: String? = null, displayInverted: Boolean? = null, normalOpen: Boolean? = null, sensorName: String? = null)
    suspend fun deleteRelay(relayId: String)
    suspend fun createRelaySwitch(relayId: String, switchId: String, label: String, schedulable: Boolean = false)
    suspend fun updateRelaySwitch(relayId: String, switchId: String, label: String? = null, schedulable: Boolean? = null)
    suspend fun deleteRelaySwitch(relayId: String, switchId: String)

    // --- heating pump settings ---
    fun getPumpConfig(): Flow<PumpConfig>
    suspend fun fetchPumpConfig(): Result<PumpConfig>
    suspend fun updatePumpConfig(relay: String? = null, switch: String? = null, enabled: Boolean? = null)

    // --- garden watering ---
    fun getGarden(): Flow<GardenResponse>
    suspend fun fetchGarden(): Result<GardenResponse>
    suspend fun updateGardenConfig(ports: List<GardenPort>? = null, loopCount: Int? = null, interval: Int? = null)
    suspend fun startGarden(loopCount: Int = 0, interval: Int = 0)
    suspend fun stopGarden()

    // --- schedules (see LocalScheduleConfig's doc comment for why the UI
    // drives entirely off this instead of a raw SensorSchedule list) ---

    fun getLocalScheduleConfigs(): Flow<List<LocalScheduleConfig>>

    // Creates a brand new local config, enabled by default, and immediately
    // POSTs it to the server.
    suspend fun createSchedule(device: String, fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int)

    // Changes a config's time window. If it's currently enabled, also PATCHes
    // the server-side schedule (remoteId); if disabled, only the local
    // record changes - there's nothing server-side to PATCH until it's
    // re-enabled.
    suspend fun updateScheduleConfig(localId: String, fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int)

    // Deletes the local config entirely (not just disables it) - used when
    // the user removes a schedule outright rather than toggling it off.
    // If it's currently enabled, the server-side schedule is deleted first.
    suspend fun deleteScheduleConfig(localId: String)

    // enabled=true: POSTs device/hours to the server, stores the returned
    // remoteId. enabled=false: DELETEs the current remoteId server-side
    // (both smartapi and, once relayed, the Pi's own store), clears
    // remoteId, but keeps device/hours in the local record so re-enabling
    // needs no re-entry.
    suspend fun setScheduleEnabled(localId: String, enabled: Boolean)

    // Wipes all locally-persisted schedule configs. Call on logout/account
    // switch - this store isn't scoped to the logged-in serial number, so
    // without this, schedules from the previous account bleed into whichever
    // account logs in next (see ScheduleConfigStore.clear's doc comment).
    suspend fun clearLocalScheduleConfigs()

    // --- sensor dashboard tile layout (see SensorTilePosition's doc
    // comment for why this is a dense integer order, not x/y) ---

    fun getSensorTilePositions(): Flow<List<SensorTilePosition>>

    // Persists the result of dragging one tile onto another's slot: the two
    // swap orders. There's no single-tile "move to here" - every valid drop
    // target is always some other tile's slot (see DashboardCanvas), so a
    // drag always exchanges two tiles' positions atomically rather than
    // leaving a moment where two tiles could transiently share an order.
    suspend fun swapSensorTilePositions(movedId: String, movedOrder: Int, displacedId: String, displacedOrder: Int)

    // Wipes all locally-persisted tile positions. Used by both the
    // dashboard's "Reset Layout" action and logout/account switch - like
    // the schedule configs above, this store isn't scoped to the logged-in
    // serial number.
    suspend fun clearSensorTileLayout()
}
