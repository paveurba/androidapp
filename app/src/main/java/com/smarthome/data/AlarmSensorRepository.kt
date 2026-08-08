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
    val kind: String, // "contact" | "occupancy" | "water_leak"
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
}
