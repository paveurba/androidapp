package com.smarthome.data

interface AuthRepository {
    suspend fun login(serialNumber: String, otp: String): Result<Unit>
    suspend fun register(serialNumber: String, otp: String): Result<Unit>
}
