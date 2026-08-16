package com.smarthome.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.smarthome.data.*
import kotlinx.coroutines.launch

// Shared by RelaysScreen's SwitchItem and SchedulesScreen's ScheduleCard so
// every on/off toggle in the app is the same (smaller-than-default) size.
internal const val SWITCH_SCALE = 0.8f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaysScreen(
    sensorRepository: SensorRepository
) {
    val relays by sensorRepository.getRelays().collectAsState(initial = emptyList())
    val sensors by sensorRepository.getSensors().collectAsState(initial = emptyList())
    val pumpConfig by sensorRepository.getPumpConfig().collectAsState(initial = PumpConfig())
    val garden by sensorRepository.getGarden().collectAsState(initial = GardenResponse())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state
    var showAddRelayDialog by remember { mutableStateOf(false) }
    var editingRelay by remember { mutableStateOf<Relay?>(null) }
    var addingChannelRelay by remember { mutableStateOf<Relay?>(null) }
    var editingChannel by remember { mutableStateOf<Pair<Relay, RelaySwitch>?>(null) }
    var showPumpDialog by remember { mutableStateOf(false) }
    var showGardenDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddRelayDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Relay") },
                text = { Text("Add Relay") }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isTablet = maxWidth >= 600.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top Action Bar: Quick access to Heating Pump and Garden Watering
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Heating Pump Button / Card
                    OutlinedCard(
                        onClick = { showPumpDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (pumpConfig.enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Heating Pump",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (pumpConfig.enabled && pumpConfig.relay.isNotBlank()) "${pumpConfig.relay}/${pumpConfig.switch} (Active)" else "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pumpConfig.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Configure Pump",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Garden Watering Button / Card
                    OutlinedCard(
                        onClick = { showGardenDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (garden.status.running) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Garden Watering",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (garden.status.running) "Watering Loop ${garden.status.currentLoop}/${garden.status.totalLoops}" else "${garden.config.ports.size} Zones configured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (garden.status.running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                if (garden.status.running) Icons.Default.PlayArrow else Icons.Default.Settings,
                                contentDescription = "Garden Watering",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                if (isTablet) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalItemSpacing = 16.dp,
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(relays, key = { it.id }) { relay ->
                            RelayCard(
                                relay = relay,
                                onSwitchToggle = { switchId ->
                                    scope.launch {
                                        sensorRepository.toggleRelaySwitch(relay.id, switchId)
                                    }
                                },
                                onEditRelay = { editingRelay = relay },
                                onAddChannel = { addingChannelRelay = relay },
                                onEditChannel = { sw -> editingChannel = Pair(relay, sw) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(relays, key = { it.id }) { relay ->
                            RelayCard(
                                relay = relay,
                                onSwitchToggle = { switchId ->
                                    scope.launch {
                                        sensorRepository.toggleRelaySwitch(relay.id, switchId)
                                    }
                                },
                                onEditRelay = { editingRelay = relay },
                                onAddChannel = { addingChannelRelay = relay },
                                onEditChannel = { sw -> editingChannel = Pair(relay, sw) }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showAddRelayDialog) {
        AddRelayModuleDialog(
            onDismiss = { showAddRelayDialog = false },
            onAdd = { id, name, displayInverted, normalOpen ->
                scope.launch {
                    try {
                        sensorRepository.createRelay(id, name, displayInverted, normalOpen)
                        snackbarHostState.showSnackbar("Relay module $id added")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                    showAddRelayDialog = false
                }
            }
        )
    }

    editingRelay?.let { relay ->
        EditRelayModuleDialog(
            relay = relay,
            onDismiss = { editingRelay = null },
            onSave = { name, displayInverted, normalOpen ->
                scope.launch {
                    try {
                        sensorRepository.updateRelay(relay.id, name = name, displayInverted = displayInverted, normalOpen = normalOpen)
                        snackbarHostState.showSnackbar("Relay module updated")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                    editingRelay = null
                }
            },
            onDelete = {
                scope.launch {
                    try {
                        sensorRepository.deleteRelay(relay.id)
                        snackbarHostState.showSnackbar("Relay module deleted")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                    editingRelay = null
                }
            }
        )
    }

    addingChannelRelay?.let { relay ->
        AddChannelDialog(
            relay = relay,
            sensors = sensors,
            onDismiss = { addingChannelRelay = null },
            onAdd = { switchId, label, schedulable ->
                scope.launch {
                    try {
                        sensorRepository.createRelaySwitch(relay.id, switchId, label, schedulable)
                        snackbarHostState.showSnackbar("Channel $switchId added")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                    addingChannelRelay = null
                }
            }
        )
    }

    editingChannel?.let { (relay, sw) ->
        EditChannelDialog(
            relay = relay,
            switch = sw,
            sensors = sensors,
            onDismiss = { editingChannel = null },
            onSave = { label, schedulable ->
                scope.launch {
                    try {
                        sensorRepository.updateRelaySwitch(relay.id, sw.id, label = label, schedulable = schedulable)
                        snackbarHostState.showSnackbar("Channel ${sw.id} updated")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                    editingChannel = null
                }
            },
            onDelete = {
                scope.launch {
                    try {
                        sensorRepository.deleteRelaySwitch(relay.id, sw.id)
                        snackbarHostState.showSnackbar("Channel ${sw.id} deleted")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                    editingChannel = null
                }
            }
        )
    }

    if (showPumpDialog) {
        HeatingPumpDialog(
            pumpConfig = pumpConfig,
            relays = relays,
            onDismiss = { showPumpDialog = false },
            onSave = { relay, switch, enabled ->
                scope.launch {
                    try {
                        sensorRepository.updatePumpConfig(relay = relay, switch = switch, enabled = enabled)
                        snackbarHostState.showSnackbar("Heating pump settings updated")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                    showPumpDialog = false
                }
            }
        )
    }

    if (showGardenDialog) {
        GardenWateringDialog(
            garden = garden,
            relays = relays,
            onDismiss = { showGardenDialog = false },
            onUpdateConfig = { ports, loopCount, interval ->
                scope.launch {
                    try {
                        sensorRepository.updateGardenConfig(ports = ports, loopCount = loopCount, interval = interval)
                        snackbarHostState.showSnackbar("Garden watering config saved")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                }
            },
            onStart = { loopCount, interval ->
                scope.launch {
                    try {
                        sensorRepository.startGarden(loopCount, interval)
                        snackbarHostState.showSnackbar("Watering started")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                }
            },
            onStop = {
                scope.launch {
                    try {
                        sensorRepository.stopGarden()
                        snackbarHostState.showSnackbar("Watering stopped")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
fun RelayCard(
    relay: Relay,
    onSwitchToggle: (String) -> Unit,
    onEditRelay: () -> Unit,
    onAddChannel: () -> Unit,
    onEditChannel: (RelaySwitch) -> Unit
) {
    val isPhysical = relay.id != "devices"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = relay.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "Module: ${relay.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (relay.normalOpen) {
                            Text(
                                text = "• NO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (relay.displayInverted) {
                            Text(
                                text = "• Inverted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                if (isPhysical) {
                    IconButton(onClick = onEditRelay) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Relay Module", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                relay.switches.chunked(2).forEach { rowSwitches ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowSwitches.forEach { relaySwitch ->
                            SwitchItem(
                                relaySwitch = relaySwitch,
                                displayInverted = relay.displayInverted,
                                isEditable = true,
                                onToggle = { onSwitchToggle(relaySwitch.id) },
                                onEdit = { onEditChannel(relaySwitch) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowSwitches.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (isPhysical) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAddChannel,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Channel", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Add Channel", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun SwitchItem(
    relaySwitch: RelaySwitch,
    displayInverted: Boolean = false,
    isEditable: Boolean = true,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayIsOn = relaySwitch.isOn xor displayInverted

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp),
        color = if (displayIsOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = isEditable) { onEdit() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = relaySwitch.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (relaySwitch.schedulable) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Schedulable",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = relaySwitch.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isEditable) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Channel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Switch(
                checked = displayIsOn,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(SWITCH_SCALE)
            )
        }
    }
}

// --- Add Relay Module Dialog ---

@Composable
fun AddRelayModuleDialog(
    onDismiss: () -> Unit,
    onAdd: (id: String, name: String, displayInverted: Boolean, normalOpen: Boolean) -> Unit
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var displayInverted by remember { mutableStateOf(false) }
    var normalOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Relay Module") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Module ID (e.g. tasmota8)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friendly Name (e.g. Garage)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Normally Open (NO)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Inverts MQTT command payload (ON <-> OFF) for normally open hardware", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = normalOpen, onCheckedChange = { normalOpen = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Display Inverted", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Inverts the visual switch status on this screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = displayInverted, onCheckedChange = { displayInverted = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(id.trim(), name.trim().ifEmpty { id.trim() }, displayInverted, normalOpen) },
                enabled = id.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// --- Edit Relay Module Dialog ---

@Composable
fun EditRelayModuleDialog(
    relay: Relay,
    onDismiss: () -> Unit,
    onSave: (name: String, displayInverted: Boolean, normalOpen: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(relay.name) }
    var displayInverted by remember { mutableStateOf(relay.displayInverted) }
    var normalOpen by remember { mutableStateOf(relay.normalOpen) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Relay Module (${relay.id})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Module Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Normally Open (NO)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Inverts MQTT command payload (ON <-> OFF) for normally open hardware", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = normalOpen, onCheckedChange = { normalOpen = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Display Inverted", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Inverts the visual switch status on this screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = displayInverted, onCheckedChange = { displayInverted = it })
                }

                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Relay Module")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim().ifEmpty { relay.id }, displayInverted, normalOpen) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Relay Module?") },
            text = { Text("Are you sure you want to delete module '${relay.name}' (${relay.id}) and all of its channels? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// --- Edit Channel Dialog (with Sensor Mapping dropdown) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditChannelDialog(
    relay: Relay,
    switch: RelaySwitch,
    sensors: List<TempSensor>,
    onDismiss: () -> Unit,
    onSave: (label: String, schedulable: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var label by remember { mutableStateOf(switch.label) }
    var schedulable by remember { mutableStateOf(switch.schedulable) }
    var showSensorDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Channel ${switch.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Module: ${relay.name} (${relay.id}) • Channel ID: ${switch.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Sensor Mapping / Channel Label
                Column {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Channel Label / Sensor Mapping") },
                        supportingText = { Text("Set to a sensor name (e.g. Hall) to link floor heating to that sensor") },
                        trailingIcon = {
                            IconButton(onClick = { showSensorDropdown = !showSensorDropdown }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Sensor")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = showSensorDropdown,
                        onDismissRequest = { showSensorDropdown = false }
                    ) {
                        if (sensors.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No sensors found") },
                                onClick = { showSensorDropdown = false }
                            )
                        } else {
                            sensors.forEach { sensor ->
                                DropdownMenuItem(
                                    text = { Text("${sensor.name} (${sensor.id})") },
                                    onClick = {
                                        label = sensor.id
                                        showSensorDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Schedulable", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Enable to allow setting on/off schedules for this switch in the Schedules tab", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = schedulable, onCheckedChange = { schedulable = it })
                }

                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Channel")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(label.trim().ifEmpty { switch.id }, schedulable) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Channel?") },
            text = { Text("Are you sure you want to delete channel '${switch.label}' (${switch.id}) from module '${relay.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// --- Add Channel Dialog ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChannelDialog(
    relay: Relay,
    sensors: List<TempSensor>,
    onDismiss: () -> Unit,
    onAdd: (switchId: String, label: String, schedulable: Boolean) -> Unit
) {
    var switchId by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var schedulable by remember { mutableStateOf(false) }
    var showSensorDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Channel to ${relay.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = switchId,
                    onValueChange = { switchId = it },
                    label = { Text("Channel ID (e.g. POWER2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Channel Label / Sensor Mapping") },
                        supportingText = { Text("Select temperature sensor to map heating control") },
                        trailingIcon = {
                            IconButton(onClick = { showSensorDropdown = !showSensorDropdown }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Sensor")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = showSensorDropdown,
                        onDismissRequest = { showSensorDropdown = false }
                    ) {
                        sensors.forEach { sensor ->
                            DropdownMenuItem(
                                text = { Text("${sensor.name} (${sensor.id})") },
                                onClick = {
                                    label = sensor.id
                                    showSensorDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Schedulable", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Allow on/off schedules in the Schedules tab", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = schedulable, onCheckedChange = { schedulable = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(switchId.trim(), label.trim().ifEmpty { switchId.trim() }, schedulable) },
                enabled = switchId.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// --- Heating Pump Dialog ---

@Composable
fun HeatingPumpDialog(
    pumpConfig: PumpConfig,
    relays: List<Relay>,
    onDismiss: () -> Unit,
    onSave: (relay: String, switch: String, enabled: Boolean) -> Unit
) {
    var enabled by remember { mutableStateOf(pumpConfig.enabled) }
    var selectedRelay by remember { mutableStateOf(pumpConfig.relay) }
    var selectedSwitch by remember { mutableStateOf(pumpConfig.switch) }

    var showRelayDropdown by remember { mutableStateOf(false) }
    var showSwitchDropdown by remember { mutableStateOf(false) }

    val physicalRelays = remember(relays) { relays.filter { it.id != "devices" } }
    val currentRelayObj = remember(selectedRelay, physicalRelays) { physicalRelays.find { it.id == selectedRelay } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Heating Circulation Pump") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Controls the central heating circulation pump. The pump turns on when all active rooms call for heat, with a 2-minute settling delay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Heating Pump", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                // Relay Module Selection
                Column {
                    OutlinedTextField(
                        value = selectedRelay,
                        onValueChange = { selectedRelay = it },
                        label = { Text("Pump Relay Module") },
                        trailingIcon = {
                            IconButton(onClick = { showRelayDropdown = !showRelayDropdown }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Relay")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = showRelayDropdown,
                        onDismissRequest = { showRelayDropdown = false }
                    ) {
                        physicalRelays.forEach { r ->
                            DropdownMenuItem(
                                text = { Text("${r.name} (${r.id})") },
                                onClick = {
                                    selectedRelay = r.id
                                    showRelayDropdown = false
                                }
                            )
                        }
                    }
                }

                // Switch Channel Selection
                Column {
                    OutlinedTextField(
                        value = selectedSwitch,
                        onValueChange = { selectedSwitch = it },
                        label = { Text("Pump Switch Channel (e.g. POWER8)") },
                        trailingIcon = {
                            IconButton(onClick = { showSwitchDropdown = !showSwitchDropdown }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Channel")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = showSwitchDropdown,
                        onDismissRequest = { showSwitchDropdown = false }
                    ) {
                        currentRelayObj?.switches?.forEach { sw ->
                            DropdownMenuItem(
                                text = { Text("${sw.id} (${sw.label})") },
                                onClick = {
                                    selectedSwitch = sw.id
                                    showSwitchDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedRelay.trim(), selectedSwitch.trim(), enabled) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// --- Garden Watering Dialog ---

@Composable
fun GardenWateringDialog(
    garden: GardenResponse,
    relays: List<Relay>,
    onDismiss: () -> Unit,
    onUpdateConfig: (ports: List<GardenPort>, loopCount: Int, interval: Int) -> Unit,
    onStart: (loopCount: Int, interval: Int) -> Unit,
    onStop: () -> Unit
) {
    val scrollState = rememberScrollState()
    var loopCountText by remember { mutableStateOf(garden.config.defaultLoopCount.toString()) }
    var intervalText by remember { mutableStateOf(garden.config.defaultInterval.toString()) }
    var ports by remember { mutableStateOf(garden.config.ports) }

    var newPortRelay by remember { mutableStateOf("") }
    var newPortSwitch by remember { mutableStateOf("") }

    val physicalRelays = remember(relays) { relays.filter { it.id != "devices" } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Garden Watering Automation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Live status card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (garden.status.running) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (garden.status.running) "STATUS: WATERING IN PROGRESS" else "STATUS: IDLE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (garden.status.running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (garden.status.running) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Loop ${garden.status.currentLoop} of ${garden.status.totalLoops} • Active Zone: ${garden.status.currentZone}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (garden.status.remainingSecs > 0) {
                                Text(
                                    text = "~${garden.status.remainingSecs}s estimated remaining",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onStop,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Stop Watering")
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val l = loopCountText.toIntOrNull() ?: 10
                                    val i = intervalText.toIntOrNull() ?: 180
                                    onStart(l, i)
                                },
                                enabled = ports.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Watering Now")
                            }
                        }
                    }
                }

                Divider()

                // Configuration parameters
                Text("Watering Sequence Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = loopCountText,
                        onValueChange = { loopCountText = it },
                        label = { Text("Loops") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it },
                        label = { Text("Zone Secs") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Configured Watering Zones
                Text("Watering Zones (${ports.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ports.forEachIndexed { index, port ->
                        Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Zone ${index + 1}: ${port.relay} / ${port.switch}", style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { ports = ports.filterIndexed { i, _ -> i != index } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Zone", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // Add Zone row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPortRelay,
                        onValueChange = { newPortRelay = it },
                        label = { Text("Relay (e.g. tasmota7)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = newPortSwitch,
                        onValueChange = { newPortSwitch = it },
                        label = { Text("Switch (e.g. POWER1)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newPortRelay.isNotBlank() && newPortSwitch.isNotBlank()) {
                                ports = ports + GardenPort(newPortRelay.trim(), newPortSwitch.trim())
                                newPortSwitch = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Zone")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val l = loopCountText.toIntOrNull() ?: 10
                            val i = intervalText.toIntOrNull() ?: 180
                            onUpdateConfig(ports, l, i)
                        }
                    ) {
                        Text("Save Config")
                    }
                }
            }
        }
    }
}

