package com.smarthome.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.smarthome.data.AlarmSensor
import com.smarthome.data.AlarmSensorRepository
import com.smarthome.data.SensorTilePosition
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Matches config.SensorKind on the agent - anything else is shown as-is,
// so an unrecognized future kind still renders instead of disappearing.
private fun kindLabel(kind: String): String = when (kind) {
    "contact" -> "Door/Window"
    "occupancy" -> "Motion"
    "water_leak" -> "Water Leak"
    "vibration" -> "Vibration"
    else -> kind
}

private fun triggeredLabel(kind: String): String = when (kind) {
    "contact" -> "Open"
    "occupancy" -> "Motion detected"
    "water_leak" -> "Leak detected"
    "vibration" -> "Vibration detected"
    else -> "Triggered"
}

private fun clearLabel(kind: String): String = when (kind) {
    "contact" -> "Closed"
    "occupancy" -> "Clear"
    "water_leak" -> "Dry"
    "vibration" -> "Still"
    else -> "OK"
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0) return "Never"
    val sdf = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSensorsScreen(
    alarmSensorRepository: AlarmSensorRepository
) {
    val sensors by alarmSensorRepository.getAlarmSensors().collectAsState(initial = emptyList())
    val tilePositions by alarmSensorRepository.getAlarmSensorTilePositions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedSensor by remember { mutableStateOf<AlarmSensor?>(null) }
    var editingSensorName by remember { mutableStateOf<AlarmSensor?>(null) }
    var confirmingDeleteSensor by remember { mutableStateOf<AlarmSensor?>(null) }
    val sensorDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            if (sensors.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No alarm sensors reporting yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                AlarmSensorDashboardCanvas(
                    sensors = sensors,
                    positions = tilePositions,
                    onSwap = { movedId, movedOrder, displacedId, displacedOrder ->
                        scope.launch {
                            alarmSensorRepository.swapAlarmSensorTilePositions(movedId, movedOrder, displacedId, displacedOrder)
                        }
                    },
                    onTileClick = { sensor ->
                        selectedSensor = sensor
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                )
            }
        }

        // Sensor Detail View (Sheet on phone, Dialog on tablet)
        selectedSensor?.let { sensor ->
            // Keep sensor data live if updated
            val liveSensor = sensors.find { it.id == sensor.id } ?: sensor
            val isBatteryLow = liveSensor.batteryLevel in 1..19

            val detailContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = liveSensor.name.ifBlank { liveSensor.id },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { editingSensorName = liveSensor },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename Alarm Sensor",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "ID: ${liveSensor.id} • ${kindLabel(liveSensor.kind)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Big Status Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (liveSensor.triggered) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (liveSensor.triggered) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (liveSensor.triggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (liveSensor.triggered) triggeredLabel(liveSensor.kind) else clearLabel(liveSensor.kind),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (liveSensor.triggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Battery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${liveSensor.batteryLevel}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isBatteryLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Link Quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${liveSensor.linkQuality} LQI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Last Updated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = formatTimestamp(liveSensor.lastUpdated),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = { confirmingDeleteSensor = liveSensor },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Sensor", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Alarm Sensor")
                }
            }

            if (isTablet) {
                Dialog(onDismissRequest = { selectedSensor = null }) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 6.dp,
                        modifier = Modifier.widthIn(max = 420.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            detailContent()
                        }
                    }
                }
            } else {
                ModalBottomSheet(
                    onDismissRequest = { selectedSensor = null },
                    sheetState = sensorDetailSheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        detailContent()
                    }
                }
            }
        }

        editingSensorName?.let { sensor ->
            var newName by remember { mutableStateOf(sensor.name.ifBlank { sensor.id }) }
            AlertDialog(
                onDismissRequest = { editingSensorName = null },
                title = { Text("Rename Alarm Sensor") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sensor ID: ${sensor.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Sensor Friendly Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = newName.trim()
                            if (trimmed.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        alarmSensorRepository.updateAlarmSensorName(sensor.id, trimmed)
                                    } catch (e: Exception) {}
                                }
                            }
                            editingSensorName = null
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingSensorName = null }) { Text("Cancel") }
                }
            )
        }

        confirmingDeleteSensor?.let { sensor ->
            AlertDialog(
                onDismissRequest = { confirmingDeleteSensor = null },
                title = { Text("Delete Alarm Sensor?") },
                text = { Text("Are you sure you want to delete '${sensor.name.ifBlank { sensor.id }}' (${sensor.id})? Its MQTT subscription will be stopped.") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    alarmSensorRepository.deleteAlarmSensor(sensor.id)
                                    selectedSensor = null
                                } catch (e: Exception) {}
                            }
                            confirmingDeleteSensor = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingDeleteSensor = null }) { Text("Cancel") }
                }
            )
        }
    }
}

/**
 * Thin [DashboardCanvas] wrapper for alarm sensors - passes [onTileClick] so
 * tapping an alarm sensor tile opens the detail sheet with rename & delete.
 */
@Composable
fun AlarmSensorDashboardCanvas(
    sensors: List<AlarmSensor>,
    positions: List<SensorTilePosition>,
    onSwap: (movedId: String, movedOrder: Int, displacedId: String, displacedOrder: Int) -> Unit,
    onTileClick: (AlarmSensor) -> Unit = {},
    modifier: Modifier = Modifier
) {
    DashboardCanvas(
        items = sensors,
        itemId = { it.id },
        positions = positions,
        onSwap = onSwap,
        onItemClick = onTileClick,
        modifier = modifier
    ) { sensor, sizeDp ->
        AlarmSensorTile(sensor = sensor, sizeDp = sizeDp)
    }
}

/**
 * Compact square dashboard tile - the alarm-sensor counterpart of
 * [SensorTile].
 */
@Composable
fun AlarmSensorTile(sensor: AlarmSensor, sizeDp: Dp, modifier: Modifier = Modifier) {
    val isBatteryLow = sensor.batteryLevel in 1..19
    val isStale = sensor.lastUpdated > 0 && (System.currentTimeMillis() - sensor.lastUpdated > 3600000)
    val displayName = sensor.name.ifBlank { sensor.id }

    Card(
        modifier = modifier.size(sizeDp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sensor.triggered)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isStale) {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(8.dp)
                            .background(color = Color(0xFFF57C00), shape = CircleShape)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (sensor.triggered) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = if (sensor.triggered) triggeredLabel(sensor.kind) else clearLabel(sensor.kind),
                    tint = if (sensor.triggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (sensor.triggered) triggeredLabel(sensor.kind) else clearLabel(sensor.kind),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (sensor.triggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = kindLabel(sensor.kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = HANDLE_CLEARANCE),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Batt ${sensor.batteryLevel}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBatteryLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Link ${sensor.linkQuality}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
