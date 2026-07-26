package com.smarthome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.smarthome.data.*
import com.smarthome.data.network.NetworkClient
import com.smarthome.navigation.AppNavigation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val authPreferences = AuthPreferences(applicationContext)
        val apiService = NetworkClient.getApiService(applicationContext, authPreferences)

        // Production repositories using the Symfony API
        val authRepository = ProductionAuthRepository(apiService)
        val sensorRepository = ProductionSensorRepository(apiService, authPreferences)
        val notificationRepository = ProductionNotificationRepository(apiService)

        // Retrieve and log FCM token (real API)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                android.util.Log.d("FCM", "Device Token: $token")
                // Save token to preferences asynchronously
                lifecycleScope.launch {
                    authPreferences.setFcmToken(token)
                }
            }
        }
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        authPreferences = authPreferences,
                        authRepository = authRepository,
                        sensorRepository = sensorRepository,
                        notificationRepository = notificationRepository
                    )
                }
            }
        }
    }
}
