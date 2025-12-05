package com.bizur.android.push

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bizur.android.crypto.IdentityManager
import com.bizur.android.data.IdentityStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.whispersystems.libsignal.ecc.Curve
import kotlin.coroutines.resume
import kotlin.text.Charsets

private val Context.pushTokenStore: DataStore<Preferences> by preferencesDataStore(name = "bizur_push")

private const val PUSH_TAG = "BizurPush"

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrar(
    context: Context,
    private val identityStore: IdentityStore,
    private val identityManager: IdentityManager,
    private val client: PushRegistrationClient,
    private val scope: CoroutineScope
) {
    private val applicationContext = context.applicationContext
    private val dataStore = applicationContext.pushTokenStore
    private val tokenKey = stringPreferencesKey("fcm_token")
    private val started = AtomicBoolean(false)

    fun warmStart() {
        if (!started.compareAndSet(false, true)) return
        FirebaseApp.initializeApp(applicationContext)
        scope.launch { fetchAndRegisterToken() }
    }

    fun handleNewToken(token: String) {
        if (token.isBlank()) return
        scope.launch { registerIfNeeded(token) }
    }

    private suspend fun fetchAndRegisterToken() {
        val token = runCatching { fetchCurrentToken() }.getOrNull()
        if (!token.isNullOrBlank()) {
            registerIfNeeded(token)
        }
    }

    private suspend fun fetchCurrentToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result) {}
            } else {
                Log.w(PUSH_TAG, "FCM token fetch failed", task.exception)
                continuation.resume(null) {}
            }
        }
    }

    private suspend fun registerIfNeeded(token: String) {
        val cached = dataStore.data.first()[tokenKey]
        if (cached == token) return
        runCatching { registerToken(token) }
            .onSuccess {
                dataStore.edit { prefs -> prefs[tokenKey] = token }
            }
            .onFailure { err -> Log.w(PUSH_TAG, "Failed to register push token", err) }
    }

    private suspend fun registerToken(token: String) {
        val peerCode = identityStore.ensureIdentityCode()
        val profile = identityManager.getOrCreateProfile()
        val timestamp = System.currentTimeMillis()
        val statement = "$token|$peerCode|${profile.deviceId}|$timestamp"
        val signature = Curve.calculateSignature(
            profile.identityKeyPair.privateKey,
            statement.toByteArray(Charsets.UTF_8)
        )
        val payload = PushRegistrationPayload(
            peerCode = peerCode,
            deviceId = profile.deviceId,
            token = token,
            timestamp = timestamp,
            identityKey = profile.identityKeyPair.publicKey.serialize().encodeBase64(),
            signature = signature.encodeBase64()
        )
        client.register(payload)
    }
}

private fun ByteArray.encodeBase64(): String =
    Base64.encodeToString(this, Base64.NO_WRAP)