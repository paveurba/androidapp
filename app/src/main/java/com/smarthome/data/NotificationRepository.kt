package com.smarthome.data

import kotlinx.coroutines.flow.Flow

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false
)

interface NotificationRepository {
    fun getNotifications(): Flow<List<AppNotification>>
    suspend fun markAsRead(notificationId: String)
    suspend fun addNotification(title: String, message: String)
    suspend fun clearAll()
}
