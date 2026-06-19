package com.smarthome.data.network

import android.content.Context
import android.provider.Settings
import com.smarthome.data.AuthPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val context: Context,
    private val authPreferences: AuthPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()

        // 1. Retrieve stored credentials and fcm token from AuthPreferences using runBlocking.
        // runBlocking is acceptable here as interceptors run on background OkHttp dispatcher threads.
        val (serialNumber, otp, fcmToken) = runBlocking {
            val sn = authPreferences.serialNumber.first()
            val o = authPreferences.otp.first()
            val token = authPreferences.fcmToken.first()
            Triple(sn, o, token)
        }

        // 2. Retrieve Android ID
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        // 3. Add Basic Auth headers if credentials exist
        if (!serialNumber.isNullOrBlank() && !otp.isNullOrBlank()) {
            val credentials = Credentials.basic(serialNumber, otp)
            builder.header("Authorization", credentials)
        }

        // 4. Add Client Identification Headers
        if (!androidId.isNullOrBlank()) {
            builder.header("X-Android-Id", androidId)
        }
        if (!fcmToken.isNullOrBlank()) {
            builder.header("X-FCM-Token", fcmToken)
        }

        return chain.proceed(builder.build())
    }
}
