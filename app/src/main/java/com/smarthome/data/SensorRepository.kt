package com.smarthome.data

import kotlinx.coroutines.flow.Flow

data class TempSensor(
    val id: String,
    val name: String,
    val currentTemp: Float,
    val setTemp: Float,
    val humidity: Float,
    val batteryLevel: Int,
    val linkQuality: Int,
    val lastUpdated: Long
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
    val displayInverted: Boolean = false
)

// LocalScheduleConfig is Android's own record of a schedule's
// device/time-window configuration, kept in DataStore (see
// ScheduleConfigStore) independent of whether it currently exists on the
// server - it's the UI's single source of truth for what a schedule is
// (device + time window + on/off), not the raw server-side SensorSchedule
// list, which only ever reflects the subset that's currently *enabled*.
// Disabling a schedule deletes it server-side (smartapi and the Pi's own
// store both drop it - see the server-side doc comments on
// Backend.DeleteSchedule) but this local record survives, so re-enabling
// recreates it with the same device/hours without the user re-entering
// anything. remoteId is the server's current schedule ID for this config,
// non-null only while enabled == true.
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
    fun getRelays(): Flow<List<Relay>>
    suspend fun toggleRelaySwitch(relayId: String, switchId: String)

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
}
