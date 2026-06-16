package com.smarthome.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
    val fromHour: Int, // 1..23
    val toHour: Int    // 1..23
)

interface SensorRepository {
    fun getSensors(): Flow<List<TempSensor>>
    suspend fun updateSetTemp(sensorId: String, newTemp: Float)
    fun getSchedules(): Flow<List<SensorSchedule>>
    suspend fun updateSchedule(scheduleId: String, fromHour: Int, toHour: Int)
}

class MockSensorRepository : SensorRepository {
    private val sensors = mutableListOf(
        TempSensor("1", "Living Room", 22.5f, 22.0f, 45.0f, 85, 200, System.currentTimeMillis()),
        TempSensor("2", "Bedroom", 18.1f, 20.0f, 50.0f, 15, 180, System.currentTimeMillis() - 60000), // Below set temp + Low battery
        TempSensor("3", "Kitchen", 24.0f, 23.5f, 55.0f, 45, 150, System.currentTimeMillis() - 4000000) // Stale data (> 1 hour)
    )

    private val schedules = mutableListOf(
        SensorSchedule("s1", "Main Heater", 7, 9),
        SensorSchedule("s2", "Water Boiler", 18, 22),
        SensorSchedule("s3", "Garden Lights", 22, 6)
    )

    override fun getSensors(): Flow<List<TempSensor>> = flow {
        while (true) {
            emit(sensors.toList())
            delay(5000) // Simulate periodic updates
        }
    }

    override suspend fun updateSetTemp(sensorId: String, newTemp: Float) {
        delay(500) // Simulate network delay
        val index = sensors.indexOfFirst { it.id == sensorId }
        if (index != -1) {
            sensors[index] = sensors[index].copy(setTemp = newTemp)
        }
    }

    override fun getSchedules(): Flow<List<SensorSchedule>> = flow {
        while (true) {
            emit(schedules.toList())
            delay(5000)
        }
    }

    override suspend fun updateSchedule(scheduleId: String, fromHour: Int, toHour: Int) {
        delay(500)
        val index = schedules.indexOfFirst { it.id == scheduleId }
        if (index != -1) {
            schedules[index] = schedules[index].copy(fromHour = fromHour, toHour = toHour)
        }
    }
}
