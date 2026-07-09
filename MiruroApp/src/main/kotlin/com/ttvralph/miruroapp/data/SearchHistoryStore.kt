package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryDataStore by preferencesDataStore("search_history")

class SearchHistoryStore(private val context: Context) {
    private val key = stringSetPreferencesKey("recent_searches")
    val recentSearches: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        prefs[key].orEmpty().mapNotNull { value ->
            val parts = value.split('|', limit = 2)
            val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val term = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            timestamp to term
        }.sortedByDescending { it.first }.map { it.second }.take(8)
    }

    suspend fun add(term: String) {
        val cleaned = term.trim().take(80)
        if (cleaned.isBlank()) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[key].orEmpty().mapNotNull { value ->
                val parts = value.split('|', limit = 2)
                val existing = parts.getOrNull(1) ?: return@mapNotNull null
                if (existing.equals(cleaned, ignoreCase = true)) null else value
            }
            prefs[key] = (listOf("${System.currentTimeMillis()}|$cleaned") + current).take(8).toSet()
        }
    }
}
