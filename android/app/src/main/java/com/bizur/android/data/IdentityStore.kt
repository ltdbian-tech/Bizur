package com.bizur.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(name = "bizur_identity")

class IdentityStore(context: Context) {
    private val dataStore = context.identityDataStore
    private val identityKey = stringPreferencesKey("identity_code")
    private val random = SecureRandom()

    val identityCode: Flow<String> = dataStore.data.map { prefs -> prefs[identityKey] ?: "" }

    suspend fun ensureIdentityCode(): String {
        var resolved: String? = null
        dataStore.edit { prefs ->
            val existing = prefs[identityKey]
            val value = if (existing.isNullOrBlank()) generateIdentityCode() else existing
            if (existing.isNullOrBlank()) {
                prefs[identityKey] = value
            }
            resolved = value
        }
        return resolved ?: generateIdentityCode()
    }

    private fun generateIdentityCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val builder = StringBuilder("BZ-")
        repeat(8) {
            val index = random.nextInt(alphabet.length)
            builder.append(alphabet[index])
        }
        return builder.toString()
    }
}
