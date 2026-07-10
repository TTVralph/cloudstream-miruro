package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchProgressDataStore by preferencesDataStore("watch_progress")

data class WatchProgress(
    val animeId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val audioType: AudioType,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long
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
        updatedAtMs
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
            if (parts.size != 7) return null
            return WatchProgress(
                animeId = parts[0].toIntOrNull() ?: return null,
                seasonNumber = parts[1].toIntOrNull() ?: return null,
                episodeNumber = parts[2].toIntOrNull() ?: return null,
                audioType = runCatching { AudioType.valueOf(parts[3]) }.getOrNull() ?: return null,
                positionMs = parts[4].toLongOrNull() ?: return null,
                durationMs = parts[5].toLongOrNull() ?: return null,
                updatedAtMs = parts[6].toLongOrNull() ?: return null
            )
        }
    }
}

class WatchProgressStore(private val context: Context) {
    private val key = stringSetPreferencesKey("episode_progress")

    val progress: Flow<List<WatchProgress>> = context.watchProgressDataStore.data.map { preferences ->
        preferences[key]
            .orEmpty()
            .mapNotNull(WatchProgress::decode)
            .sortedByDescending { it.updatedAtMs }
    }

    suspend fun save(progress: WatchProgress) {
        context.watchProgressDataStore.edit { preferences ->
            val next = preferences[key]
                .orEmpty()
                .mapNotNull(WatchProgress::decode)
                .filterNot { it.key == progress.key }
                .toMutableList()
            next.add(progress)
            preferences[key] = next
                .sortedByDescending { it.updatedAtMs }
                .take(MAX_SAVED_PROGRESS)
                .map { it.encoded() }
                .toSet()
        }
    }

    suspend fun delete(animeId: Int, seasonNumber: Int, episodeNumber: Int, audioType: AudioType) {
        val deleteKey = WatchProgress.makeKey(animeId, seasonNumber, episodeNumber, audioType)
        context.watchProgressDataStore.edit { preferences ->
            preferences[key] = preferences[key]
                .orEmpty()
                .mapNotNull(WatchProgress::decode)
                .filterNot { it.key == deleteKey }
                .map { it.encoded() }
                .toSet()
        }
    }

    suspend fun clear() {
        context.watchProgressDataStore.edit { it.remove(key) }
    }

    companion object {
        private const val MAX_SAVED_PROGRESS = 3_000
    }
}
