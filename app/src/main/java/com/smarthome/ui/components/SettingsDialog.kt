package com.smarthome.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.smarthome.BuildConfig
import com.smarthome.data.AuthPreferences
import com.smarthome.data.DiscoveredDevice
import com.smarthome.data.PairingRepository
import com.smarthome.data.PairingStatus
import kotlinx.coroutines.launch

import com.smarthome.data.ble.BleProvisioningRepository
import com.smarthome.ui.components.BleProvisioningContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    authPreferences: AuthPreferences,
    pairingRepository: PairingRepository,
    bleRepository: BleProvisioningRepository,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Zigbee") },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Zigbee Pairing") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("WiFi Setup") },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "BLE WiFi Setup") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Server") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Server Endpoints") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> ZigbeePairingTabContent(pairingRepository = pairingRepository)
                    1 -> BleProvisioningContent(
                        bleRepository = bleRepository,
                        onFinish = onDismiss
                    )
                    2 -> ServerEndpointTabContent(
                        authPreferences = authPreferences,
                        onSaved = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun ZigbeePairingTabContent(
    pairingRepository: PairingRepository
) {
    val status by pairingRepository.getPairingStatus().collectAsState(initial = null)
    val discovered by pairingRepository.getDiscoveredDevices().collectAsState(initial = emptyList())
    var confirmingDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PairingStatusCard(
            status = status,
            onStart = {
                scope.launch {
                    try {
                        errorMessage = null
                        pairingRepository.startPairing()
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to start pairing"
                    }
                }
            },
            onStop = {
                scope.launch {
                    try {
                        pairingRepository.stopPairing()
                    } catch (e: Exception) {}
                }
            }
        )

        errorMessage?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (discovered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (status?.active == true)
                        "Listening for new devices...\nPress the pairing button on your Zigbee sensor or plug."
                    else
                        "No devices found.\nTap \"Start Pairing\" above to allow new Zigbee devices to join.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = "Discovered Devices (${discovered.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discovered, key = { it.id }) { device ->
                    DiscoveredDeviceCard(
                        device = device,
                        onConfirm = { confirmingDevice = device }
                    )
                }
            }
        }
    }

    confirmingDevice?.let { device ->
        NameDeviceDialog(
            device = device,
            onDismiss = { confirmingDevice = null },
            onConfirm = { name ->
                scope.launch {
                    try {
                        pairingRepository.confirmDevice(device.id, name)
                        confirmingDevice = null
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to add device"
                    }
                }
            }
        )
    }
}

@Composable
private fun PairingStatusCard(
    status: PairingStatus?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status?.active == true) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (status?.active == true) "Pairing Active" else "Pairing Inactive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (status?.active == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (status?.active == true) {
                    Text(
                        text = "Countdown: ${status.remainingSeconds}s remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Permit-join for Zigbee devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (status?.active == true) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop")
                }
            } else {
                Button(onClick = onStart) {
                    Text("Start Pairing")
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    onConfirm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceKindLabel(device),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val subtitle = listOfNotNull(
                    device.manufacturer.ifBlank { null },
                    device.model.ifBlank { null }
                ).joinToString(" ")
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onConfirm, enabled = device.isActuator || device.kind.isNotBlank()) {
                Text("Add")
            }
        }
    }
}

private fun deviceKindLabel(device: DiscoveredDevice): String = when {
    device.isActuator -> "Switch / plug"
    device.kind.isBlank() -> "Unrecognized device"
    else -> device.kind.replaceFirstChar { it.uppercase() }.replace("_", " ")
}

@Composable
private fun NameDeviceDialog(
    device: DiscoveredDevice,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val isValid = name.isNotBlank() && !name.contains("/")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this ${deviceKindLabel(device).lowercase()}") },
        text = {
            Column {
                Text(
                    text = "This becomes the device's permanent name, e.g. \"Kitchen\" or \"Hall Light\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friendly Name") },
                    singleLine = true,
                    isError = name.isNotBlank() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (name.isNotBlank() && !isValid) {
                    Text(
                        text = "Name cannot contain '/'",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }, enabled = isValid) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ServerEndpointTabContent(
    authPreferences: AuthPreferences,
    onSaved: () -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Configure custom local HTTP & WebSocket endpoints when operating directly on local network.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Custom Server Mode",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
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
            color = if (enabledState) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (enabledState) "Active Mode: Custom Server" else "Active Mode: Cloud Server (.env)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabledState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "HTTP: " + if (enabledState) {
                        inputUrl.ifBlank { BuildConfig.API_BASE_URL }
                    } else {
                        BuildConfig.API_BASE_URL
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "WebSocket: " + if (enabledState) {
                        if (inputWsUrl.isNotBlank()) inputWsUrl else "Auto-derived"
                    } else {
                        "Default"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Apply")
        }
    }
}
