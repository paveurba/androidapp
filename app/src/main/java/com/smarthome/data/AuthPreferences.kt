package com.smarthome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.smarthome.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthPreferences(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val SERIAL_NUMBER = stringPreferencesKey("serial_number")
        val OTP = stringPreferencesKey("otp")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val IS_CUSTOM_SERVER_ENABLED = booleanPreferencesKey("is_custom_server_enabled")
        val CUSTOM_SERVER_URL = stringPreferencesKey("custom_server_url")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_LOGGED_IN] ?: false
        }

    val serialNumber: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SERIAL_NUMBER]
        }

    val otp: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[OTP]
        }

    val fcmToken: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[FCM_TOKEN]
        }

    val isCustomServerEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_CUSTOM_SERVER_ENABLED] ?: false
        }

    val customServerUrl: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[CUSTOM_SERVER_URL]
        }

    @Volatile
    var cachedIsCustomServerEnabled: Boolean = false
        private set

    @Volatile
    var cachedCustomServerUrl: String? = null
        private set

    init {
        scope.launch {
            isCustomServerEnabled.collect { enabled ->
                cachedIsCustomServerEnabled = enabled
            }
        }
        scope.launch {
            customServerUrl.collect { url ->
                cachedCustomServerUrl = url
            }
        }
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = loggedIn
        }
    }

    suspend fun saveCredentials(serialNumber: String, otp: String) {
        context.dataStore.edit { preferences ->
            preferences[SERIAL_NUMBER] = serialNumber
            preferences[OTP] = otp
        }
    }

    suspend fun setFcmToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[FCM_TOKEN] = token
        }
    }

    suspend fun setCustomServerEnabled(enabled: Boolean) {
        cachedIsCustomServerEnabled = enabled
        context.dataStore.edit { preferences ->
            preferences[IS_CUSTOM_SERVER_ENABLED] = enabled
        }
    }

    suspend fun setCustomServerUrl(url: String) {
        var formatted = url.trim()
        if (formatted.isNotBlank()) {
            if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                formatted = "http://$formatted"
            }
            if (!formatted.endsWith("/")) {
                formatted = "$formatted/"
            }
        }
        cachedCustomServerUrl = formatted.ifBlank { null }
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_SERVER_URL] = formatted
        }
    }

    fun getEffectiveBaseUrl(): String {
        if (cachedIsCustomServerEnabled) {
            val custom = cachedCustomServerUrl
            if (!custom.isNullOrBlank()) {
                return custom
            }
        }
        return BuildConfig.API_BASE_URL
    }

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.remove(IS_LOGGED_IN)
            preferences.remove(SERIAL_NUMBER)
            preferences.remove(OTP)
            preferences.remove(FCM_TOKEN)
        }
    }
}
