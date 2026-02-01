package com.revengine3r.bluezcript.security

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecurityManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bluezcript_security", Context.MODE_PRIVATE)

    fun savePairing(deviceId: String, psk: String) {
        prefs.edit().putString("psk_$deviceId", psk).apply()
        prefs.edit().putLong("nonce_$deviceId", 0).apply()
    }

    fun getPsk(deviceId: String): String? = prefs.getString("psk_$deviceId", null)

    private fun getNextNonce(deviceId: String): Long {
        val current = prefs.getLong("nonce_$deviceId", 0)
        val next = current + 1
        prefs.edit().putLong("nonce_$deviceId", next).apply()
        return next
    }

    fun generateAuthenticatedPayload(deviceId: String): ByteArray? {
        val pskHex = getPsk(deviceId) ?: return null
        val psk = pskHex.decodeHex()
        val nonce = getNextNonce(deviceId)
        
        // Protocol: [Type (1 byte) + Nonce (4 bytes)]
        val type = 0x01.toByte()
        val payload = ByteArray(5)
        payload[0] = type
        payload[1] = (nonce shr 24).toByte()
        payload[2] = (nonce shr 16).toByte()
        payload[3] = (nonce shr 8).toByte()
        payload[4] = nonce.toByte()

        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(psk, "HmacSHA256"))
        val fullSig = hmac.doFinal(payload)
        
        // Truncate to 8 bytes for BLE
        val sig = fullSig.copyOfRange(0, 8)
        
        // Final Manufacturer Data: [Type(1) + Nonce(4) + Sig(8)] = 13 bytes
        // (Company ID prepended by BLE advertiser)
        return payload + sig
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
