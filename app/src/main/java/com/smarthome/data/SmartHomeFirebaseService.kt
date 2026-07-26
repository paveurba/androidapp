package com.smarthome.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.smarthome.MainActivity
import com.smarthome.R

/**
 * Real Firebase Messaging Service implementation.
 * Note: Requires 'com.google.firebase:firebase-messaging' dependency in build.gradle.
 */
class SmartHomeFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token generated: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCM", "From: ${remoteMessage.from}")

        // Handle notification payload
        remoteMessage.notification?.let {
            showNotification(it.title ?: "Smart Home Alert", it.body ?: "")
        }

        // Handle data payload (this is where you'd save to your local repository)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: " + remoteMessage.data)
            // You can broadcast this or save to repository here
        }
    }

    private fun sendTokenToServer(token: String) {
        // TODO: Implement your API call here
        // myApi.registerToken(token)
        Log.d("FCM", "Sending token to server...")
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "smart_home_alerts"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Smart Home Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Use a unique request code per notification (matching the notification ID below) so
        // each notification gets its own PendingIntent. A shared request code combined with
        // FLAG_ONE_SHOT meant tapping one notification could invalidate the PendingIntent used
        // by another still-visible notification, silently failing to reopen the app.
        val notificationId = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your app icon
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
