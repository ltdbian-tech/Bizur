package com.bizur.android.transport

import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generates and parses QR codes for offline peer pairing.
 * 
 * The QR contains:
 * - Peer identity code
 * - Local IP address for direct connection
 * - Pre-key bundle for Signal encryption setup
 * - Connection port
 * 
 * This allows two devices on the same network to establish
 * an encrypted P2P connection without any server.
 */
object OfflinePairingQR {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class PairingPayload(
        val peerCode: String,
        val localIp: String,
        val port: Int,
        val registrationId: Int,
        val deviceId: Int,
        val identityKey: String,
        val signedPreKeyId: Int,
        val signedPreKey: String,
        val signedPreKeySignature: String,
        val preKeyId: Int,
        val preKey: String
    )

    /**
     * Generate a QR code bitmap containing the pairing payload.
     */
    fun generateQrBitmap(payload: PairingPayload, size: Int = 512): Bitmap {
        val content = json.encodeToString(payload)
        val compressed = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(compressed, BarcodeFormat.QR_CODE, size, size, hints)
        
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /**
     * Parse a scanned QR code string back into a pairing payload.
     */
    fun parseQrContent(encoded: String): PairingPayload? {
        return try {
            val decoded = Base64.decode(encoded, Base64.NO_WRAP).toString(Charsets.UTF_8)
            json.decodeFromString<PairingPayload>(decoded)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate pairing payload string (for display or sharing).
     */
    fun encodePayload(payload: PairingPayload): String {
        val content = json.encodeToString(payload)
        return Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
