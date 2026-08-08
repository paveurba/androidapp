package com.smarthome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smarthome.navigation.AppNavigation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: notifications simply won't show if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Repositories & preferences live on the Application (process-scoped singletons) so
        // they aren't recreated - and the old instances leaked - on every activity recreation
        // (rotation, dark-mode toggle, etc).
        val app = application as SmartHomeApplication
        val authPreferences = app.authPreferences
        val authRepository = app.authRepository
        val sensorRepository = app.sensorRepository
        val notificationRepository = app.notificationRepository
        val alarmSensorRepository = app.alarmSensorRepository
        val agentStatusRepository = app.agentStatusRepository

        requestNotificationPermissionIfNeeded()

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
                        notificationRepository = notificationRepository,
                        alarmSensorRepository = alarmSensorRepository,
                        agentStatusRepository = agentStatusRepository
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        // POST_NOTIFICATIONS is a runtime (dangerous) permission starting Android 13 (API 33).
        // Declaring it in the manifest alone is not enough - without this request, FCM
        // notifications would silently never display on Android 13+ devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
