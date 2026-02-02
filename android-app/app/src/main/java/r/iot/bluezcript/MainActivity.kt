package r.iot.bluezcript

import android.Manifest
import android.bluetooth.BluetoothManager
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
import r.iot.bluezcript.ble.TriggerService
import r.iot.bluezcript.security.SecurityManager

class MainActivity : ComponentActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var triggerService: TriggerService
    private var status by mutableStateOf("Ready")
    private var isPaired by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (!perms.values.all { it }) status = "Permissions Denied"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        securityManager = SecurityManager(this)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        
        // Fix: Retrieve the Advertiser instead of the Scanner
        val advertiser = adapter?.bluetoothLeAdvertiser
        
        if (advertiser != null) {
            triggerService = TriggerService(advertiser, securityManager)
        } else {
            status = "BLE Advertising not supported"
        }
        
        isPaired = securityManager.getPsk() != null

        setContent {
            MainScreen(
                status = status,
                isPaired = isPaired,
                onPair = { id, psk ->
                    securityManager.savePairing(id, psk)
                    isPaired = true
                    status = "Device Paired"
                },
                onTrigger = {
                    if (::triggerService.isInitialized) {
                        triggerService.sendTrigger()
                        status = "Beacon Sent"
                    } else {
                        status = "Error: Service Not Ready"
                    }
                },
                onRequestPermissions = { checkPermissions() }
            )
        }
    }

    private fun checkPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    }
}

@Composable
fun MainScreen(
    status: String,
    isPaired: Boolean,
    onPair: (String, String) -> Unit,
    onTrigger: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    var deviceId by remember { mutableStateOf("") }
    var psk by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "BlueZcript Secure", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Status: $status", color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(32.dp))

            if (!isPaired) {
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("Device ID") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = psk,
                    onValueChange = { psk = it },
                    label = { Text("Pre-Shared Key (Hex)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { if (deviceId.isNotBlank() && psk.isNotBlank()) onPair(deviceId, psk) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE PAIRING")
                }
            } else {
                Button(
                    onClick = onTrigger,
                    modifier = Modifier.size(200.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("TRIGGER")
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onRequestPermissions) {
                    Text("Refresh Permissions")
                }
            }
        }
    }
}
