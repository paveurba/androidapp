package com.smarthome.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.smarthome.data.Relay
import com.smarthome.data.RelaySwitch
import com.smarthome.data.SensorRepository
import kotlinx.coroutines.launch

// Shared by RelaysScreen's SwitchItem and SchedulesScreen's ScheduleCard so
// every on/off toggle in the app is the same (smaller-than-default) size.
internal const val SWITCH_SCALE = 0.8f

@Composable
fun RelaysScreen(
    sensorRepository: SensorRepository
) {
    val relays by sensorRepository.getRelays().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // No separate in-body title here - the TopAppBar
            // (DashboardScreen) already reads "Relays" for this tab.
            if (isTablet) {
                // Staggered (masonry), not a row-aligned LazyVerticalGrid -
                // relay cards vary a lot in height (a group with 1 switch
                // vs. 3), and a row-aligned grid leaves visible gaps under
                // the shorter cards in a row instead of letting the next
                // card start right where the previous one in that column
                // ended. That mismatch is exactly what looked "chaotic" on
                // a wide tablet with only a handful of relay groups to fill
                // the row.
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 16.dp
                ) {
                    items(relays, key = { it.id }) { relay ->
                        RelayCard(
                            relay = relay,
                            onSwitchToggle = { switchId ->
                                scope.launch {
                                    sensorRepository.toggleRelaySwitch(relay.id, switchId)
                                }
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(relays, key = { it.id }) { relay ->
                        RelayCard(
                            relay = relay,
                            onSwitchToggle = { switchId ->
                                scope.launch {
                                    sensorRepository.toggleRelaySwitch(relay.id, switchId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RelayCard(
    relay: Relay,
    onSwitchToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Plain default text color, not tinted primary - matches
            // ScheduleCard's device title (SchedulesScreen.kt) so a title
            // reads the same regardless of which tab it's on.
            Text(
                text = relay.name,
                style = MaterialTheme.typography.titleMedium
            )

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
                                onToggle = { onSwitchToggle(relaySwitch.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowSwitches.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SwitchItem(
    relaySwitch: RelaySwitch,
    displayInverted: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // relaySwitch.isOn is always the raw backend value (what the agent's
    // hardware feedback reports, and what a toggle flips) - displayInverted
    // only affects what's shown here, never what onToggle does. See
    // Relay.displayInverted's doc comment.
    val displayIsOn = relaySwitch.isOn xor displayInverted

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = relaySwitch.label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            // Scaled down to match ScheduleCard's Switch (SchedulesScreen.kt)
            // - same SWITCH_SCALE constant, so toggles are the same
            // (smaller) size everywhere, not just consistent with each
            // other but also less space-hungry in a list of many.
            Switch(
                checked = displayIsOn,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(SWITCH_SCALE)
            )
        }
    }
}
