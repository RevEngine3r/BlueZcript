package r.iot.bluezcript

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import r.iot.bluezcript.ble.TriggerService
import r.iot.bluezcript.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var triggerService: TriggerService
    private lateinit var bluetoothManager: BluetoothManager
    private var status by mutableStateOf("Ready")
    private var isPaired by mutableStateOf(false)
    private var hasBluetoothPermissions by mutableStateOf(false)
    private var detectedMacAddress by mutableStateOf<String?>(null)

    // Result launcher for QR Code Scanning
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val parts = result.contents.split("|")
            if (parts.size == 3) {
                // New format: server_url|device_id|psk
                val serverUrl = parts[0]
                val deviceId = parts[1]
                val psk = parts[2]
                
                // Get our Bluetooth MAC address and register with server
                val macAddress = getBluetoothMacAddress()
                if (macAddress != null) {
                    detectedMacAddress = macAddress
                    status = "MAC: $macAddress - Registering..."
                    registerDeviceWithServer(serverUrl, deviceId, macAddress, psk)
                } else {
                    status = "Could not read Bluetooth MAC address"
                    Toast.makeText(this, "Bluetooth MAC address unavailable. Check in Settings > About Phone > Bluetooth Address and manually pair in Web UI.", Toast.LENGTH_LONG).show()
                }
            } else if (parts.size == 2) {
                // Legacy format: device_id|psk (save directly)
                // Assume device_id is the MAC address
                securityManager.savePairing(parts[0], parts[1])
                isPaired = true
                status = "Successfully Paired (Legacy)"
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
        val bleAdvertiseGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms[Manifest.permission.BLUETOOTH_ADVERTISE] ?: false
        } else {
            true
        }
        val bleConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        } else {
            true
        }
        
        hasBluetoothPermissions = bleAdvertiseGranted && bleConnectGranted
        
        if (!cameraGranted) {
            status = "Camera Permission Required for QR"
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
        }
        if (!hasBluetoothPermissions) {
            status = "BLE Permissions Required"
            Toast.makeText(this, "Bluetooth permissions are required to send triggers", Toast.LENGTH_LONG).show()
        } else {
            status = "Permissions Granted - Ready"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        securityManager = SecurityManager(this)
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser
        
        if (advertiser != null) {
            triggerService = TriggerService(advertiser, securityManager)
        }
        
        isPaired = securityManager.getPsk() != null
        
        // Try to get MAC address on startup
        val mac = getBluetoothMacAddress()
        if (mac != null) {
            detectedMacAddress = mac
        }

        // Check if permissions are already granted
        checkPermissionsStatus()
        
        // Request permissions on startup
        if (!hasBluetoothPermissions) {
            requestPermissions()
        }

        setContent {
            MainScreen(
                status = status,
                isPaired = isPaired,
                hasBluetoothPermissions = hasBluetoothPermissions,
                detectedMac = detectedMacAddress,
                onScanQR = { 
                    if (!hasBluetoothPermissions) {
                        requestPermissions()
                    } else {
                        startQRScanner()
                    }
                },
                onTrigger = {
                    if (!hasBluetoothPermissions) {
                        status = "Bluetooth permissions required"
                        Toast.makeText(this, "Please grant Bluetooth permissions", Toast.LENGTH_LONG).show()
                        requestPermissions()
                    } else if (::triggerService.isInitialized) {
                        triggerService.sendTrigger()
                        status = "Trigger Beacon Sent"
                    }
                },
                onReset = {
                    getSharedPreferences("bluezcript_security", MODE_PRIVATE).edit().clear().apply()
                    isPaired = false
                    status = "Pairing Reset"
                }
            )
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getBluetoothMacAddress(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return null
                }
            }
            val address = bluetoothManager.adapter?.address
            // Note: On Android 6+ this may return 02:00:00:00:00:00 for privacy
            if (address != null && address != "02:00:00:00:00:00") {
                address.replace(":", "").lowercase()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting MAC address", e)
            null
        }
    }

    private fun registerDeviceWithServer(serverUrl: String, deviceId: String, macAddress: String, psk: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$serverUrl/register-device")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                
                val postData = "device_id=${URLEncoder.encode(deviceId, "UTF-8")}" +
                        "&mac_address=${URLEncoder.encode(macAddress, "UTF-8")}" +
                        "&psk=${URLEncoder.encode(psk, "UTF-8")}"
                
                connection.outputStream.write(postData.toByteArray())
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    // Save pairing locally with MAC as device_id
                    securityManager.savePairing(macAddress, psk)
                    runOnUiThread {
                        isPaired = true
                        status = "Paired! MAC: $macAddress"
                        Toast.makeText(this@MainActivity, "Device registered. If triggers don't work, check listener logs for actual MAC.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    runOnUiThread {
                        status = "Registration failed"
                        Toast.makeText(this@MainActivity, "Failed to register with server", Toast.LENGTH_SHORT).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error registering device", e)
                runOnUiThread {
                    status = "Registration error: ${e.message}"
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkPermissionsStatus() {
        hasBluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        
        permissionLauncher.launch(permissions.toTypedArray())
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
    hasBluetoothPermissions: Boolean,
    detectedMac: String?,
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
            
            if (detectedMac != null && !isPaired) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Detected MAC: $detectedMac",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "⚠ May differ from actual BLE MAC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasBluetoothPermissions) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.secondary
                    ),
                    enabled = hasBluetoothPermissions
                ) {
                    Text("SEND TRIGGER", style = MaterialTheme.typography.titleMedium)
                }
                
                if (!hasBluetoothPermissions) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Grant Bluetooth permissions to enable trigger",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                TextButton(onClick = onReset) {
                    Text("Reset Pairing Credentials", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
