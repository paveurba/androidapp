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
    val fromHour: Int, // 0..23
    val toHour: Int    // 0..23
)

data class RelaySwitch(
    val id: String,
    val label: String,
    val isOn: Boolean
)

data class Relay(
    val id: String,
    val name: String,
    val switches: List<RelaySwitch>
)

interface SensorRepository {
    fun getSensors(): Flow<List<TempSensor>>
    suspend fun updateSetTemp(sensorId: String, newTemp: Float)
    fun getSchedules(): Flow<List<SensorSchedule>>
    suspend fun updateSchedule(scheduleId: String, fromHour: Int, toHour: Int)
    fun getRelays(): Flow<List<Relay>>
    suspend fun toggleRelaySwitch(relayId: String, switchId: String)
}
