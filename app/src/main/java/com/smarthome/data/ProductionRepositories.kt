package com.smarthome.data

import com.smarthome.data.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import org.json.JSONObject

class ProductionAuthRepository(private val apiService: ApiService) : AuthRepository {
    override suspend fun login(serialNumber: String, otp: String): Result<Unit> {
        return try {
            val response = apiService.login(LoginRequest(serialNumber, otp))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(serialNumber: String, otp: String): Result<Unit> {
        return try {
            val response = apiService.register(RegisterRequest(serialNumber, otp))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class ProductionSensorRepository(
    private val apiService: ApiService,
    private val authPreferences: AuthPreferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : SensorRepository {

    private val _sensors = MutableStateFlow<List<TempSensor>>(emptyList())
    private val _schedules = MutableStateFlow<List<SensorSchedule>>(emptyList())
    private val _relays = MutableStateFlow<List<Relay>>(emptyList())

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient()
    private var isConnecting = false
    private var currentSerialNumber: String? = null

    // Bumped every time the socket is intentionally torn down (logout, serial number change).
    // Reconnect attempts capture the generation they were scheduled under and no-op if it has
    // since changed, so a delayed reconnect can't resurrect a session after the user logged out.
    private var connectionGeneration = 0

    init {
        startPolling()
        startWebSocketListener()
    }

    private fun startWebSocketListener() {
        scope.launch {
            combine(
                authPreferences.serialNumber,
                authPreferences.isCustomServerEnabled,
                authPreferences.customServerUrl,
                authPreferences.customWebSocketUrl
            ) { serialNumber, _, _, _ -> serialNumber }
            .collect { serialNumber ->
                disconnectWebSocket()
                if (!serialNumber.isNullOrBlank()) {
                    connectWebSocket(serialNumber)
                }
            }
        }
    }

    private fun connectWebSocket(serialNumber: String, expectedGeneration: Int = connectionGeneration) {
        if (expectedGeneration != connectionGeneration) return
        currentSerialNumber = serialNumber
        if (webSocket != null || isConnecting) return
        isConnecting = true

        scope.launch {
            val wsUrl = authPreferences.getEffectiveWebSocketUrl(serialNumber)
            android.util.Log.d("SocketEvent", "Connecting to WebSocket: $wsUrl")

            val request = Request.Builder().url(wsUrl).build()

            // Bail out if we were disconnected (e.g. logout) while suspended above.
            if (expectedGeneration != connectionGeneration) {
                isConnecting = false
                return@launch
            }

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (expectedGeneration != connectionGeneration) {
                        webSocket.close(1000, null)
                        return
                    }
                    isConnecting = false
                    android.util.Log.i("SocketEvent", "WebSocket connection opened")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    android.util.Log.d("SocketEvent", "WebSocket message received: $text")
                    try {
                        val json = JSONObject(text)
                        val event = json.optString("event")
                        val data = json.optJSONObject("data")

                        when (event) {
                            "refresh_sensors" -> {
                                android.util.Log.i("SocketEvent", "Refreshing sensor list from socket event")
                                if (data != null && data.has("sensorId") && data.has("setTemp")) {
                                    val sensorId = data.getString("sensorId")
                                    val newSetTemp = data.getDouble("setTemp").toFloat()
                                    val currentSensors = _sensors.value.toMutableList()
                                    val idx = currentSensors.indexOfFirst { it.id == sensorId }
                                    if (idx != -1) {
                                        currentSensors[idx] = currentSensors[idx].copy(setTemp = newSetTemp)
                                        _sensors.value = currentSensors
                                    }
                                }
                                scope.launch { fetchSensors() }
                            }
                            "refresh_relays" -> {
                                android.util.Log.i("SocketEvent", "Refreshing relays list from socket event")
                                if (data != null && data.has("relayId") && data.has("switchId")) {
                                    val relayId = data.getString("relayId")
                                    val switchId = data.getString("switchId")
                                    val currentRelays = _relays.value.toMutableList()
                                    val relayIndex = currentRelays.indexOfFirst { it.id == relayId }
                                    if (relayIndex != -1) {
                                        val relay = currentRelays[relayIndex]
                                        val switches = relay.switches.toMutableList()
                                        val switchIndex = switches.indexOfFirst { it.id == switchId }
                                        if (switchIndex != -1) {
                                            val isOn = if (data.has("isOn")) data.getBoolean("isOn") else !switches[switchIndex].isOn
                                            switches[switchIndex] = switches[switchIndex].copy(isOn = isOn)
                                            currentRelays[relayIndex] = relay.copy(switches = switches)
                                            _relays.value = currentRelays
                                        }
                                    }
                                }
                                scope.launch { fetchRelays() }
                            }
                            "refresh_schedules" -> {
                                android.util.Log.i("SocketEvent", "Refreshing schedules list from socket event")
                                scope.launch { fetchSchedules() }
                            }
                            "refresh_notifications" -> {
                                android.util.Log.i("SocketEvent", "Refreshing notifications list from socket event")
                                scope.launch {
                                    fetchSensors()
                                    fetchRelays()
                                }
                            }
                            "refresh_all" -> {
                                scope.launch {
                                    fetchSensors()
                                    fetchRelays()
                                    fetchSchedules()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SocketEvent", "Error parsing WebSocket message: ${e.message}")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    android.util.Log.i("SocketEvent", "WebSocket connection closed: $reason ($code)")
                    if (expectedGeneration != connectionGeneration) return
                    this@ProductionSensorRepository.webSocket = null
                    isConnecting = false
                    triggerReconnect(expectedGeneration)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    android.util.Log.e("SocketEvent", "WebSocket failure: ${t.message}", t)
                    if (expectedGeneration != connectionGeneration) return
                    this@ProductionSensorRepository.webSocket = null
                    isConnecting = false
                    triggerReconnect(expectedGeneration)
                }
            })
        }
    }

    private fun triggerReconnect(expectedGeneration: Int) {
        val sn = currentSerialNumber
        if (!sn.isNullOrBlank()) {
            scope.launch {
                delay(5000)
                // If the user logged out (or the serial number changed) during the delay,
                // connectionGeneration will have moved on and this reconnect is stale — drop it.
                if (expectedGeneration == connectionGeneration) {
                    connectWebSocket(sn, expectedGeneration)
                }
            }
        }
    }

    private fun disconnectWebSocket() {
        connectionGeneration++
        currentSerialNumber = null
        webSocket?.close(1000, "User logged out")
        webSocket = null
        isConnecting = false
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                fetchSensors()
                delay(10000)
            }
        }
        scope.launch {
            while (isActive) {
                fetchRelays()
                delay(5000)
            }
        }
        scope.launch {
            while (isActive) {
                fetchSchedules()
                delay(30000)
            }
        }
    }

    private suspend fun fetchSensors() {
        try {
            val response = apiService.getSensors()
            if (response.isSuccessful) {
                _sensors.value = response.body()?.member ?: emptyList()
            }
        } catch (e: Exception) {}
    }

    private suspend fun fetchRelays() {
        try {
            val response = apiService.getRelays()
            if (response.isSuccessful) {
                _relays.value = response.body()?.member ?: emptyList()
            }
        } catch (e: Exception) {}
    }

    private suspend fun fetchSchedules() {
        try {
            val response = apiService.getSchedules()
            if (response.isSuccessful) {
                _schedules.value = response.body()?.member ?: emptyList()
            }
        } catch (e: Exception) {}
    }

    override fun getSensors(): Flow<List<TempSensor>> = _sensors.asStateFlow()

    override suspend fun updateSetTemp(sensorId: String, newTemp: Float) {
        val current = _sensors.value.toMutableList()
        val index = current.indexOfFirst { it.id == sensorId }
        if (index != -1) {
            current[index] = current[index].copy(setTemp = newTemp)
            _sensors.value = current
        }

        try {
            apiService.updateSensor(sensorId, mapOf("setTemp" to newTemp))
        } catch (e: Exception) {
            fetchSensors()
        }
    }

    override fun getSchedules(): Flow<List<SensorSchedule>> = _schedules.asStateFlow()

    override suspend fun updateSchedule(scheduleId: String, fromHour: Int, toHour: Int) {
        val schedules = _schedules.value
        val scheduleToUpdate = schedules.find { it.id == scheduleId } ?: return
        
        // Local Validation: Check for overlaps before sending to server
        if (isOverlappingLocal(scheduleId, scheduleToUpdate.sensorName, fromHour, toHour)) {
            throw Exception("Schedule overlaps with an existing time window for this device.")
        }

        // Optimistic UI update
        val current = schedules.toMutableList()
        val index = current.indexOfFirst { it.id == scheduleId }
        if (index != -1) {
            current[index] = current[index].copy(fromHour = fromHour, toHour = toHour)
            _schedules.value = current
        }

        try {
            val response = apiService.updateSchedule(scheduleId, mapOf("fromHour" to fromHour, "toHour" to toHour))
            if (!response.isSuccessful) {
                fetchSchedules() // Rollback on server error
                throw Exception("Failed to update schedule: ${response.message()}")
            }
        } catch (e: Exception) {
            fetchSchedules()
            throw e
        }
    }

    private fun isOverlappingLocal(id: String, deviceName: String, start: Int, end: Int): Boolean {
        val range1 = getHoursInRange(start, end)
        return _schedules.value.any { s ->
            if (s.id == id || s.sensorName != deviceName) return@any false
            val range2 = getHoursInRange(s.fromHour, s.toHour)
            range1.intersect(range2).isNotEmpty()
        }
    }

    private fun getHoursInRange(start: Int, end: Int): Set<Int> {
        val hours = mutableSetOf<Int>()
        var current = start
        while (current != end) {
            hours.add(current)
            current = (current + 1) % 24
        }
        return hours
    }

    override fun getRelays(): Flow<List<Relay>> = _relays.asStateFlow()

    override suspend fun toggleRelaySwitch(relayId: String, switchId: String) {
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

        try {
            apiService.toggleRelay(relayId, switchId)
        } catch (e: Exception) {
            fetchRelays()
        }
    }
}

class ProductionAlarmSensorRepository(
    private val apiService: ApiService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AlarmSensorRepository {

    private val _alarmSensors = MutableStateFlow<List<AlarmSensor>>(emptyList())

    init {
        scope.launch {
            while (isActive) {
                try {
                    val response = apiService.getAlarmSensors()
                    if (response.isSuccessful) {
                        _alarmSensors.value = response.body()?.member ?: emptyList()
                    }
                    // Not successful (e.g. 404 via the cloud, which doesn't mount this
                    // endpoint) just leaves the last known value in place, same as every
                    // other repository here - not an error worth surfacing.
                } catch (e: Exception) {}
                delay(15000)
            }
        }
    }

    override fun getAlarmSensors(): Flow<List<AlarmSensor>> = _alarmSensors.asStateFlow()
}

class ProductionNotificationRepository(
    private val apiService: ApiService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : NotificationRepository {
    
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())

    init {
        scope.launch {
            while (isActive) {
                try {
                    val response = apiService.getNotifications()
                    if (response.isSuccessful) {
                        _notifications.value = response.body()?.member ?: emptyList()
                    }
                } catch (e: Exception) {}
                delay(5000)
            }
        }
    }

    override fun getNotifications(): Flow<List<AppNotification>> = _notifications.asStateFlow()

    override suspend fun markAsRead(notificationId: String) {
        val current = _notifications.value.toMutableList()
        val index = current.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            current[index] = current[index].copy(isRead = true)
            _notifications.value = current
        }

        try {
            apiService.markNotificationRead(notificationId, mapOf("isRead" to true))
        } catch (e: Exception) {}
    }

    override suspend fun addNotification(title: String, message: String) {}

    override suspend fun clearAll() {
        _notifications.value = emptyList()
        try {
            apiService.clearNotifications()
        } catch (e: Exception) {}
    }
}
