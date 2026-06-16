package com.smarthome.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smarthome.data.NotificationRepository
import com.smarthome.data.SensorRepository
import com.smarthome.data.TempSensor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TempUnit { CELSIUS, FAHRENHEIT }

fun Float.toUnit(unit: TempUnit): Float = if (unit == TempUnit.FAHRENHEIT) (this * 9/5) + 32 else this
fun TempUnit.symbol(): String = if (this == TempUnit.FAHRENHEIT) "°F" else "°C"

private val timeFormatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    sensorRepository: SensorRepository,
    notificationRepository: NotificationRepository,
    onLogout: () -> Unit
) {
    val sensors by sensorRepository.getSensors().collectAsState(initial = emptyList())
    val notifications by notificationRepository.getNotifications().collectAsState(initial = emptyList())
    val unreadCount = notifications.count { !it.isRead }
    
    var selectedSensor by remember { mutableStateOf<TempSensor?>(null) }
    var tempUnit by remember { mutableStateOf(TempUnit.CELSIUS) }
    var currentTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Sync selected sensor with the latest data from the repository
    LaunchedEffect(sensors) {
        selectedSensor?.let { current ->
            selectedSensor = sensors.find { it.id == current.id }
        }
    }

    // Simulate an incoming "Push Notification" from server
    LaunchedEffect(Unit) {
        delay(15000) // 15 seconds after app start
        notificationRepository.addNotification(
            "Energy Report", 
            "Your weekly energy consumption decreased by 12%!"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(when(currentTab) {
                        0 -> "Smart Home"
                        1 -> "Schedules"
                        2 -> "Relays"
                        else -> "Notifications"
                    }) 
                },
                actions = {
                    if (currentTab == 0) {
                        TextButton(onClick = {
                            tempUnit = if (tempUnit == TempUnit.CELSIUS) TempUnit.FAHRENHEIT else TempUnit.CELSIUS
                        }) {
                            Text(if (tempUnit == TempUnit.CELSIUS) "°C" else "°F")
                        }
                    }
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            if (selectedSensor == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Sensors") },
                        label = { Text("Sensors") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Schedules") },
                        label = { Text("Schedules") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.List, contentDescription = "Relays") },
                        label = { Text("Relays") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(unreadCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                            }
                        },
                        label = { Text("Alerts") }
                    )
                }
            }
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
                        setTemp = selectedSensor!!.setTemp,
                        unit = tempUnit,
                        onSetTempChanged = { newTemp ->
                            scope.launch {
                                // Repository expects Celsius
                                sensorRepository.updateSetTemp(selectedSensor!!.id, newTemp)
                            }
                        }
                    )
                }
            } else {
                when (currentTab) {
                    0 -> {
                        // List View: All Sensors
                        Column {
                            Text(
                                text = "Smart Sensors",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(sensors, key = { it.id }) { sensor ->
                                    SensorCard(
                                        sensor = sensor,
                                        unit = tempUnit,
                                        onClick = { selectedSensor = sensor }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        SchedulesScreen(sensorRepository = sensorRepository)
                    }
                    2 -> {
                        RelaysScreen(sensorRepository = sensorRepository)
                    }
                    3 -> {
                        NotificationsScreen(notificationRepository = notificationRepository)
                    }
                }
            }
        }
    }
}

@Composable
fun SensorCard(sensor: TempSensor, unit: TempUnit, onClick: () -> Unit) {
    val isHeatingNeeded = sensor.currentTemp < sensor.setTemp
    val isBatteryLow = sensor.batteryLevel < 20
    val isStale = System.currentTimeMillis() - sensor.lastUpdated > 3600000 // 1 hour

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHeatingNeeded) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
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
                    Text(text = sensor.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Set: ${"%.1f".format(sensor.setTemp.toUnit(unit))}${unit.symbol()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "${"%.1f".format(sensor.currentTemp.toUnit(unit))}${unit.symbol()}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isHeatingNeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SensorInfoItem(label = "Humidity", value = "${sensor.humidity.toInt()}%")
                SensorInfoItem(
                    label = "Battery", 
                    value = "${sensor.batteryLevel}%",
                    valueColor = if (isBatteryLow) MaterialTheme.colorScheme.error else Color.Unspecified
                )
                SensorInfoItem(label = "Link", value = "${sensor.linkQuality}")
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last updated: ${timeFormatter.format(java.util.Date(sensor.lastUpdated))}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isStale) Color(0xFFF57C00) else MaterialTheme.colorScheme.outline, // Orange if stale
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun SensorInfoItem(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(
            text = value, 
            style = MaterialTheme.typography.bodySmall, 
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = valueColor
        )
    }
}
