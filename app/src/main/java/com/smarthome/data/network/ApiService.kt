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

    @POST("relays/{relayId}/toggle/{switchId}")
    suspend fun toggleRelay(@Path("relayId") relayId: String, @Path("switchId") switchId: String): Response<Unit>

    // Pi-only (see AlarmSensorRepository) - not mounted on the cloud server,
    // so this 404s unless talking to the Pi directly on the local network.
    @GET("alarm-sensors")
    suspend fun getAlarmSensors(): Response<HydraCollection<AlarmSensor>>

    @GET("notifications")
    suspend fun getNotifications(): Response<HydraCollection<AppNotification>>

    @PATCH("notifications/{id}")
    suspend fun markNotificationRead(@Path("id") id: String, @Body data: Map<String, Boolean>): Response<AppNotification>

    @DELETE("notifications")
    suspend fun clearNotifications(): Response<Unit>
}
