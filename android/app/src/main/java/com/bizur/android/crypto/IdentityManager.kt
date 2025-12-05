package com.bizur.android.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.state.impl.InMemoryIdentityKeyStore
import org.whispersystems.libsignal.util.KeyHelper

/**
 * Manages the long-term Signal identity for the current device.
 * Keys are generated once and stored inside EncryptedSharedPreferences.
 */
class IdentityManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val random = SecureRandom()

    fun getOrCreateProfile(): IdentityProfile {
        val cachedPublic = prefs.getString(KEY_IDENTITY_PUBLIC, null)
        val cachedPrivate = prefs.getString(KEY_IDENTITY_PRIVATE, null)
        val cachedReg = prefs.getInt(KEY_REGISTRATION_ID, -1)
        val cachedDeviceId = prefs.getInt(KEY_DEVICE_ID, -1)

        return if (cachedPublic != null && cachedPrivate != null && cachedReg != -1 && cachedDeviceId != -1) {
            IdentityProfile(
                registrationId = cachedReg,
                deviceId = cachedDeviceId,
                identityKeyPair = IdentityKeyPair(
                    IdentityKey(Base64.decode(cachedPublic, Base64.NO_WRAP), 0),
                    Curve.decodePrivatePoint(Base64.decode(cachedPrivate, Base64.NO_WRAP))
                )
            )
        } else {
            createProfile().also { profile ->
                prefs.edit()
                    .putString(KEY_IDENTITY_PUBLIC, Base64.encodeToString(profile.identityKeyPair.publicKey.serialize(), Base64.NO_WRAP))
                    .putString(KEY_IDENTITY_PRIVATE, Base64.encodeToString(profile.identityKeyPair.privateKey.serialize(), Base64.NO_WRAP))
                    .putInt(KEY_REGISTRATION_ID, profile.registrationId)
                    .putInt(KEY_DEVICE_ID, profile.deviceId)
                    .apply()
            }
        }
    }

    private fun createProfile(): IdentityProfile {
        val identityKeyPair = Curve.generateKeyPair().let { keyPair ->
            IdentityKeyPair(IdentityKey(keyPair.publicKey), keyPair.privateKey)
        }
        val registrationId = KeyHelper.generateRegistrationId(true)
        val deviceId = random.nextInt(1_000_000)
        return IdentityProfile(
            registrationId = registrationId,
            deviceId = deviceId,
            identityKeyPair = identityKeyPair
        )
    }

    fun buildIdentityStore(): InMemoryIdentityKeyStore {
        val profile = getOrCreateProfile()
        return object : InMemoryIdentityKeyStore(profile.identityKeyPair, profile.registrationId) {}
    }

    companion object {
        private const val PREFS_NAME = "bizur.identity"
        private const val KEY_IDENTITY_PUBLIC = "identity_public"
        private const val KEY_IDENTITY_PRIVATE = "identity_private"
        private const val KEY_REGISTRATION_ID = "identity_reg"
        private const val KEY_DEVICE_ID = "identity_device"
    }
}

data class IdentityProfile(
    val registrationId: Int,
    val deviceId: Int,
    val identityKeyPair: IdentityKeyPair
)
