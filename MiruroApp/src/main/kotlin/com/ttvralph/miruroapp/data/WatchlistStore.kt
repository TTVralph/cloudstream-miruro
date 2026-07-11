package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.watchlistDataStore by preferencesDataStore("watchlist")

data class WatchlistEntry(
    val id: Int,
    val title: String? = null,
    val posterUrl: String? = null,
    val addedAtMs: Long = System.currentTimeMillis()
) {
    fun encoded(): String = listOf(
        id.toString(),
        addedAtMs.toString(),
        title.orEmpty().escape(),
        posterUrl.orEmpty().escape()
    ).joinToString("|")

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

private data class ProfileWatchlistEntry(val profileId: String, val entry: WatchlistEntry) {
    fun encoded(): String = "$profileId~${entry.encoded()}"
}

private fun decodeProfileWatchlistEntry(value: String): ProfileWatchlistEntry? {
    val separator = value.indexOf('~')
    return if (separator > 0) {
        val entry = WatchlistEntry.decode(value.substring(separator + 1)) ?: return null
        ProfileWatchlistEntry(value.substring(0, separator), entry)
    } else {
        // Legacy favourites remain attached to the default profile.
        WatchlistEntry.decode(value)?.let { ProfileWatchlistEntry(DEFAULT_PROFILE_ID, it) }
    }
}

class WatchlistStore(private val context: Context) {
    private val key = stringSetPreferencesKey("favorite_anime_ids")

    val entries: Flow<List<WatchlistEntry>> = combine(
        context.watchlistDataStore.data,
        ProfileSession.activeId
    ) { preferences, activeProfile ->
        preferences[key]
            .orEmpty()
            .mapNotNull(::decodeProfileWatchlistEntry)
            .filter { it.profileId == activeProfile }
            .map { it.entry }
            .sortedByDescending { it.addedAtMs }
    }

    val favoriteIds: Flow<Set<Int>> = entries.map { values -> values.map(WatchlistEntry::id).toSet() }

    suspend fun setFavorite(item: AnimeItem, favorite: Boolean) = setFavorite(item.id, favorite, item)

    suspend fun setFavorite(id: Int, favorite: Boolean, item: AnimeItem? = null) {
        val profileId = ProfileSession.activeId.value
        context.watchlistDataStore.edit { preferences ->
            val all = preferences[key]
                .orEmpty()
                .mapNotNull(::decodeProfileWatchlistEntry)
                .filterNot { it.profileId == profileId && it.entry.id == id }
                .toMutableList()
            if (favorite) {
                all += ProfileWatchlistEntry(
                    profileId,
                    WatchlistEntry(id, item?.title, item?.posterUrl, System.currentTimeMillis())
                )
            }
            preferences[key] = all.map(ProfileWatchlistEntry::encoded).toSet()
        }
    }
}
