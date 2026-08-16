package com.smarthome.data

import kotlinx.coroutines.flow.Flow

// Mirrors model.PairingState from the shared Go core - sourced from
// Zigbee2mqtt's own confirmed bridge/info permit_join/permit_join_end on
// the agent side, not a locally-tracked timer, so this always reflects
// real radio state (see the backend's doc comment on
// GET /api/pairing/status).
data class PairingStatus(
    val active: Boolean,
    val remainingSeconds: Int
)

// A Zigbee device that joined the network while pairing was open, not yet
// confirmed into a real sensor/actuator - mirrors model.DiscoveredDevice.
// kind is "" for an actuator (isActuator true) or a device whose exposes
// couldn't be classified - PairingScreen shows a generic label either way,
// it's still confirmable as long as isActuator is set (an unclassified
// non-actuator, kind == "" and isActuator == false, is the one case
// confirmDevice's server side rejects - see api.confirmDevice).
data class DiscoveredDevice(
    val id: String,
    val kind: String,
    val topic: String,
    val model: String = "",
    val manufacturer: String = "",
    val isActuator: Boolean = false,
    val discoveredAt: Long = 0L
)

/**
 * Backed by the pairing endpoints under /api/pairing, part of the shared
 * REST layer both the Pi agent and the cloud server mount - works
 * identically whether the app is talking to the Pi directly or through
 * upanet.org, same as every other repository here.
 */
interface PairingRepository {
    fun getPairingStatus(): Flow<PairingStatus>
    fun getDiscoveredDevices(): Flow<List<DiscoveredDevice>>

    // timeoutSeconds defaults to the server's own default (120s, capped at
    // 254) if not specified.
    suspend fun startPairing(timeoutSeconds: Int? = null)
    suspend fun stopPairing()

    // Renames device in Zigbee2mqtt to name and turns it into a real
    // sensor (or, isActuator, a devices-relay switch) - see
    // api.confirmDevice's doc comment. Throws with a server-supplied
    // message on failure (duplicate name, actuator pairing on a kind that
    // isn't supported, etc.) so the caller can surface it in a snackbar,
    // same convention as SensorRepository.setScheduleEnabled.
    suspend fun confirmDevice(deviceId: String, name: String)
}
