package com.smarthome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.smarthome.data.AuthPreferences
import com.smarthome.data.MockAuthRepository
import com.smarthome.data.MockNotificationRepository
import com.smarthome.data.MockSensorRepository
import com.smarthome.data.SmartHomeFirebaseService
import com.smarthome.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val authPreferences = AuthPreferences(applicationContext)
        val authRepository = MockAuthRepository()
        val sensorRepository = MockSensorRepository()
        val notificationRepository = MockNotificationRepository()

        // Retrieve and log FCM token (real API)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                android.util.Log.d("FCM", "Device Token: $token")
                // Here you would send the token to your server
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
