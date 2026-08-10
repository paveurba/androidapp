package com.smarthome.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smarthome.data.LocalScheduleConfig
import com.smarthome.data.SensorRepository
import kotlinx.coroutines.launch

@Composable
fun SchedulesScreen(
    sensorRepository: SensorRepository
) {
    val configs by sensorRepository.getLocalScheduleConfigs().collectAsState(initial = emptyList())
    val relays by sensorRepository.getRelays().collectAsState(initial = emptyList())
    val schedulableDevices = remember(relays) {
        relays.flatMap { it.switches }.filter { it.schedulable }.map { it.label }.distinct().sorted()
    }

    var editingConfig by remember { mutableStateOf<LocalScheduleConfig?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (schedulableDevices.isNotEmpty()) {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Schedule")
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isTablet = maxWidth >= 600.dp

            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Device Schedules",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                if (configs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (schedulableDevices.isEmpty())
                                "No schedulable devices configured on this agent yet."
                            else
                                "No schedules yet - tap + to create one.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (isTablet) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(configs, key = { it.localId }) { config ->
                            ScheduleCard(
                                config = config,
                                onEditClick = { editingConfig = config },
                                onDeleteClick = { scope.launch { sensorRepository.deleteScheduleConfig(config.localId) } },
                                onEnabledChange = { enabled ->
                                    scope.launch {
                                        try {
                                            sensorRepository.setScheduleEnabled(config.localId, enabled)
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(e.message ?: "Failed to update schedule")
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(configs, key = { it.localId }) { config ->
                            ScheduleCard(
                                config = config,
                                onEditClick = { editingConfig = config },
                                onDeleteClick = { scope.launch { sensorRepository.deleteScheduleConfig(config.localId) } },
                                onEnabledChange = { enabled ->
                                    scope.launch {
                                        try {
                                            sensorRepository.setScheduleEnabled(config.localId, enabled)
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(e.message ?: "Failed to update schedule")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (editingConfig != null) {
                ScheduleTimeDialog(
                    title = "Edit Schedule for ${editingConfig!!.device}",
                    initialFromHour = editingConfig!!.fromHour,
                    initialFromMinute = editingConfig!!.fromMinute,
                    initialToHour = editingConfig!!.toHour,
                    initialToMinute = editingConfig!!.toMinute,
                    onDismiss = { editingConfig = null },
                    onSave = { fromH, fromM, toH, toM ->
                        scope.launch {
                            try {
                                sensorRepository.updateScheduleConfig(editingConfig!!.localId, fromH, fromM, toH, toM)
                                editingConfig = null
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "Update failed")
                            }
                        }
                    }
                )
            }

            if (creating) {
                CreateScheduleDialog(
                    devices = schedulableDevices,
                    onDismiss = { creating = false },
                    onCreate = { device, fromH, fromM, toH, toM ->
                        scope.launch {
                            try {
                                sensorRepository.createSchedule(device, fromH, fromM, toH, toM)
                                creating = false
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "Failed to create schedule")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ScheduleCard(
    config: LocalScheduleConfig,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
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
                Text(text = config.device, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "From ${formatTime(config.fromHour, config.fromMinute)} till ${formatTime(config.toHour, config.toMinute)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(checked = config.enabled, onCheckedChange = onEnabledChange)
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Schedule")
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Schedule")
            }
        }
    }
}

private fun formatTime(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimeDialog(
    title: String,
    initialFromHour: Int,
    initialFromMinute: Int,
    initialToHour: Int,
    initialToMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Int) -> Unit
) {
    var fromHour by remember { mutableFloatStateOf(initialFromHour.toFloat()) }
    var fromMinute by remember { mutableFloatStateOf(initialFromMinute.toFloat()) }
    var toHour by remember { mutableFloatStateOf(initialToHour.toFloat()) }
    var toMinute by remember { mutableFloatStateOf(initialToMinute.toFloat()) }

    val sameTime = fromHour.toInt() == toHour.toInt() && fromMinute.toInt() == toMinute.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("From: ${formatTime(fromHour.toInt(), fromMinute.toInt())}")
                Slider(value = fromHour, onValueChange = { fromHour = it }, valueRange = 0f..23f, steps = 22)
                Slider(value = fromMinute, onValueChange = { fromMinute = it }, valueRange = 0f..59f, steps = 58)

                Spacer(modifier = Modifier.height(16.dp))

                Text("Till: ${formatTime(toHour.toInt(), toMinute.toInt())}")
                Slider(value = toHour, onValueChange = { toHour = it }, valueRange = 0f..23f, steps = 22)
                Slider(value = toMinute, onValueChange = { toMinute = it }, valueRange = 0f..59f, steps = 58)

                if (sameTime) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start and end times cannot be the same.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(fromHour.toInt(), fromMinute.toInt(), toHour.toInt(), toMinute.toInt()) },
                enabled = !sameTime
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateScheduleDialog(
    devices: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int, Int, Int) -> Unit
) {
    var selectedDevice by remember { mutableStateOf(devices.first()) }
    var deviceMenuExpanded by remember { mutableStateOf(false) }
    var fromHour by remember { mutableFloatStateOf(0f) }
    var fromMinute by remember { mutableFloatStateOf(0f) }
    var toHour by remember { mutableFloatStateOf(0f) }
    var toMinute by remember { mutableFloatStateOf(0f) }

    val sameTime = fromHour.toInt() == toHour.toInt() && fromMinute.toInt() == toMinute.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Schedule") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = deviceMenuExpanded,
                    onExpandedChange = { deviceMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedDevice,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Device") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = deviceMenuExpanded,
                        onDismissRequest = { deviceMenuExpanded = false }
                    ) {
                        devices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device) },
                                onClick = {
                                    selectedDevice = device
                                    deviceMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("From: ${formatTime(fromHour.toInt(), fromMinute.toInt())}")
                Slider(value = fromHour, onValueChange = { fromHour = it }, valueRange = 0f..23f, steps = 22)
                Slider(value = fromMinute, onValueChange = { fromMinute = it }, valueRange = 0f..59f, steps = 58)

                Spacer(modifier = Modifier.height(16.dp))

                Text("Till: ${formatTime(toHour.toInt(), toMinute.toInt())}")
                Slider(value = toHour, onValueChange = { toHour = it }, valueRange = 0f..23f, steps = 22)
                Slider(value = toMinute, onValueChange = { toMinute = it }, valueRange = 0f..59f, steps = 58)

                if (sameTime) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start and end times cannot be the same.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(selectedDevice, fromHour.toInt(), fromMinute.toInt(), toHour.toInt(), toMinute.toInt()) },
                enabled = !sameTime
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
