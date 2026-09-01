package com.smarthome.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.smarthome.data.ble.*
import kotlinx.coroutines.launch

@Composable
fun BleProvisioningDialog(
    bleRepository: BleProvisioningRepository,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            bleRepository.reset()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BLE WiFi Setup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = {
                            bleRepository.reset()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                BleProvisioningContent(
                    bleRepository = bleRepository,
                    onFinish = {
                        bleRepository.reset()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun BleProvisioningContent(
    bleRepository: BleProvisioningRepository,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val scanResults by bleRepository.scanResults.collectAsState()
    val isScanning by bleRepository.isScanning.collectAsState()
    val connectionState by bleRepository.connectionState.collectAsState()
    val connectedDevice by bleRepository.connectedDevice.collectAsState()
    val provisionStatus by bleRepository.provisionStatus.collectAsState()
    val errorMessage by bleRepository.errorMessage.collectAsState()

    var hasRequiredPermissions by remember {
        mutableStateOf(checkBlePermissions(context))
    }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasRequiredPermissions = results.values.all { it }
        if (hasRequiredPermissions) {
            bleRepository.startScan()
        }
    }

    // Check if Bluetooth adapter is enabled
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    var isBluetoothEnabled by remember {
        mutableStateOf(bluetoothManager?.adapter?.isEnabled == true)
    }

    // Credentials form state
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasRequiredPermissions, isBluetoothEnabled) {
        if (hasRequiredPermissions && isBluetoothEnabled && connectionState == BleConnectionState.DISCONNECTED && !isScanning) {
            bleRepository.startScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bleRepository.stopScan()
        }
    }

    // hasRequiredPermissions/isBluetoothEnabled are otherwise only computed
    // once (remember{}) - without this, a user who taps "Open Bluetooth
    // Settings" or grants a permission outside permissionLauncher (e.g. via
    // system App Info), fixes it there, and returns to the app would be
    // stuck on the "disabled"/"permissions required" screen forever, since
    // nothing re-checks either flag until the dialog is fully closed and
    // reopened. Re-check both whenever this screen resumes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasRequiredPermissions = checkBlePermissions(context)
                isBluetoothEnabled = bluetoothManager?.adapter?.isEnabled == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (!hasRequiredPermissions) {
            // Permissions prompt
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Bluetooth Permissions Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To scan and connect to your SmartHome device over Bluetooth Low Energy, please grant Bluetooth permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { permissionLauncher.launch(requiredPermissions) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }
        } else if (!isBluetoothEnabled) {
            // Bluetooth disabled prompt
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Bluetooth is Disabled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please turn on Bluetooth to discover and provision nearby SmartHome devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Bluetooth Settings")
                        }
                    }
                }
            }
        } else if (connectionState == BleConnectionState.DISCONNECTED || connectionState == BleConnectionState.SCANNING) {
            // Device Scanning & Discovery List
            Column(modifier = Modifier.fillMaxSize()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isScanning) "Scanning for Devices..." else "Scan Paused",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Looking for 'pihome*-Setup' or SmartHome BLE peripherals",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { bleRepository.startScan() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                            }
                        }
                    }
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (scanResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Make sure your Raspberry Pi is powered on and running the provisioning service.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            if (!isScanning) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { bleRepository.startScan() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Again")
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Found Peripherals (${scanResults.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scanResults, key = { it.address }) { dev ->
                            DiscoveredBleDeviceCard(
                                device = dev,
                                onConnect = { bleRepository.connect(dev) }
                            )
                        }
                    }
                }
            }
        } else if (connectionState == BleConnectionState.CONNECTING || connectionState == BleConnectionState.DISCOVERING_SERVICES) {
            // Connecting Spinner
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Connecting to ${connectedDevice?.name ?: "Device"}...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Establishing secure Just Works LE connection & discovering GATT services",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = { bleRepository.disconnect() }) {
                        Text("Cancel")
                    }
                }
            }
        } else {
            // Connected: WiFi Credentials Form & Live Provisioning Status
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
            ) {
                // Connected Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = connectedDevice?.name ?: "Connected Device",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = connectedDevice?.address ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = { bleRepository.disconnect() },
                            enabled = provisionStatus?.state != "connecting"
                        ) {
                            Text("Disconnect")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Provisioning Status Card (if in progress or completed)
                provisionStatus?.let { status ->
                    ProvisionStatusCard(
                        status = status,
                        onRetry = {
                            isSending = false
                            localError = null
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // If not successfully connected yet, show credentials inputs
                if (provisionStatus?.state != "connected") {
                    Text(
                        text = "Target WiFi Network",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { 
                            if (it.toByteArray(Charsets.UTF_8).size <= 32) {
                                ssid = it
                                localError = null
                            }
                        },
                        label = { Text("WiFi Network Name (SSID)") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = provisionStatus?.state != "connecting" && !isSending,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            if (it.toByteArray(Charsets.UTF_8).size <= 63) {
                                password = it
                                localError = null
                            }
                        },
                        label = { Text("WiFi Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Check else Icons.Default.Lock,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = provisionStatus?.state != "connecting" && !isSending,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )

                    localError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (ssid.isBlank()) {
                                localError = "SSID cannot be empty"
                                return@Button
                            }
                            scope.launch {
                                isSending = true
                                localError = null
                                val result = bleRepository.sendCredentials(ssid.trim(), password)
                                isSending = false
                                if (result.isFailure) {
                                    localError = result.exceptionOrNull()?.message ?: "Failed to transmit credentials"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ssid.isNotBlank() && provisionStatus?.state != "connecting" && !isSending
                    ) {
                        if (isSending || provisionStatus?.state == "connecting") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configuring Pi...")
                        } else {
                            Text("Apply WiFi Credentials")
                        }
                    }
                } else {
                    // Success State
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredBleDeviceCard(
    device: DiscoveredBleDevice,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${device.address}  •  ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun ProvisionStatusCard(
    status: BleProvisionStatus,
    onRetry: () -> Unit
) {
    val isSuccess = status.state == "connected"
    val isConnecting = status.state == "connecting"
    val isFailed = status.state == "failed"

    val containerColor = when {
        isSuccess -> Color(0xFFE8F5E9)
        isFailed -> Color(0xFFFFEBEE)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }

    val contentColor = when {
        isSuccess -> Color(0xFF2E7D32)
        isFailed -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = contentColor
                    )
                }
                isSuccess -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                isFailed -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isConnecting -> "Connecting to WiFi..."
                        isSuccess -> "WiFi Connected!"
                        isFailed -> "Connection Failed"
                        else -> "Status: ${status.state}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                if (isSuccess && !status.ip.isNullOrBlank()) {
                    Text(
                        text = "Assigned IP: ${status.ip}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }

                if (isFailed && !status.detail.isNullOrBlank()) {
                    Text(
                        text = status.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                } else if (isConnecting) {
                    Text(
                        text = "Waiting for Raspberry Pi to acquire IP address...",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }

                if (isFailed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                        Text("Try Again", color = contentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun checkBlePermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
