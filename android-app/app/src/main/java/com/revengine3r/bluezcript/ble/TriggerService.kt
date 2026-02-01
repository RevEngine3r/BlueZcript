package com.revengine3r.bluezcript.ble

import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.util.Log
import com.revengine3r.bluezcript.security.SecurityManager
import java.nio.ByteBuffer

class TriggerService(
    private val advertiser: BluetoothLeAdvertiser,
    private val securityManager: SecurityManager
) {
    companion object {
        const val COMPANY_ID = 0xFFFF
    }

    fun sendTrigger(deviceId: String) {
        val authPayload = securityManager.generateAuthenticatedPayload(deviceId)
        if (authPayload == null) {
            Log.e("TriggerService", "No PSK found for device $deviceId. Please pair first.")
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
        Log.i("TriggerService", "Authenticated trigger sent for $deviceId")
    }
}
