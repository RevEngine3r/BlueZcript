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

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val parts = result.contents.split("|")
            if (parts.size == 2) {
                securityManager.savePairing(parts[0], parts[1])
                isPaired = true
                status = "Paired via QR"
            } else {
                Toast.makeText(this, "Invalid QR Format", Toast.LENGTH_SHORT).show()
            }
        }
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
                    val options = ScanOptions()
                    options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    options.setPrompt("Scan BlueZcript Pairing QR")
                    options.setBeepEnabled(false)
                    barcodeLauncher.launch(options)
                },
                onTrigger = {
                    if (::triggerService.isInitialized) {
                        triggerService.sendTrigger()
                        status = "Beacon Sent"
                    }
                }
            )
        }
    }
}

@Composable
fun MainScreen(status: String, isPaired: Boolean, onScanQR: () -> Unit, onTrigger: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "BlueZcript Secure", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Status: $status", color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(48.dp))

            if (!isPaired) {
                Button(onClick = onScanQR, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("SCAN QR CODE TO PAIR")
                }
            } else {
                Button(
                    onClick = onTrigger,
                    modifier = Modifier.size(200.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("TRIGGER")
                }
            }
        }
    }
}
