package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchlistDataStore by preferencesDataStore("watchlist")

data class WatchlistEntry(
    val id: Int,
    val title: String? = null,
    val posterUrl: String? = null,
    val addedAtMs: Long = System.currentTimeMillis()
) {
    fun encoded(): String = listOf(id.toString(), addedAtMs.toString(), title.orEmpty().escape(), posterUrl.orEmpty().escape()).joinToString("|")

    companion object {
        fun decode(value: String): WatchlistEntry? {
            val parts = value.split('|')
            if (parts.size == 1) return parts[0].toIntOrNull()?.let { WatchlistEntry(it) }
            return WatchlistEntry(
                id = parts.getOrNull(0)?.toIntOrNull() ?: return null,
                addedAtMs = parts.getOrNull(1)?.toLongOrNull() ?: System.currentTimeMillis(),
                title = parts.getOrNull(2)?.unescape()?.takeIf { it.isNotBlank() },
                posterUrl = parts.getOrNull(3)?.unescape()?.takeIf { it.isNotBlank() }
            )
        }

        private fun String.escape() = replace("%", "%25").replace("|", "%7C")
        private fun String.unescape() = replace("%7C", "|").replace("%25", "%")
    }
}

class WatchlistStore(private val context: Context) {
    private val key = stringSetPreferencesKey("favorite_anime_ids")
    val entries: Flow<List<WatchlistEntry>> = context.watchlistDataStore.data.map { prefs ->
        prefs[key].orEmpty().mapNotNull(WatchlistEntry::decode).sortedByDescending { it.addedAtMs }
    }
    val favoriteIds: Flow<Set<Int>> = entries.map { it.map(WatchlistEntry::id).toSet() }

    suspend fun setFavorite(item: AnimeItem, favorite: Boolean) = setFavorite(item.id, favorite, item)

    suspend fun setFavorite(id: Int, favorite: Boolean, item: AnimeItem? = null) {
        context.watchlistDataStore.edit { prefs ->
            val next = prefs[key].orEmpty().mapNotNull(WatchlistEntry::decode).filterNot { it.id == id }.toMutableList()
            if (favorite) next.add(WatchlistEntry(id, item?.title, item?.posterUrl, System.currentTimeMillis()))
            prefs[key] = next.map { it.encoded() }.toSet()
        }
    }
}
