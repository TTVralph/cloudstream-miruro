package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private val Context.watchProgressDataStore by preferencesDataStore("watch_progress")

data class WatchProgress(
    val animeId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val audioType: AudioType,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
    val sourceProvider: String? = null,
    val sourceLabel: String? = null,
    val sourceId: String? = null
) {
    val key: String = makeKey(animeId, seasonNumber, episodeNumber, audioType)
    private val rawPercent: Float = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val watched: Boolean = rawPercent >= WATCHED_THRESHOLD
    val percent: Float = if (watched) 1f else rawPercent

    fun encoded(): String = listOf(
        animeId,
        seasonNumber,
        episodeNumber,
        audioType.name,
        positionMs,
        durationMs,
        updatedAtMs,
        sourceProvider.orEmpty().escapeProgressField(),
        sourceLabel.orEmpty().escapeProgressField(),
        sourceId.orEmpty().escapeProgressField()
    ).joinToString("|")

    companion object {
        const val WATCHED_THRESHOLD = 0.9f

        fun makeKey(
            animeId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
            audioType: AudioType
        ): String = "$animeId:$seasonNumber:$episodeNumber:${audioType.name}"

        fun decode(value: String): WatchProgress? {
            val parts = value.split('|')
            if (parts.size !in setOf(7, 9, 10)) return null
            return WatchProgress(
                animeId = parts[0].toIntOrNull() ?: return null,
                seasonNumber = parts[1].toIntOrNull() ?: return null,
                episodeNumber = parts[2].toIntOrNull() ?: return null,
                audioType = runCatching { AudioType.valueOf(parts[3]) }.getOrNull() ?: return null,
                positionMs = parts[4].toLongOrNull() ?: return null,
                durationMs = parts[5].toLongOrNull() ?: return null,
                updatedAtMs = parts[6].toLongOrNull() ?: return null,
                sourceProvider = parts.getOrNull(7)?.unescapeProgressField()?.takeIf { it.isNotBlank() },
                sourceLabel = parts.getOrNull(8)?.unescapeProgressField()?.takeIf { it.isNotBlank() },
                sourceId = parts.getOrNull(9)?.unescapeProgressField()?.takeIf { it.isNotBlank() }
            )
        }
    }
}

private fun String.escapeProgressField(): String = replace("%", "%25").replace("|", "%7C")
private fun String.unescapeProgressField(): String = replace("%7C", "|").replace("%25", "%")

private data class ProfileProgress(val profileId: String, val progress: WatchProgress) {
    fun encoded(): String = "$profileId~${progress.encoded()}"
}

private fun decodeProfileProgress(value: String): ProfileProgress? {
    val separator = value.indexOf('~')
    return if (separator > 0) {
        val profileId = value.substring(0, separator)
        val progress = WatchProgress.decode(value.substring(separator + 1)) ?: return null
        ProfileProgress(profileId, progress)
    } else {
        // Data saved before profiles existed belongs to the default profile.
        WatchProgress.decode(value)?.let { ProfileProgress(DEFAULT_PROFILE_ID, it) }
    }
}

class WatchProgressStore(private val context: Context) {
    private val key = stringSetPreferencesKey("episode_progress")

    val progress: Flow<List<WatchProgress>> = combine(
        context.watchProgressDataStore.data,
        ProfileSession.activeId
    ) { preferences, activeProfile ->
        preferences[key]
            .orEmpty()
            .mapNotNull(::decodeProfileProgress)
            .filter { it.profileId == activeProfile }
            .map { it.progress }
            .sortedByDescending { it.updatedAtMs }
    }

    suspend fun save(progress: WatchProgress) {
        val profileId = ProfileSession.activeId.value
        context.watchProgressDataStore.edit { preferences ->
            val all = preferences[key]
                .orEmpty()
                .mapNotNull(::decodeProfileProgress)
                .toMutableList()
            all.removeAll { it.profileId == profileId && it.progress.key == progress.key }
            all += ProfileProgress(profileId, progress)
            preferences[key] = all
                .groupBy { it.profileId }
                .flatMap { (_, entries) ->
                    entries.sortedByDescending { it.progress.updatedAtMs }.take(MAX_SAVED_PROGRESS)
                }
                .map(ProfileProgress::encoded)
                .toSet()
        }
    }

    suspend fun delete(animeId: Int, seasonNumber: Int, episodeNumber: Int, audioType: AudioType) {
        val profileId = ProfileSession.activeId.value
        val deleteKey = WatchProgress.makeKey(animeId, seasonNumber, episodeNumber, audioType)
        updateEntries { entry ->
            entry.profileId == profileId && entry.progress.key == deleteKey
        }
    }

    suspend fun deleteAnime(animeIds: Set<Int>) {
        if (animeIds.isEmpty()) return
        val profileId = ProfileSession.activeId.value
        updateEntries { entry ->
            entry.profileId == profileId && entry.progress.animeId in animeIds
        }
    }

    suspend fun clear() {
        val profileId = ProfileSession.activeId.value
        updateEntries { it.profileId == profileId }
    }

    private suspend fun updateEntries(removeWhen: (ProfileProgress) -> Boolean) {
        context.watchProgressDataStore.edit { preferences ->
            val remaining = preferences[key]
                .orEmpty()
                .mapNotNull(::decodeProfileProgress)
                .filterNot(removeWhen)
                .map(ProfileProgress::encoded)
                .toSet()
            if (remaining.isEmpty()) preferences.remove(key) else preferences[key] = remaining
        }
    }

    companion object {
        private const val MAX_SAVED_PROGRESS = 3_000
    }
}
