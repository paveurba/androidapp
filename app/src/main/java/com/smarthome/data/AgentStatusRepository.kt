package com.smarthome.data

import kotlinx.coroutines.flow.Flow

/**
 * Whether the Pi agent currently has a live connection to the cloud server -
 * cloud-only (see ApiService.getAgentStatus's doc comment): meaningless and
 * 404s when the app is talking to the Pi directly, since a device is
 * trivially "connected to itself". null means "unknown" (no successful
 * response yet, e.g. because the app is on the local network, not because
 * the Pi is actually offline) - a client should only render a status badge
 * once it has a real answer, not show "offline" for what's really just
 * "not applicable right now".
 */
data class AgentStatus(
    val connected: Boolean,
    val lastSeen: Long? // epoch millis, only meaningful when connected == false
)

interface AgentStatusRepository {
    fun getAgentStatus(): Flow<AgentStatus?>
}
