package com.smarthome.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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

@Composable
fun RelaysScreen(
    sensorRepository: SensorRepository
) {
    val relays by sensorRepository.getRelays().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Relay Controllers",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            if (isTablet) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
            Text(
                text = relay.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
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
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Switch(
                checked = relaySwitch.isOn,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}
