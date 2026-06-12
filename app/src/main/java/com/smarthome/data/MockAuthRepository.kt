package com.smarthome.data

import kotlinx.coroutines.delay

interface AuthRepository {
    suspend fun login(serialNumber: String, otp: String): Result<Unit>
    suspend fun register(serialNumber: String, otp: String): Result<Unit>
}

class MockAuthRepository : AuthRepository {
    // Predefined mock data
    private val validSerialNumber = "SN123456"
    private val validOtp = "12345678"

    override suspend fun login(serialNumber: String, otp: String): Result<Unit> {
        delay(1000) // Simulate network delay
        return if (serialNumber == validSerialNumber && otp == validOtp) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Invalid serial number or OTP"))
        }
    }

    override suspend fun register(serialNumber: String, otp: String): Result<Unit> {
        delay(1500) // Simulate network delay
        // For mock, any registration succeeds if OTP is 8 digits
        return if (otp.length == 8 && otp.all { it.isDigit() }) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Registration failed: OTP must be 8 digits"))
        }
    }
}
