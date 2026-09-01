package com.smarthome.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.smarthome.data.AuthPreferences
import com.smarthome.data.AuthRepository
import com.smarthome.ui.components.CustomApiServerDialog
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Refresh
import com.smarthome.data.ble.BleProvisioningRepository
import com.smarthome.ui.components.BleProvisioningDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authPreferences: AuthPreferences,
    authRepository: AuthRepository,
    bleProvisioningRepository: BleProvisioningRepository,
    onLoginSuccess: (serialNumber: String, otp: String) -> Unit
) {
    var isLogin by remember { mutableStateOf(true) }
    var serialNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showBleDialog by remember { mutableStateOf(false) }

    val isCustomServerEnabled by authPreferences.isCustomServerEnabled.collectAsState(initial = false)
    val customServerUrl by authPreferences.customServerUrl.collectAsState(initial = null)

    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Top right buttons for BLE Setup and Custom API Server Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showBleDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "BLE WiFi Setup",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { showServerDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Custom API Server Settings",
                    tint = if (isCustomServerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ElevatedCard(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isLogin) "Smart Home Login" else "Register Device",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    if (isCustomServerEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AssistChip(
                            onClick = { showServerDialog = true },
                            label = { 
                                Text(
                                    text = "Custom API: ${customServerUrl ?: "Local Server"}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = serialNumber,
                        onValueChange = { serialNumber = it },
                        label = { Text("Serial Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = otp,
                        onValueChange = { 
                            if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                                otp = it
                            }
                        },
                        label = { Text("8-digit OTP") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                val result = if (isLogin) {
                                    authRepository.login(serialNumber, otp)
                                } else {
                                    authRepository.register(serialNumber, otp)
                                }
                                
                                isLoading = false
                                if (result.isSuccess) {
                                    onLoginSuccess(serialNumber, otp)
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message ?: "An error occurred"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && serialNumber.isNotBlank() && otp.length == 8
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = LocalContentColor.current)
                        } else {
                            Text(if (isLogin) "Login" else "Register")
                        }
                    }

                    TextButton(onClick = { isLogin = !isLogin }) {
                        Text(if (isLogin) "Don't have an account? Register" else "Already registered? Login")
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedButton(
                        onClick = { showBleDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set up Pi WiFi via Bluetooth")
                    }
                }
            }
        }
    }

    if (showBleDialog) {
        BleProvisioningDialog(
            bleRepository = bleProvisioningRepository,
            onDismiss = { showBleDialog = false }
        )
    }

    if (showServerDialog) {
        CustomApiServerDialog(
            authPreferences = authPreferences,
            onDismiss = { showServerDialog = false }
        )
    }
}
