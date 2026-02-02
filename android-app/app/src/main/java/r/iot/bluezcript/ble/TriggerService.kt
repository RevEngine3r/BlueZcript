package r.iot.bluezcript.ble

import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.util.Log
import r.iot.bluezcript.security.SecurityManager

class TriggerService(
    private val advertiser: BluetoothLeAdvertiser,
    private val securityManager: SecurityManager
) {
    companion object {
        const val COMPANY_ID = 0xFFFF
    }

    fun sendTrigger() {
        val authPayload = securityManager.generateAuthenticatedPayload()
        if (authPayload == null) {
            Log.e("TriggerService", "Please pair first.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(1000)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(COMPANY_ID, authPayload)
            .build()

        advertiser.startAdvertising(settings, data, null)
    }
}
