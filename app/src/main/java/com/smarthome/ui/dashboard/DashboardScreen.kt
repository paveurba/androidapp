package com.smarthome.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.smarthome.data.AgentStatusRepository
import com.smarthome.data.AlarmSensorRepository
import com.smarthome.data.AuthPreferences
import com.smarthome.data.NotificationRepository
import com.smarthome.data.PairingRepository
import com.smarthome.data.SensorRepository
import com.smarthome.data.TempSensor
import com.smarthome.ui.components.CustomApiServerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TempUnit { CELSIUS, FAHRENHEIT }

fun Float.toUnit(unit: TempUnit): Float = if (unit == TempUnit.FAHRENHEIT) (this * 9/5) + 32 else this
fun TempUnit.symbol(): String = if (this == TempUnit.FAHRENHEIT) "°F" else "°C"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    authPreferences: AuthPreferences,
    sensorRepository: SensorRepository,
    notificationRepository: NotificationRepository,
    alarmSensorRepository: AlarmSensorRepository,
    agentStatusRepository: AgentStatusRepository,
    pairingRepository: PairingRepository,
    onLogout: () -> Unit
) {
    val isCustomServerEnabled by authPreferences.isCustomServerEnabled.collectAsState(initial = false)
    var showServerDialog by remember { mutableStateOf(false) }

    val agentStatus by agentStatusRepository.getAgentStatus().collectAsState(initial = null)
    val sensors by sensorRepository.getSensors().collectAsState(initial = emptyList())
    val tilePositions by sensorRepository.getSensorTilePositions().collectAsState(initial = emptyList())
    val notifications by notificationRepository.getNotifications().collectAsState(initial = emptyList())
    val unreadCount = notifications.count { !it.isRead }

    var selectedSensor by remember { mutableStateOf<TempSensor?>(null) }
    var tempUnit by remember { mutableStateOf(TempUnit.CELSIUS) }
    var currentTab by remember { mutableIntStateOf(0) }
    var showResetLayoutConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Sensor detail: a ModalBottomSheet on phone, a small centered Dialog
    // on tablet (full-screen would waste most of a large tablet display).
    // Same 600dp breakpoint used elsewhere in the app (e.g.
    // AlarmSensorsScreen). skipPartiallyExpanded avoids the sheet opening
    // at a "peek" height needing a drag-up to see the rest.
    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val sensorDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Sync selected sensor with the latest data from the repository
    LaunchedEffect(sensors) {
        selectedSensor?.let { current ->
            selectedSensor = sensors.find { it.id == current.id }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(when(currentTab) {
                        0 -> "Sensors"
                        1 -> "Schedules"
                        2 -> "Relays"
                        3 -> "Alarm Sensors"
                        4 -> "Notifications"
                        else -> "Pairing"
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
                    // Reset Layout as a top-bar icon, not a text button
                    // taking up its own row in the body - only the Sensors
                    // and Alarms tabs have a freeform tile layout to reset.
                    if (currentTab == 0 || currentTab == 3) {
                        IconButton(onClick = { showResetLayoutConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Layout"
                            )
                        }
                    }
                    AgentStatusBadge(agentStatus)
                    IconButton(onClick = { showServerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Custom API Server Settings",
                            tint = if (isCustomServerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            // Sensor detail is always an overlay now (ModalBottomSheet on
            // phone, Dialog on tablet - see isTablet's doc comment), never
            // a full-screen replacement, so the bar never needs to hide.
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
                    icon = { Icon(Icons.Default.Warning, contentDescription = "Alarm Sensors") },
                    label = { Text("Alarms") }
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
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
                NavigationBarItem(
                    selected = currentTab == 5,
                    onClick = { currentTab = 5 },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Pairing") },
                    label = { Text("Pairing") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                0 -> {
                    // Dashboard grid: tiles sit in a responsive grid (more
                    // columns as the screen gets wider - see
                    // SensorDashboardCanvas) and can be dragged onto another
                    // tile to swap places with it, on phone and tablet
                    // alike. Full width on both now, unlike the old
                    // fixed-360dp tablet master pane, so a wide tablet
                    // actually shows more sensors at once instead of
                    // capping out at ~2 columns.
                    Column(modifier = Modifier.fillMaxSize()) {
                        // No separate in-body title or Reset Layout button
                        // here - the TopAppBar already reads "Smart Home"
                        // for this tab and now carries the reset action as
                        // an icon (see actions above), so this used to be
                        // two rows of near-duplicate chrome above the grid.
                        SensorDashboardCanvas(
                            sensors = sensors,
                            positions = tilePositions,
                            unit = tempUnit,
                            onSwap = { movedId, movedOrder, displacedId, displacedOrder ->
                                scope.launch {
                                    sensorRepository.swapSensorTilePositions(movedId, movedOrder, displacedId, displacedOrder)
                                }
                            },
                            onTileClick = { selectedSensor = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                1 -> SchedulesScreen(sensorRepository = sensorRepository)
                2 -> RelaysScreen(sensorRepository = sensorRepository)
                3 -> AlarmSensorsScreen(alarmSensorRepository = alarmSensorRepository)
                4 -> NotificationsScreen(notificationRepository = notificationRepository)
                5 -> PairingScreen(pairingRepository = pairingRepository)
            }
        }

        if (showServerDialog) {
            CustomApiServerDialog(
                authPreferences = authPreferences,
                onDismiss = { showServerDialog = false }
            )
        }

        if (showResetLayoutConfirm) {
            // Same dialog for both tabs the top-bar reset icon can appear
            // on (see actions above) - just the wording and which
            // repository gets cleared differ by which tab triggered it.
            val resettingAlarms = currentTab == 3
            AlertDialog(
                onDismissRequest = { showResetLayoutConfirm = false },
                title = { Text("Reset layout?") },
                text = {
                    Text(
                        "Every ${if (resettingAlarms) "alarm sensor" else "sensor"} tile goes back to the default grid arrangement. This can't be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showResetLayoutConfirm = false
                        scope.launch {
                            if (resettingAlarms) {
                                alarmSensorRepository.clearAlarmSensorTileLayout()
                            } else {
                                sensorRepository.clearSensorTileLayout()
                            }
                        }
                    }) { Text("Reset") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetLayoutConfirm = false }) { Text("Cancel") }
                }
            )
        }

        // Sensor detail: a Dialog on tablet (centered, sizes to content -
        // a ModalBottomSheet anchors to the bottom edge and stretches
        // full-width, which on a wide tablet put a small centered dial in
        // a large mostly-empty bar pinned to the bottom, read as "not the
        // whole window"). A ModalBottomSheet on phone instead, where that
        // full-width-bottom-anchored shape is exactly the familiar,
        // expected one (share sheets, action sheets, etc. all look like
        // this on phone) and doesn't have the tablet's empty-space problem.
        selectedSensor?.let { sensor ->
            val detailContent: @Composable () -> Unit = {
                Text(
                    text = sensor.name,
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(32.dp))

                ThermostatControl(
                    currentTemp = sensor.currentTemp,
                    setTemp = sensor.setTemp,
                    unit = tempUnit,
                    onSetTempChanged = { newTemp ->
                        scope.launch {
                            sensorRepository.updateSetTemp(sensor.id, newTemp)
                        }
                    }
                )
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
    }
}

/**
 * Small colored-dot indicator for whether the Pi's cloud uplink is
 * currently connected (com.smarthome.data.AgentStatusRepository). Renders
 * nothing at all while status is null - "unknown" (e.g. talking to the Pi
 * directly, where this is meaningless) is not the same as "offline" and
 * shouldn't be shown as if it were.
 */
@Composable
fun AgentStatusBadge(status: com.smarthome.data.AgentStatus?) {
    if (status == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (status.connected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        val label = if (status.connected) {
            "Home"
        } else {
            "Offline" + (status.lastSeen?.let { " (${elapsedLabel(it)})" } ?: "")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun elapsedLabel(epochMillis: Long): String {
    val elapsedMs = System.currentTimeMillis() - epochMillis
    val minutes = elapsedMs / 60000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
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
