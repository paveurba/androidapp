package com.smarthome.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

class ProductionBleProvisioningRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : BleProvisioningRepository {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("c1060000-0000-1000-8000-00805f9b34fb")
        val CHAR_SSID_UUID: UUID = UUID.fromString("c1060001-0000-1000-8000-00805f9b34fb")
        val CHAR_PASSWORD_UUID: UUID = UUID.fromString("c1060002-0000-1000-8000-00805f9b34fb")
        val CHAR_CONNECT_UUID: UUID = UUID.fromString("c1060003-0000-1000-8000-00805f9b34fb")
        val CHAR_STATUS_UUID: UUID = UUID.fromString("c1060004-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _scanResults = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    override val scanResults: StateFlow<List<DiscoveredBleDevice>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<DiscoveredBleDevice?>(null)
    override val connectedDevice: StateFlow<DiscoveredBleDevice?> = _connectedDevice.asStateFlow()

    private val _provisionStatus = MutableStateFlow<BleProvisionStatus?>(null)
    override val provisionStatus: StateFlow<BleProvisionStatus?> = _provisionStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var scanJob: Job? = null
    private val gattOperationMutex = Mutex()
    private var pendingWriteDeferred: CompletableDeferred<Int>? = null
    private var pendingDescriptorDeferred: CompletableDeferred<Int>? = null

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            handleScanResult(result)
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e("BleProvision", "BLE scan failed with error code: $errorCode")
            _isScanning.value = false
            _errorMessage.value = "BLE scan failed (error code $errorCode)"
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val rawName = try {
            result.scanRecord?.deviceName ?: device.name
        } catch (e: SecurityException) {
            null
        }

        val name = rawName?.takeIf { it.isNotBlank() } ?: "Unnamed device"
        val serviceUuids = result.scanRecord?.serviceUuids ?: emptyList()
        val hasTargetService = serviceUuids.any { it.uuid == SERVICE_UUID }
        val matchesName = name.contains("Setup", ignoreCase = true) || 
                          name.contains("pihome", ignoreCase = true) ||
                          name.contains("SmartHome", ignoreCase = true)

        // Filter for our provisioning devices or devices matching the target UUID
        if (!hasTargetService && !matchesName && !name.startsWith("pi", ignoreCase = true)) {
            return
        }

        val discovered = DiscoveredBleDevice(
            name = name,
            address = device.address,
            rssi = result.rssi,
            device = device
        )

        val currentList = _scanResults.value.toMutableList()
        val index = currentList.indexOfFirst { it.address == discovered.address }
        if (index != -1) {
            currentList[index] = discovered
        } else {
            currentList.add(discovered)
        }
        _scanResults.value = currentList.sortedByDescending { it.rssi }
    }

    @SuppressLint("MissingPermission")
    override fun startScan() {
        if (!hasScanPermission()) {
            _errorMessage.value = "Bluetooth scan permission is required"
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _errorMessage.value = "Bluetooth is disabled. Please enable Bluetooth."
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _errorMessage.value = "BLE scanner unavailable"
            return
        }

        _errorMessage.value = null
        _scanResults.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        try {
            // Scan with and without filter to ensure quick discovery even if advertisement data is split
            scanner.startScan(listOf(filter), settings, scanCallback)
            android.util.Log.d("BleProvision", "Started BLE scanning")
        } catch (e: Exception) {
            try {
                // Fallback to open scan
                scanner.startScan(null, settings, scanCallback)
            } catch (e2: Exception) {
                _isScanning.value = false
                _errorMessage.value = e2.message ?: "Failed to start BLE scan"
                return
            }
        }

        // Auto stop scan after 15 seconds
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(15000)
            if (_isScanning.value) {
                stopScan()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        if (!_isScanning.value) return

        try {
            if (hasScanPermission()) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            android.util.Log.w("BleProvision", "Error stopping BLE scan: ${e.message}")
        }
        _isScanning.value = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.d("BleProvision", "GATT connection state change: status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = BleConnectionState.CONNECTED
                _connectionState.value = BleConnectionState.DISCOVERING_SERVICES
                scope.launch {
                    delay(300) // Brief delay before service discovery for BT stability
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = BleConnectionState.DISCONNECTED
                _connectedDevice.value = null
                activeGatt?.close()
                activeGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            android.util.Log.d("BleProvision", "Services discovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val statusChar = service.getCharacteristic(CHAR_STATUS_UUID)
                    if (statusChar != null) {
                        enableStatusNotification(gatt, statusChar)
                    }
                    _connectionState.value = BleConnectionState.READY
                    android.util.Log.i("BleProvision", "GATT peripheral ready for provisioning")
                } else {
                    _errorMessage.value = "Provisioning service not found on device"
                    // Actually tear down the connection instead of just flipping app state
                    // to DISCONNECTED - onConnectionStateChange (below) does the real
                    // activeGatt = null / gatt.close(), so calling disconnect() here (rather
                    // than duplicating that cleanup) keeps the OS-level GATT connection from
                    // being silently held open while the UI claims disconnected - BLE stacks
                    // typically cap simultaneous GATT connections in the single digits, so a
                    // leaked one here would eventually block connecting to anything else.
                    disconnect()
                }
            } else {
                _errorMessage.value = "Failed to discover services (status $status)"
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            android.util.Log.d("BleProvision", "Characteristic write callback: uuid=${characteristic?.uuid}, status=$status")
            pendingWriteDeferred?.complete(status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            android.util.Log.d("BleProvision", "Descriptor write callback: uuid=${descriptor?.uuid}, status=$status")
            pendingDescriptorDeferred?.complete(status)
            // After enabling notifications, read the initial status characteristic
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor?.characteristic?.uuid == CHAR_STATUS_UUID) {
                try {
                    @SuppressLint("MissingPermission")
                    descriptor.characteristic?.let { gatt?.readCharacteristic(it) }
                } catch (e: Exception) {
                    android.util.Log.w("BleProvision", "Reading initial status characteristic failed: ${e.message}")
                }
            }
        }

        // For Android 13+ (API 33+)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleStatusPayload(characteristic.uuid, value)
        }

        // Deprecated callback for Android < 13
        @Deprecated("Used for Android < 13 compatibility")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic != null) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                handleStatusPayload(characteristic.uuid, value)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleStatusPayload(characteristic.uuid, value)
            }
        }

        @Deprecated("Used for Android < 13 compatibility")
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                handleStatusPayload(characteristic.uuid, value)
            }
        }
    }

    private fun handleStatusPayload(uuid: UUID, value: ByteArray) {
        if (uuid != CHAR_STATUS_UUID) return
        val jsonString = String(value, Charsets.UTF_8)
        android.util.Log.i("BleProvision", "Received Status payload: $jsonString")

        try {
            val json = JSONObject(jsonString)
            val state = json.optString("state", "idle")
            val ssid = json.optString("ssid").takeIf { it.isNotBlank() }
            val ip = json.optString("ip").takeIf { it.isNotBlank() }
            val detail = json.optString("detail").takeIf { it.isNotBlank() }

            _provisionStatus.value = BleProvisionStatus(
                state = state,
                ssid = ssid,
                ip = ip,
                detail = detail
            )
        } catch (e: Exception) {
            android.util.Log.e("BleProvision", "Failed to parse Status JSON payload: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableStatusNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return

        scope.launch {
            gattOperationMutex.withLock {
                pendingDescriptorDeferred = CompletableDeferred()
                val writeInitiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }

                if (writeInitiated) {
                    try {
                        withTimeout(3000) {
                            pendingDescriptorDeferred?.await()
                        }
                    } catch (e: TimeoutCancellationException) {
                        android.util.Log.w("BleProvision", "Timeout waiting for CCCD descriptor write")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: DiscoveredBleDevice) {
        if (!hasConnectPermission()) {
            _errorMessage.value = "Bluetooth connect permission is required"
            return
        }

        stopScan()
        disconnect()

        _errorMessage.value = null
        _connectedDevice.value = device
        _connectionState.value = BleConnectionState.CONNECTING
        _provisionStatus.value = null

        try {
            activeGatt = device.device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (e: Exception) {
            _connectionState.value = BleConnectionState.DISCONNECTED
            _connectedDevice.value = null
            _errorMessage.value = e.message ?: "Failed to connect to BLE device"
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (e: Exception) {
            android.util.Log.w("BleProvision", "Error closing GATT: ${e.message}")
        }
        activeGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        _connectedDevice.value = null
    }

    override suspend fun sendCredentials(ssid: String, password: String): Result<Unit> {
        if (ssid.isBlank()) {
            return Result.failure(IllegalArgumentException("WiFi SSID cannot be empty"))
        }
        if (ssid.toByteArray(Charsets.UTF_8).size > 32) {
            return Result.failure(IllegalArgumentException("WiFi SSID exceeds 32 bytes"))
        }
        if (password.toByteArray(Charsets.UTF_8).size > 63) {
            return Result.failure(IllegalArgumentException("WiFi Password exceeds 63 bytes"))
        }

        val gatt = activeGatt ?: return Result.failure(IllegalStateException("No active BLE connection"))
        val service = gatt.getService(SERVICE_UUID) ?: return Result.failure(IllegalStateException("Provisioning service not found"))
        val ssidChar = service.getCharacteristic(CHAR_SSID_UUID) ?: return Result.failure(IllegalStateException("SSID characteristic not found"))
        val passChar = service.getCharacteristic(CHAR_PASSWORD_UUID) ?: return Result.failure(IllegalStateException("Password characteristic not found"))
        val connChar = service.getCharacteristic(CHAR_CONNECT_UUID) ?: return Result.failure(IllegalStateException("Connect characteristic not found"))

        return try {
            // 1. Write SSID
            writeCharacteristicWithLock(gatt, ssidChar, ssid.toByteArray(Charsets.UTF_8))

            // 2. Write Password
            writeCharacteristicWithLock(gatt, passChar, password.toByteArray(Charsets.UTF_8))

            // 3. Write Connect trigger
            writeCharacteristicWithLock(gatt, connChar, byteArrayOf(1))

            _provisionStatus.value = BleProvisionStatus(state = "connecting", ssid = ssid)
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("BleProvision", "Failed to send credentials: ${e.message}", e)
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCharacteristicWithLock(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        gattOperationMutex.withLock {
            pendingWriteDeferred = CompletableDeferred()
            val writeSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }

            if (!writeSuccess) {
                throw IllegalStateException("GATT write failed to initiate for ${characteristic.uuid}")
            }

            val status = withTimeout(5000) {
                pendingWriteDeferred?.await() ?: BluetoothGatt.GATT_FAILURE
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                throw IllegalStateException("GATT write failed with status $status for ${characteristic.uuid}")
            }
        }
    }

    override fun reset() {
        stopScan()
        disconnect()
        _scanResults.value = emptyList()
        _provisionStatus.value = null
        _errorMessage.value = null
    }

    override fun clearStatus() {
        _provisionStatus.value = null
    }
}
