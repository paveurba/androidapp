package com.smarthome.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class MockSensorRepository : SensorRepository {
    private val _sensors = MutableStateFlow(listOf(
        TempSensor("1", "Living Room", 22.5f, 22.0f, 45.0f, 85, 200, System.currentTimeMillis()),
        TempSensor("2", "Bedroom", 18.1f, 20.0f, 50.0f, 15, 180, System.currentTimeMillis() - 60000),
        TempSensor("3", "Kitchen", 24.0f, 23.5f, 55.0f, 45, 150, System.currentTimeMillis() - 4000000)
    ))

    private val _schedules = MutableStateFlow(listOf(
        SensorSchedule("s1", "Main Heater", 7, 9),
        SensorSchedule("s2", "Water Boiler", 18, 22),
        SensorSchedule("s3", "Garden Lights", 22, 6)
    ))

    private val _relays = MutableStateFlow(listOf(
        Relay("r1", "Living Room Relay", listOf(
            RelaySwitch("rs1", "Main Light", true),
            RelaySwitch("rs2", "Socket 1", false),
            RelaySwitch("rs3", "Socket 2", false)
        )),
        Relay("r2", "Kitchen Module", listOf(
            RelaySwitch("rs4", "Ceiling Light", false),
            RelaySwitch("rs5", "Counter Light", true)
        )),
        Relay("r3", "16-Channel Controller", (1..16).map { 
            RelaySwitch("rs_16_$it", "Switch $it", it % 2 == 0) 
        })
    ))

    override fun getSensors(): Flow<List<TempSensor>> = _sensors.asStateFlow()

    override suspend fun updateSetTemp(sensorId: String, newTemp: Float) {
        delay(300)
        val currentList = _sensors.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == sensorId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(setTemp = newTemp)
            _sensors.value = currentList
        }
    }

    override fun getSchedules(): Flow<List<SensorSchedule>> = _schedules.asStateFlow()

    override suspend fun updateSchedule(scheduleId: String, fromHour: Int, toHour: Int) {
        delay(300)
        val currentList = _schedules.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == scheduleId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(fromHour = fromHour, toHour = toHour)
            _schedules.value = currentList
        }
    }

    override fun getRelays(): Flow<List<Relay>> = _relays.asStateFlow()

    override suspend fun toggleRelaySwitch(relayId: String, switchId: String) {
        delay(100) // Fast response for switches
        val currentRelays = _relays.value.toMutableList()
        val relayIndex = currentRelays.indexOfFirst { it.id == relayId }
        if (relayIndex != -1) {
            val relay = currentRelays[relayIndex]
            val switches = relay.switches.toMutableList()
            val switchIndex = switches.indexOfFirst { it.id == switchId }
            if (switchIndex != -1) {
                switches[switchIndex] = switches[switchIndex].copy(isOn = !switches[switchIndex].isOn)
                currentRelays[relayIndex] = relay.copy(switches = switches)
                _relays.value = currentRelays
            }
        }
    }
}
