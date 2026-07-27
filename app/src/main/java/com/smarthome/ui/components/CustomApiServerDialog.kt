package com.smarthome.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smarthome.BuildConfig
import com.smarthome.data.AuthPreferences
import kotlinx.coroutines.launch

@Composable
fun CustomApiServerDialog(
    authPreferences: AuthPreferences,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isCustomEnabled by authPreferences.isCustomServerEnabled.collectAsState(initial = false)
    val customUrlPreference by authPreferences.customServerUrl.collectAsState(initial = null)
    val customWsPreference by authPreferences.customWebSocketUrl.collectAsState(initial = null)

    var enabledState by remember(isCustomEnabled) { mutableStateOf(isCustomEnabled) }
    var inputUrl by remember(customUrlPreference) { 
        mutableStateOf(customUrlPreference ?: BuildConfig.API_BASE_URL) 
    }
    var inputWsUrl by remember(customWsPreference) {
        mutableStateOf(customWsPreference ?: "")
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, contentDescription = "Custom API Server Settings") },
        title = { Text("Custom API & WebSocket Server") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure custom local HTTP & WebSocket endpoints when offline or operating on a local network.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Custom Server Mode",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = enabledState,
                        onCheckedChange = { 
                            enabledState = it 
                            validationError = null
                        }
                    )
                }

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { 
                        inputUrl = it 
                        validationError = null
                    },
                    label = { Text("Custom API Base URL") },
                    placeholder = { Text("http://192.168.1.100:8000/api/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabledState,
                    isError = validationError != null
                )

                OutlinedTextField(
                    value = inputWsUrl,
                    onValueChange = { 
                        inputWsUrl = it 
                    },
                    label = { Text("Custom WebSocket URL (Optional)") },
                    placeholder = { Text("ws://192.168.1.100:8080/") },
                    supportingText = { Text("Leave blank to auto-derive from API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabledState
                )

                validationError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            inputUrl = BuildConfig.API_BASE_URL
                            inputWsUrl = ""
                        },
                        enabled = enabledState
                    ) {
                        Text("Reset to Defaults")
                    }
                }

                Surface(
                    color = if (enabledState) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (enabledState) "Mode: Custom Server Active" else "Mode: Default (.env Base URL)",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (enabledState) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "HTTP API:\n" + if (enabledState) {
                                inputUrl.ifBlank { BuildConfig.API_BASE_URL }
                            } else {
                                BuildConfig.API_BASE_URL
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabledState) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "WebSocket:\n" + if (enabledState) {
                                if (inputWsUrl.isNotBlank()) inputWsUrl else "Auto-derived (wss://host/ws or ws://host:8080)"
                            } else {
                                "Default (wss://host/ws or ws://host:8080)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabledState) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (enabledState && inputUrl.isBlank()) {
                        validationError = "Please enter a valid API URL"
                        return@Button
                    }
                    scope.launch {
                        authPreferences.setCustomServerUrl(inputUrl)
                        authPreferences.setCustomWebSocketUrl(inputWsUrl)
                        authPreferences.setCustomServerEnabled(enabledState)
                        onDismiss()
                    }
                }
            ) {
                Text("Save & Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
