package com.bizur.android.crypto

import android.content.Context
import android.util.Base64
import android.content.SharedPreferences
import androidx.security.crypto.MasterKey
import java.util.SortedSet
import java.util.TreeSet
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.InvalidKeyIdException
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.IdentityKeyStore
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.PreKeyStore
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SessionStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyStore
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.impl.InMemorySessionStore
import org.whispersystems.libsignal.util.KeyHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SignalStore(
    context: Context,
    private val identityManager: IdentityManager,
) : SignalProtocolStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val identityStore = identityManager.buildIdentityStore()
    private val preKeyStore = PreferencesPreKeyStore(prefs, json)
    private val signedPreKeyStore = PreferencesSignedPreKeyStore(prefs, json)
    private val sessionStore: SessionStore = InMemorySessionStore()

    private var nextPreKeyId: Int
        get() = prefs.getInt(KEY_NEXT_PREKEY_ID, 1)
        set(value) = prefs.edit().putInt(KEY_NEXT_PREKEY_ID, value).apply()

    private var nextSignedPreKeyId: Int
        get() = prefs.getInt(KEY_NEXT_SIGNED_PREKEY_ID, 1)
        set(value) = prefs.edit().putInt(KEY_NEXT_SIGNED_PREKEY_ID, value).apply()

    init {
        if (preKeyStore.isEmpty()) {
            generatePreKeys()
        }
        if (signedPreKeyStore.isEmpty()) {
            generateSignedPreKey()
        }
    }

    fun generatePreKeys(batchSize: Int = 50): List<Int> {
        val startId = nextPreKeyId
        val records = KeyHelper.generatePreKeys(startId, batchSize)
            .map { PreKeyRecord(it.id, it.keyPair) }
        records.forEach { preKeyStore.storePreKey(it.id, it) }
        nextPreKeyId = startId + batchSize
        return records.map { it.id }
    }

    fun generateSignedPreKey(): Int {
        val identity = identityManager.getOrCreateProfile()
        val signed = KeyHelper.generateSignedPreKey(identity.identityKeyPair, nextSignedPreKeyId)
        signedPreKeyStore.storeSignedPreKey(signed.id, signed)
        nextSignedPreKeyId = signed.id + 1
        return signed.id
    }

    fun exportPreKeyBundle(): PreKeyBundlePayload {
        if (preKeyStore.isEmpty()) {
            generatePreKeys()
        }
        if (signedPreKeyStore.isEmpty()) {
            generateSignedPreKey()
        }

        val profile = identityManager.getOrCreateProfile()
        val preKeyId = preKeyStore.peekOldestId()
            ?: throw IllegalStateException("No prekeys available")
        val preKey = preKeyStore.loadPreKey(preKeyId)
        val signedPreKeyId = signedPreKeyStore.peekLatestId()
            ?: throw IllegalStateException("No signed prekeys")
        val signedPreKey = signedPreKeyStore.loadSignedPreKey(signedPreKeyId)

        return PreKeyBundlePayload(
            registrationId = profile.registrationId,
            deviceId = profile.deviceId,
            identityKey = profile.identityKeyPair.publicKey.serialize().encodeBase64(),
            preKeyId = preKeyId,
            preKey = preKey.keyPair.publicKey.serialize().encodeBase64(),
            signedPreKeyId = signedPreKeyId,
            signedPreKey = signedPreKey.keyPair.publicKey.serialize().encodeBase64(),
            signedPreKeySignature = signedPreKey.signature.encodeBase64()
        )
    }

    override fun getIdentityKeyPair() = identityStore.identityKeyPair

    override fun getLocalRegistrationId() = identityStore.localRegistrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey) =
        identityStore.saveIdentity(address, identityKey)

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ) = identityStore.isTrustedIdentity(address, identityKey, direction)

    override fun getIdentity(address: SignalProtocolAddress) = identityStore.getIdentity(address)

    override fun loadPreKey(preKeyId: Int) = preKeyStore.loadPreKey(preKeyId)

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) = preKeyStore.storePreKey(preKeyId, record)

    override fun containsPreKey(preKeyId: Int) = preKeyStore.containsPreKey(preKeyId)

    override fun removePreKey(preKeyId: Int) = preKeyStore.removePreKey(preKeyId)

    override fun loadSignedPreKey(signedPreKeyId: Int) = signedPreKeyStore.loadSignedPreKey(signedPreKeyId)

    override fun loadSignedPreKeys() = signedPreKeyStore.loadSignedPreKeys()

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) =
        signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record)

    override fun containsSignedPreKey(signedPreKeyId: Int) = signedPreKeyStore.containsSignedPreKey(signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) = signedPreKeyStore.removeSignedPreKey(signedPreKeyId)

    override fun loadSession(address: SignalProtocolAddress): SessionRecord = sessionStore.loadSession(address)

    override fun getSubDeviceSessions(name: String) = sessionStore.getSubDeviceSessions(name)

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) =
        sessionStore.storeSession(address, record)

    override fun containsSession(address: SignalProtocolAddress) = sessionStore.containsSession(address)

    override fun deleteSession(address: SignalProtocolAddress) = sessionStore.deleteSession(address)

    override fun deleteAllSessions(name: String) = sessionStore.deleteAllSessions(name)

    companion object {
        private const val PREFS_NAME = "bizur.signal"
        private const val KEY_NEXT_PREKEY_ID = "next_prekey_id"
        private const val KEY_NEXT_SIGNED_PREKEY_ID = "next_signed_prekey_id"
    }
}

@Serializable
data class PreKeyBundlePayload(
    val registrationId: Int,
    val deviceId: Int,
    val identityKey: String,
    val preKeyId: Int,
    val preKey: String,
    val signedPreKeyId: Int,
    val signedPreKey: String,
    val signedPreKeySignature: String
)

private class PreferencesPreKeyStore(
    private val prefs: SharedPreferences,
    private val json: Json
) : PreKeyStore {
    private var cache: MutableMap<Int, PreKeyRecord> = loadFromPrefs()

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        cache[preKeyId] ?: throw InvalidKeyIdException("Unknown prekey $preKeyId")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        cache[preKeyId] = record
        persist()
    }

    override fun containsPreKey(preKeyId: Int) = cache.containsKey(preKeyId)

    override fun removePreKey(preKeyId: Int) {
        cache.remove(preKeyId)
        persist()
    }

    fun peekOldestId(): Int? = cache.keys.minOrNull()

    fun isEmpty() = cache.isEmpty()

    private fun persist() {
        val serialized = cache.entries.map { SerializedKey(it.key, it.value.serialize().encodeBase64()) }
        prefs.edit().putString(KEY_PREKEYS, json.encodeToString(serialized)).apply()
    }

    private fun loadFromPrefs(): MutableMap<Int, PreKeyRecord> {
        val raw = prefs.getString(KEY_PREKEYS, null) ?: return mutableMapOf()
        val decoded = json.decodeFromString<List<SerializedKey>>(raw)
        return decoded.associate { it.id to PreKeyRecord(it.blob.decodeBase64()) }.toMutableMap()
    }

    companion object {
        private const val KEY_PREKEYS = "prekeys"
    }
}

private class PreferencesSignedPreKeyStore(
    private val prefs: SharedPreferences,
    private val json: Json
) : SignedPreKeyStore {
    private var cache: MutableMap<Int, SignedPreKeyRecord> = loadFromPrefs()
    private val order: SortedSet<Int> = TreeSet(cache.keys)

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        cache[signedPreKeyId] ?: throw InvalidKeyIdException("Unknown signed prekey $signedPreKeyId")

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = cache.values.toMutableList()

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        cache[signedPreKeyId] = record
        order.add(signedPreKeyId)
        persist()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int) = cache.containsKey(signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        cache.remove(signedPreKeyId)
        order.remove(signedPreKeyId)
        persist()
    }

    fun peekLatestId(): Int? = order.lastOrNull()

    fun isEmpty() = cache.isEmpty()

    private fun persist() {
        val serialized = cache.entries.map { SerializedKey(it.key, it.value.serialize().encodeBase64()) }
        prefs.edit().putString(KEY_SIGNED_PREKEYS, json.encodeToString(serialized)).apply()
    }

    private fun loadFromPrefs(): MutableMap<Int, SignedPreKeyRecord> {
        val raw = prefs.getString(KEY_SIGNED_PREKEYS, null) ?: return mutableMapOf()
        val decoded = json.decodeFromString<List<SerializedKey>>(raw)
        return decoded.associate { it.id to SignedPreKeyRecord(it.blob.decodeBase64()) }.toMutableMap()
    }

    companion object {
        private const val KEY_SIGNED_PREKEYS = "signed_prekeys"
    }
}

@Serializable
private data class SerializedKey(val id: Int, val blob: String)

private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)