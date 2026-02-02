package r.iot.bluezcript.security

import android.content.Context
import android.content.SharedPreferences
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecurityManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bluezcript_security", Context.MODE_PRIVATE)

    fun savePairing(deviceId: String, psk: String) {
        prefs.edit().putString("device_id", deviceId).apply()
        prefs.edit().putString("psk", psk).apply()
        prefs.edit().putLong("nonce", 0).apply()
    }

    fun getDeviceId(): String? = prefs.getString("device_id", null)
    fun getPsk(): String? = prefs.getString("psk", null)

    private fun getNextNonce(): Long {
        val current = prefs.getLong("nonce", 0)
        val next = current + 1
        prefs.edit().putLong("nonce", next).apply()
        return next
    }

    fun generateAuthenticatedPayload(): ByteArray? {
        val pskHex = getPsk() ?: return null
        val psk = pskHex.decodeHex()
        val nonce = getNextNonce()
        
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
        return payload + sig
    }

    private fun String.decodeHex(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
