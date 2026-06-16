package com.smarthome.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(modifier = Modifier.fillMaxSize()) {
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
            onDismiss = { editingSchedule = null },
            onSave = { from, till ->
                scope.launch {
                    sensorRepository.updateSchedule(editingSchedule!!.id, from, till)
                    editingSchedule = null
                }
            }
        )
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
                    text = "From ${schedule.fromHour}:00 till ${schedule.toHour}:00",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Schedule")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditDialog(
    schedule: SensorSchedule,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var fromHour by remember { mutableStateOf(schedule.fromHour.toFloat()) }
    var toHour by remember { mutableStateOf(schedule.toHour.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Schedule for ${schedule.sensorName}") },
        text = {
            Column {
                Text("From: ${fromHour.toInt()}:00")
                Slider(
                    value = fromHour,
                    onValueChange = { fromHour = it },
                    valueRange = 0f..23f,
                    steps = 22
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Till: ${toHour.toInt()}:00")
                Slider(
                    value = toHour,
                    onValueChange = { toHour = it },
                    valueRange = 0f..23f,
                    steps = 22
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(fromHour.toInt(), toHour.toInt()) }) {
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
