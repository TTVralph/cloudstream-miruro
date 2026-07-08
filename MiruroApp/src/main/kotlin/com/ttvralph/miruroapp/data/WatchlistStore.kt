package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchlistDataStore by preferencesDataStore("watchlist")

class WatchlistStore(private val context: Context) {
    private val key = stringSetPreferencesKey("favorite_anime_ids")
    val favoriteIds: Flow<Set<Int>> = context.watchlistDataStore.data.map { prefs -> prefs[key].orEmpty().mapNotNull { it.toIntOrNull() }.toSet() }
    suspend fun setFavorite(id: Int, favorite: Boolean) { context.watchlistDataStore.edit { prefs -> val next = prefs[key].orEmpty().toMutableSet(); if (favorite) next.add(id.toString()) else next.remove(id.toString()); prefs[key] = next } }
}
