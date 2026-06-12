package com.smarthome.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class TempSensor(
    val id: String,
    val name: String,
    val currentTemp: Float,
    val targetTemp: Float
)

interface SensorRepository {
    fun getSensors(): Flow<List<TempSensor>>
    suspend fun updateTargetTemp(sensorId: String, newTemp: Float)
}

class MockSensorRepository : SensorRepository {
    private val sensors = mutableListOf(
        TempSensor("1", "Living Room", 22.5f, 22.0f),
        TempSensor("2", "Bedroom", 20.1f, 19.0f),
        TempSensor("3", "Kitchen", 24.0f, 23.5f)
    )

    override fun getSensors(): Flow<List<TempSensor>> = flow {
        while (true) {
            emit(sensors.toList())
            delay(5000) // Simulate periodic updates
        }
    }

    override suspend fun updateTargetTemp(sensorId: String, newTemp: Float) {
        delay(500) // Simulate network delay
        val index = sensors.indexOfFirst { it.id == sensorId }
        if (index != -1) {
            sensors[index] = sensors[index].copy(targetTemp = newTemp)
        }
    }
}
