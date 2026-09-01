package com.smarthome.data.ble

import android.bluetooth.BluetoothDevice

/**
 * Represents a nearby Bluetooth Low Energy peripheral discovered during scanning.
 */
data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice
)

/**
 * Status payload received from the peripheral's Status characteristic (c1060004-...).
 * State values: "idle", "connecting", "connected", "failed".
 */
data class BleProvisionStatus(
    val state: String,
    val ssid: String? = null,
    val ip: String? = null,
    val detail: String? = null
)

/**
 * Connection states of the BLE GATT client session.
 */
enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    READY,
    DISCONNECTING
}
