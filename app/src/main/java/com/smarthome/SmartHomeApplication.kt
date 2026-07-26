package com.smarthome

import android.app.Application
import com.smarthome.data.AuthPreferences
import com.smarthome.data.ProductionAuthRepository
import com.smarthome.data.ProductionNotificationRepository
import com.smarthome.data.ProductionSensorRepository
import com.smarthome.data.network.NetworkClient

/**
 * Holds process-lifetime singletons for auth/network/repositories.
 *
 * These used to be created inside MainActivity.onCreate(), which meant every activity
 * recreation (screen rotation, dark-mode toggle, multi-window resize, etc. - MainActivity
 * declares no android:configChanges) built a brand new AuthPreferences and a brand new
 * ProductionSensorRepository, each spinning up its own unbounded CoroutineScope: DataStore
 * collectors, three infinite polling loops, and a live WebSocket connection. The old
 * instances were never torn down, so every recreation permanently leaked another full set
 * of background workers. Building them once here, at Application scope, fixes that at the
 * root instead of special-casing activity lifecycle callbacks.
 */
class SmartHomeApplication : Application() {

    val authPreferences: AuthPreferences by lazy {
        AuthPreferences(applicationContext)
    }

    private val apiService by lazy {
        NetworkClient.getApiService(applicationContext, authPreferences)
    }

    val authRepository by lazy {
        ProductionAuthRepository(apiService)
    }

    val sensorRepository by lazy {
        ProductionSensorRepository(apiService, authPreferences)
    }

    val notificationRepository by lazy {
        ProductionNotificationRepository(apiService)
    }
}
