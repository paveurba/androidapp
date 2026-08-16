package com.smarthome.data

import kotlinx.coroutines.flow.Flow

/**
 * A contact/occupancy/water-leak device's current reading - battery, link
 * quality, and whether its alarm condition is active right now (door/garage
 * open, motion detected, or a leak detected - normalized into one boolean
 * regardless of kind, matching model.AlarmSensor). Deliberately not a
 * TempSensor: those three fields don't fit a door sensor, and this is a
 * live status snapshot, not something you set a target value on.
 */
data class AlarmSensor(
    val id: String,
    val name: String = "",
    val kind: String, // "contact" | "occupancy" | "water_leak" | "vibration"
    val triggered: Boolean,
    val batteryLevel: Int,
    val linkQuality: Int,
    val lastUpdated: Long
)

/**
 * Backed by /api/alarm-sensors, part of the shared REST layer both the Pi
 * agent and the cloud server mount (smarthomeapi's pkg/core/api - same as
 * sensors/relays/schedules/notifications). Works identically whether the
 * app is talking to the Pi directly or through upanet.org: the agent writes
 * readings into its own shared store, which its uplink then mirrors to the
 * cloud's copy the same way it already does for temperature sensors.
 */
interface AlarmSensorRepository {
    fun getAlarmSensors(): Flow<List<AlarmSensor>>
    suspend fun updateAlarmSensorName(sensorId: String, newName: String)
    suspend fun deleteAlarmSensor(sensorId: String)

    // --- alarm-sensor dashboard tile layout, same idea as
    // SensorRepository's sensor tile layout (see SensorTilePosition's doc
    // comment) but stored separately - alarm sensor ids and temp sensor ids
    // are different id spaces and aren't guaranteed distinct from each
    // other, so sharing one store could let a temp sensor's saved position
    // collide with an alarm sensor's. ---

    fun getAlarmSensorTilePositions(): Flow<List<SensorTilePosition>>
    suspend fun swapAlarmSensorTilePositions(movedId: String, movedOrder: Int, displacedId: String, displacedOrder: Int)

    // Wipes all locally-persisted tile positions. Used by both the alarm
    // dashboard's "Reset Layout" action and logout/account switch - not
    // scoped to the logged-in serial number, same as every other local-only
    // store here.
    suspend fun clearAlarmSensorTileLayout()
}
