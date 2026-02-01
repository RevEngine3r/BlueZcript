package com.bluezcript.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.*

class MainActivity : ComponentActivity() {

    private val SERVICE_UUID = UUID.fromString("A0010001-0000-1000-8000-00805F9B34FB")
    private val CHAR_UUID = UUID.fromString("A0010002-0000-1000-8000-00805F9B34FB")

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = mutableStateOf(false)
    private var statusMessage = mutableStateOf("Ready")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                statusMessage.value = "Permissions Granted"
            } else {
                statusMessage.value = "Permissions Denied"
            }
        }

        setContent {
            BlueZcriptTheme {
                MainScreen(
                    status = statusMessage.value,
                    isConnected = isConnected.value,
                    onTrigger = { startScanAndTrigger() }
                )
            }
        }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun startScanAndTrigger() {
        statusMessage.value = "Scanning for Pi..."
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        scanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.name == "BlueZcript-Pi") {
                    scanner.stopScan(this)
                    connectToDevice(result.device)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        statusMessage.value = "Connecting..."
        bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected.value = true
                    statusMessage.value = "Connected. Discovering Services..."
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected.value = false
                    statusMessage.value = "Disconnected"
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(CHAR_UUID)
                    if (characteristic != null) {
                        statusMessage.value = "Ready to Trigger"
                        sendTrigger(gatt, characteristic)
                    }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun sendTrigger(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        characteristic.value = "1".toByteArray()
        gatt.writeCharacteristic(characteristic)
        statusMessage.value = "Trigger Sent!"
        // Cleanup after delay
        Timer().schedule(object : TimerTask() {
            override fun run() { gatt.disconnect() }
        }, 1000)
    }
}

@Composable
fun MainScreen(status: String, isConnected: Boolean, onTrigger: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "BlueZcript Controller", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Status: $status")
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onTrigger,
                modifier = Modifier.size(200.dp, 60.dp)
            ) {
                Text(text = if (isConnected) "SEND TRIGGER" else "SCAN & TRIGGER")
            }
        }
    }
}

@Composable
fun BlueZcriptTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
