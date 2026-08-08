package com.smarthome.data.network

import com.smarthome.data.*
import retrofit2.Response
import retrofit2.http.*

data class LoginRequest(val serialNumber: String, val otp: String)
data class LoginResponse(val token: String)
data class RegisterRequest(val serialNumber: String, val otp: String)

data class HydraCollection<T>(
    @com.google.gson.annotations.SerializedName("member")
    val member: List<T>
)

data class ToggleRelayResponse(val isOn: Boolean)

interface ApiService {
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("register")
    suspend fun register(@Body request: RegisterRequest): Response<Unit>

    @GET("sensors")
    suspend fun getSensors(): Response<HydraCollection<TempSensor>>

    @PATCH("sensors/{id}")
    suspend fun updateSensor(@Path("id") id: String, @Body sensor: Map<String, Float>): Response<TempSensor>

    @GET("schedules")
    suspend fun getSchedules(): Response<HydraCollection<SensorSchedule>>

    @PATCH("schedules/{id}")
    suspend fun updateSchedule(@Path("id") id: String, @Body schedule: Map<String, Int>): Response<SensorSchedule>

    @GET("relays")
    suspend fun getRelays(): Response<HydraCollection<Relay>>

    // Server returns the real resulting isOn (not just success) - this is a
    // *toggle* (flip whatever the server currently has), so our own
    // optimistic guess at the pre-toggle state can be wrong (e.g. it hasn't
    // caught up to a feedback-driven update yet) and land backwards. See
    // ProductionRepositories.kt's toggleRelaySwitch.
    @POST("relays/{relayId}/toggle/{switchId}")
    suspend fun toggleRelay(@Path("relayId") relayId: String, @Path("switchId") switchId: String): Response<ToggleRelayResponse>

    // Shared with the cloud server, same as everything else here (see
    // AlarmSensorRepository).
    @GET("alarm-sensors")
    suspend fun getAlarmSensors(): Response<HydraCollection<AlarmSensor>>

    @GET("notifications")
    suspend fun getNotifications(): Response<HydraCollection<AppNotification>>

    @PATCH("notifications/{id}")
    suspend fun markNotificationRead(@Path("id") id: String, @Body data: Map<String, Boolean>): Response<AppNotification>

    @DELETE("notifications")
    suspend fun clearNotifications(): Response<Unit>
}
