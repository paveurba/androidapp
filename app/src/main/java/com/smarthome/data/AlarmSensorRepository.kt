package com.smarthome.data

import kotlinx.coroutines.flow.Flow

/**
 * A contact/occupancy/water-leak device's current reading - battery, link
 * quality, and whether its alarm condition is active right now (door/garage
 * open, motion detected, or a leak detected - normalized into one boolean
 * regardless of kind, matching the agent's AlarmSensorState). Deliberately
 * not a TempSensor: those three fields don't fit a door sensor, and this is
 * a live status snapshot, not something you set a target value on.
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
 * Backed by the Pi agent's /api/alarm-sensors - a Pi-only endpoint (see
 * smarthome/internal/agentbackend/alarmsensorroutes.go), same situation as
 * garden: not mounted on the cloud server, so this returns nothing useful
 * unless the app is talking to the Pi directly on the local network. That's
 * a silent no-op, not an error - same as every other repository here when a
 * request fails.
 */
interface AlarmSensorRepository {
    fun getAlarmSensors(): Flow<List<AlarmSensor>>
}
