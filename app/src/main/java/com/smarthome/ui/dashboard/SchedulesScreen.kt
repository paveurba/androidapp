package com.smarthome.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smarthome.data.SensorRepository
import com.smarthome.data.SensorSchedule
import kotlinx.coroutines.launch

@Composable
fun SchedulesScreen(
    sensorRepository: SensorRepository
) {
    val schedules by sensorRepository.getSchedules().collectAsState(initial = emptyList())
    var editingSchedule by remember { mutableStateOf<SensorSchedule?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = "Device Schedules",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onEditClick = { editingSchedule = schedule }
                    )
                }
            }
        }

        if (editingSchedule != null) {
            ScheduleEditDialog(
                schedule = editingSchedule!!,
                allSchedules = schedules,
                onDismiss = { editingSchedule = null },
                onSave = { from, till ->
                    scope.launch {
                        try {
                            sensorRepository.updateSchedule(editingSchedule!!.id, from, till)
                            editingSchedule = null
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(e.message ?: "Update failed")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ScheduleCard(
    schedule: SensorSchedule,
    onEditClick: () -> Unit
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
                Text(text = schedule.sensorName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "From ${schedule.fromHour.formatHour()} till ${schedule.toHour.formatHour()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Schedule")
            }
        }
    }
}

private fun Int.formatHour() = "%02d:00".format(this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditDialog(
    schedule: SensorSchedule,
    allSchedules: List<SensorSchedule>,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var fromHour by remember { mutableFloatStateOf(schedule.fromHour.toFloat()) }
    var toHour by remember { mutableFloatStateOf(schedule.toHour.toFloat()) }

    val hasOverlap = remember(fromHour, toHour) {
        val start = fromHour.toInt()
        val end = toHour.toInt()
        
        // Validation logic
        val range1 = getHoursInRange(start, end)
        allSchedules.any { s ->
            if (s.id == schedule.id || s.sensorName != schedule.sensorName) return@any false
            val range2 = getHoursInRange(s.fromHour, s.toHour)
            range1.intersect(range2).isNotEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Schedule for ${schedule.sensorName}") },
        text = {
            Column {
                Text("From: ${fromHour.toInt().formatHour()}")
                Slider(
                    value = fromHour,
                    onValueChange = { fromHour = it },
                    valueRange = 0f..23f,
                    steps = 22
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Till: ${toHour.toInt().formatHour()}")
                Slider(
                    value = toHour,
                    onValueChange = { toHour = it },
                    valueRange = 0f..23f,
                    steps = 22
                )

                if (hasOverlap) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ This schedule overlaps with another window for this device.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                if (fromHour.toInt() == toHour.toInt()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start and End times cannot be the same.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(fromHour.toInt(), toHour.toInt()) },
                enabled = !hasOverlap && fromHour.toInt() != toHour.toInt()
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

private fun getHoursInRange(start: Int, end: Int): Set<Int> {
    val hours = mutableSetOf<Int>()
    var current = start
    while (current != end) {
        hours.add(current)
        current = (current + 1) % 24
    }
    return hours
}
