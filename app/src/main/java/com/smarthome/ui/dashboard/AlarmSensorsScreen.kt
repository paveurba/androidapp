package com.smarthome.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smarthome.data.AlarmSensor
import com.smarthome.data.AlarmSensorRepository

private val alarmTimeFormatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

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

/**
 * Read-only - these devices have no on/off command, just a live condition
 * (see AlarmSensor/AlarmSensorRepository). Works both on the local network
 * and through the cloud, same as the Sensors/Relays tabs.
 */
@Composable
fun AlarmSensorsScreen(
    alarmSensorRepository: AlarmSensorRepository
) {
    val sensors by alarmSensorRepository.getAlarmSensors().collectAsState(initial = emptyList())

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Alarm Sensors",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

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
            } else if (isTablet) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(sensors, key = { it.id }) { sensor -> AlarmSensorCard(sensor) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sensors, key = { it.id }) { sensor -> AlarmSensorCard(sensor) }
                }
            }
        }
    }
}

@Composable
fun AlarmSensorCard(sensor: AlarmSensor) {
    val isBatteryLow = sensor.batteryLevel in 1..19 // 0 usually just means "never reported a battery field"
    val isStale = System.currentTimeMillis() - sensor.lastUpdated > 3600000 // 1 hour

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sensor.triggered)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = sensor.id, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = kindLabel(sensor.kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Icon(
                    imageVector = if (sensor.triggered) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = if (sensor.triggered) triggeredLabel(sensor.kind) else clearLabel(sensor.kind),
                    tint = if (sensor.triggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (sensor.triggered) triggeredLabel(sensor.kind) else clearLabel(sensor.kind),
                style = MaterialTheme.typography.bodyMedium,
                color = if (sensor.triggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SensorInfoItem(
                    label = "Battery",
                    value = "${sensor.batteryLevel}%",
                    valueColor = if (isBatteryLow) MaterialTheme.colorScheme.error else Color.Unspecified
                )
                SensorInfoItem(label = "Link", value = "${sensor.linkQuality}")
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last updated: ${alarmTimeFormatter.format(java.util.Date(sensor.lastUpdated))}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isStale) Color(0xFFF57C00) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
