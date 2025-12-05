package com.bizur.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(name = "bizur_prefs")

class DraftStore(context: Context) {
    private val dataStore = context.draftDataStore
    private val draftKey = stringPreferencesKey("draft_text")

    val draft: Flow<String> = dataStore.data.map { prefs -> prefs[draftKey] ?: "" }

    suspend fun update(text: String) {
        dataStore.edit { prefs ->
            if (text.isEmpty()) {
                prefs.remove(draftKey)
            } else {
                prefs[draftKey] = text
            }
        }
    }
}
