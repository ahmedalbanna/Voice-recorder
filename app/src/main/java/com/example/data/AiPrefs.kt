package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aiDataStore by preferencesDataStore(name = "ai_prefs")

class AiPrefs(private val context: Context) {
    private val optInKey = booleanPreferencesKey("ai_opt_in")

    val aiOptIn: Flow<Boolean> = context.aiDataStore.data.map { it[optInKey] ?: false }

    suspend fun setAiOptIn(enabled: Boolean) {
        context.aiDataStore.edit { it[optInKey] = enabled }
    }
}
