package com.smarthome.data.ble

import kotlinx.coroutines.flow.StateFlow

/**
 * Manages scanning, GATT connection, characteristic writes, and status notifications
 * for the BLE WiFi Provisioning peripheral on the Raspberry Pi.
 */
interface BleProvisioningRepository {
    val scanResults: StateFlow<List<DiscoveredBleDevice>>
    val isScanning: StateFlow<Boolean>
    val connectionState: StateFlow<BleConnectionState>
    val connectedDevice: StateFlow<DiscoveredBleDevice?>
    val provisionStatus: StateFlow<BleProvisionStatus?>
    val errorMessage: StateFlow<String?>

    fun startScan()
    fun stopScan()
    fun connect(device: DiscoveredBleDevice)
    fun disconnect()
    suspend fun sendCredentials(ssid: String, password: String): Result<Unit>
    fun reset()
}
