package r.iot.bluezcript

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID

class MainActivity : ComponentActivity() {

    // --- Configuration & State ---
    private val SERVICE_UUID = UUID.fromString("A0010001-0000-1000-8000-00805F9B34FB")
    private val CHAR_UUID = UUID.fromString("A0010002-0000-1000-8000-00805F9B34FB")

    private var bluetoothGatt: BluetoothGatt? = null
    private var bleStatus by mutableStateOf("Ready")
    private var isConnected by mutableStateOf(false)

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    // --- 1. Entry Point & Permission Handling ---
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) startTriggerFlow()
        else bleStatus = "Permissions Denied"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                status = bleStatus,
                isConnected = isConnected,
                onTrigger = { checkPermissionsAndStart() })
        }
    }

    private fun checkPermissionsAndStart() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    // --- 2. Orchestration ---
    private fun startTriggerFlow() {
        if (bluetoothAdapter?.isEnabled != true) {
            bleStatus = "Bluetooth is Off"
            return
        }
        scanForDevice()
    }

    // --- 3. Discovery Function ---
    @SuppressLint("MissingPermission")
    private fun scanForDevice() {
        bleStatus = "Scanning..."
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.name == "BlueZcript-Pi") {
                    scanner?.stopScan(this)
                    connectToDevice(result.device)
                }
            }
        }
        scanner?.startScan(scanCallback)
    }

    // --- 4. Connection Function ---
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        bleStatus = "Connecting..."
        bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false
                    releaseBleResources()
                }
            }

            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHAR_UUID)
                characteristic?.let { writeTriggerData(gatt, it) }
            }
        })
    }

    // --- 5. Data Transmission (Modern API 33+) ---
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    private fun writeTriggerData(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        val data = "1".toByteArray()
        // API 33+ standard: avoid char.setValue()
        gatt.writeCharacteristic(
            char, data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )

        bleStatus = "Trigger Sent!"

        // Auto-cleanup after transmission
        Handler(Looper.getMainLooper()).postDelayed({ releaseBleResources() }, 1000)
    }

    // --- 6. Resource Management ---
    @SuppressLint("MissingPermission")
    private fun releaseBleResources() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        if (bleStatus != "Trigger Sent!") bleStatus = "Disconnected"
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseBleResources()
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

