package com.smarthome.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smarthome.data.DiscoveredDevice
import com.smarthome.data.PairingRepository
import kotlinx.coroutines.launch

// New Zigbee sensors/actuators join the network the same way regardless of
// which agent (Pi direct, or via the cloud relay) receives the request -
// see PairingRepository's doc comment.
@Composable
fun PairingScreen(
    pairingRepository: PairingRepository
) {
    val status by pairingRepository.getPairingStatus().collectAsState(initial = null)
    val discovered by pairingRepository.getDiscoveredDevices().collectAsState(initial = emptyList())

    var confirmingDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // No separate in-body title here - the TopAppBar
            // (DashboardScreen) already reads "Pairing" for this tab.
            PairingStatusCard(
                status = status,
                onStart = {
                    scope.launch {
                        try {
                            pairingRepository.startPairing()
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(e.message ?: "Failed to start pairing")
                        }
                    }
                },
                onStop = { scope.launch { pairingRepository.stopPairing() } }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (discovered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (status?.active == true)
                            "Listening for new devices - press the pairing button on your Zigbee sensor or plug."
                        else
                            "No devices found. Tap \"Start Pairing\" above, then press the pairing button on your Zigbee device.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            snackbarHostState.showSnackbar(e.message ?: "Failed to add device")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PairingStatusCard(
    status: com.smarthome.data.PairingStatus?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (status?.active == true) "Pairing open" else "Pairing closed",
                    style = MaterialTheme.typography.titleMedium
                )
                if (status?.active == true) {
                    Text(
                        text = "Closes in ${status.remainingSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Opens the Zigbee network for 2 minutes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (status?.active == true) {
                Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    text = deviceKindLabel(device),
                    style = MaterialTheme.typography.titleMedium,
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    label = { Text("Name") },
                    singleLine = true,
                    isError = name.isNotBlank() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (name.isNotBlank() && !isValid) {
                    Text(
                        text = "Name can't contain '/'",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = isValid) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
