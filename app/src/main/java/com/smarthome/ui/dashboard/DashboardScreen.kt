package com.smarthome.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smarthome.data.SensorRepository
import com.smarthome.data.TempSensor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    sensorRepository: SensorRepository,
    onLogout: () -> Unit
) {
    val sensors by sensorRepository.getSensors().collectAsState(initial = emptyList())
    var selectedSensor by remember { mutableStateOf<TempSensor?>(null) }
    val scope = rememberCoroutineScope()

    // Sync selected sensor with the latest data from the repository
    LaunchedEffect(sensors) {
        selectedSensor?.let { current ->
            selectedSensor = sensors.find { it.id == current.id }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Home Dashboard") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedSensor != null) {
                // Detail View: Thermostat Control
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { selectedSensor = null },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Text("< Back")
                        }
                    }
                    
                    Text(
                        text = selectedSensor!!.name,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    ThermostatControl(
                        currentTemp = selectedSensor!!.currentTemp,
                        targetTemp = selectedSensor!!.targetTemp,
                        onTargetTempChanged = { newTemp ->
                            scope.launch {
                                sensorRepository.updateTargetTemp(selectedSensor!!.id, newTemp)
                            }
                        }
                    )
                }
            } else {
                // List View: All Sensors
                Column {
                    Text(
                        text = "Temperature Sensors",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sensors) { sensor ->
                            SensorCard(sensor = sensor, onClick = { selectedSensor = sensor })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorCard(sensor: TempSensor, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Text(text = sensor.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "Target: ${sensor.targetTemp}°C", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "${sensor.currentTemp}°C",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
