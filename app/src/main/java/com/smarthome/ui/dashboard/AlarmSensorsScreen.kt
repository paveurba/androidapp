package com.smarthome.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smarthome.data.AlarmSensor
import com.smarthome.data.AlarmSensorRepository
import kotlinx.coroutines.launch

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
 * (see AlarmSensor/AlarmSensorRepository), so unlike the sensor dashboard a
 * tile tap does nothing; only the corner drag handle to reposition is wired up.
 * Same dashing.io-style freeform block layout as the temp-sensor dashboard
 * (see DashboardCanvas), sharing its grid math and drag handling but with
 * its own persisted layout (AlarmSensorLayoutStore) - alarm sensor ids and
 * temp sensor ids are different id spaces, so they don't share a store.
 */
@Composable
fun AlarmSensorsScreen(
    alarmSensorRepository: AlarmSensorRepository
) {
    val sensors by alarmSensorRepository.getAlarmSensors().collectAsState(initial = emptyList())
    val tilePositions by alarmSensorRepository.getAlarmSensorTilePositions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // No separate in-body title or Reset Layout button here - the
        // TopAppBar (DashboardScreen) already reads "Alarm Sensors" for
        // this tab and now carries the reset action as an icon (and its
        // confirmation dialog) itself, shared with the Sensors tab.
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Thin [DashboardCanvas] wrapper for alarm sensors - see
 * [SensorDashboardCanvas] for the temp-sensor counterpart. onItemClick is
 * left at its no-op default since alarm sensors have no detail view to open.
 */
@Composable
fun AlarmSensorDashboardCanvas(
    sensors: List<AlarmSensor>,
    positions: List<com.smarthome.data.SensorTilePosition>,
    onSwap: (movedId: String, movedOrder: Int, displacedId: String, displacedOrder: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardCanvas(
        items = sensors,
        itemId = { it.id },
        positions = positions,
        onSwap = onSwap,
        modifier = modifier
    ) { sensor, sizeDp ->
        AlarmSensorTile(sensor = sensor, sizeDp = sizeDp)
    }
}

/**
 * Compact square dashboard tile - the alarm-sensor counterpart of
 * [SensorTile]. Keeps the same status rules as the old full-width
 * [AlarmSensorCard] (triggered = red tint + warning icon, low-battery red,
 * stale-reading warning) in less space: a small colored dot replaces the
 * old "Last updated: HH:mm:ss" text line.
 */
@Composable
fun AlarmSensorTile(sensor: AlarmSensor, sizeDp: Dp, modifier: Modifier = Modifier) {
    val isBatteryLow = sensor.batteryLevel in 1..19 // 0 usually just means "never reported a battery field"
    val isStale = System.currentTimeMillis() - sensor.lastUpdated > 3600000 // 1 hour

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
                    text = sensor.id,
                    style = MaterialTheme.typography.titleSmall,
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
                    tint = if (sensor.triggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (sensor.triggered) triggeredLabel(sensor.kind) else clearLabel(sensor.kind),
                    style = MaterialTheme.typography.labelSmall,
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

            // End-padded so this bottom-most row's trailing text doesn't run
            // under the corner DragHandle DraggableTile overlays on top of
            // this card - see DraggableTile's doc comment (SensorDashboard.kt).
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
