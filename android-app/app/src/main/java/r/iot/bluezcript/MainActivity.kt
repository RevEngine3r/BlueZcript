package r.iot.bluezcript

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import r.iot.bluezcript.ble.TriggerService
import r.iot.bluezcript.security.SecurityManager

class MainActivity : ComponentActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var triggerService: TriggerService
    private var status by mutableStateOf("Ready")
    private var isPaired by mutableStateOf(false)

    // Result launcher for QR Code Scanning
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val parts = result.contents.split("|")
            if (parts.size == 2) {
                securityManager.savePairing(parts[0], parts[1])
                isPaired = true
                status = "Successfully Paired"
            } else {
                status = "Invalid QR Code Format"
                Toast.makeText(this, "QR Format Error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] ?: false
        val bleGranted = perms[Manifest.permission.BLUETOOTH_ADVERTISE] ?: false
        
        if (!cameraGranted) status = "Camera Permission Required for QR"
        if (!bleGranted) status = "BLE Permissions Required"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        securityManager = SecurityManager(this)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser
        
        if (advertiser != null) {
            triggerService = TriggerService(advertiser, securityManager)
        }
        
        isPaired = securityManager.getPsk() != null

        setContent {
            MainScreen(
                status = status,
                isPaired = isPaired,
                onScanQR = { 
                    checkAndRequestPermissions()
                    startQRScanner()
                },
                onTrigger = {
                    if (::triggerService.isInitialized) {
                        triggerService.sendTrigger()
                        status = "Trigger Beacon Sent"
                    }
                },
                onReset = {
                    // Hidden feature to reset pairing for testing
                    getSharedPreferences("bluezcript_security", MODE_PRIVATE).edit().clear().apply()
                    isPaired = false
                    status = "Pairing Reset"
                }
            )
        }
    }

    private fun checkAndRequestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    }

    private fun startQRScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Align QR code from Web UI within the frame")
        options.setBeepEnabled(true)
        options.setOrientationLocked(false)
        barcodeLauncher.launch(options)
    }
}

@Composable
fun MainScreen(
    status: String, 
    isPaired: Boolean, 
    onScanQR: () -> Unit, 
    onTrigger: () -> Unit,
    onReset: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "BlueZcript Secure", style = MaterialTheme.typography.headlineLarge)
            Text(text = status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(64.dp))

            if (!isPaired) {
                Button(
                    onClick = onScanQR, 
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("SCAN QR CODE TO PAIR")
                }
            } else {
                Button(
                    onClick = onTrigger,
                    modifier = Modifier.size(220.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("SEND TRIGGER", style = MaterialTheme.typography.titleMedium)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                TextButton(onClick = onReset) {
                    Text("Reset Pairing Credentials", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
