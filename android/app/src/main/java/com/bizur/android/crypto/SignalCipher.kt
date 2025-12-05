package com.bizur.android.crypto

import android.util.Base64
import com.bizur.android.transport.EncryptedEnvelope
import com.bizur.android.transport.PreKeyService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.whispersystems.libsignal.InvalidMessageException
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage

class SignalCipher(
    private val signalStore: SignalStore,
    private val preKeyService: PreKeyService
) {
    private val addressCache = ConcurrentHashMap<String, SignalProtocolAddress>()
    private val sessionMutex = Mutex()

    suspend fun encrypt(peerId: String, plaintext: ByteArray): EncryptedEnvelope {
        val cipher = SessionCipher(signalStore, ensureSession(peerId, null))
        val ciphertext = cipher.encrypt(plaintext)
        return EncryptedEnvelope(
            ciphertext = ciphertext.serialize().encodeBase64(),
            messageType = ciphertext.type
        )
    }

    suspend fun decrypt(peerId: String, envelope: EncryptedEnvelope, remoteBundle: PreKeyBundlePayload?): ByteArray {
        val cipher = SessionCipher(signalStore, ensureSession(peerId, remoteBundle))
        val bytes = envelope.ciphertext.decodeBase64()
        return when (envelope.messageType) {
            CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(bytes))
            CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(bytes))
            else -> throw InvalidMessageException("Unsupported ciphertext type ${envelope.messageType}")
        }
    }

    private suspend fun ensureSession(peerId: String, remoteBundle: PreKeyBundlePayload?): SignalProtocolAddress {
        val cached = addressCache[peerId]
        if (cached != null && signalStore.containsSession(cached)) {
            return cached
        }

        return sessionMutex.withLock {
            val existing = addressCache[peerId]
            if (existing != null && signalStore.containsSession(existing)) {
                return@withLock existing
            }

            val bundle = remoteBundle ?: preKeyService.fetch(peerId)
                ?: throw IllegalStateException("Missing pre-key bundle for $peerId")
            val address = SignalProtocolAddress(peerId, bundle.deviceId)
            val builder = SessionBuilder(signalStore, address)
            builder.process(bundle.toSignalBundle())
            addressCache[peerId] = address
            address
        }
    }
}

private fun PreKeyBundlePayload.toSignalBundle(): PreKeyBundle {
    val identity = IdentityKey(identityKey.decodeBase64(), 0)
    val preKeyPublic = Curve.decodePoint(preKey.decodeBase64(), 0)
    val signedPreKeyPublic = Curve.decodePoint(signedPreKey.decodeBase64(), 0)
    return PreKeyBundle(
        registrationId,
        deviceId,
        preKeyId,
        preKeyPublic,
        signedPreKeyId,
        signedPreKeyPublic,
        signedPreKeySignature.decodeBase64(),
        identity
    )
}

private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP or Base64.URL_SAFE)

private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP or Base64.URL_SAFE)
