package com.smarthome.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class MockNotificationRepository : NotificationRepository {
    private val _notifications = MutableStateFlow(listOf(
        AppNotification("1", "System Update", "New firmware available for Kitchen Relay.", System.currentTimeMillis() - 3600000, true),
        AppNotification("2", "Security Alert", "Motion detected in Living Room at 2:00 AM.", System.currentTimeMillis() - 7200000, false),
        AppNotification("3", "Battery Low", "Bedroom Sensor battery is below 15%.", System.currentTimeMillis() - 86400000, false)
    ))

    override fun getNotifications(): Flow<List<AppNotification>> = _notifications.asStateFlow()

    override suspend fun markAsRead(notificationId: String) {
        val current = _notifications.value.toMutableList()
        val index = current.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            current[index] = current[index].copy(isRead = true)
            _notifications.value = current
        }
    }

    override suspend fun addNotification(title: String, message: String) {
        val current = _notifications.value.toMutableList()
        current.add(0, AppNotification(
            id = System.currentTimeMillis().toString(),
            title = title,
            message = message,
            timestamp = System.currentTimeMillis()
        ))
        _notifications.value = current
    }

    override suspend fun clearAll() {
        _notifications.value = emptyList()
    }
}
