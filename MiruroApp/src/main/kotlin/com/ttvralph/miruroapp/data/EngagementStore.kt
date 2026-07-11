package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private val Context.engagementDataStore by preferencesDataStore("anistream_engagement")

private data class StoredReaction(
    val profileId: String,
    val animeId: Int,
    val reaction: TitleReaction,
    val updatedAtMs: Long
) {
    fun encoded(): String = "$profileId|$animeId|${reaction.name}|$updatedAtMs"
}

private fun decodeReaction(value: String): StoredReaction? {
    val parts = value.split('|')
    if (parts.size != 4) return null
    return StoredReaction(
        profileId = parts[0],
        animeId = parts[1].toIntOrNull() ?: return null,
        reaction = runCatching { TitleReaction.valueOf(parts[2]) }.getOrNull() ?: return null,
        updatedAtMs = parts[3].toLongOrNull() ?: 0L
    )
}

private data class StoredReminder(val profileId: String, val animeId: Int) {
    fun encoded(): String = "$profileId|$animeId"
}

private fun decodeReminder(value: String): StoredReminder? {
    val parts = value.split('|')
    if (parts.size != 2) return null
    return StoredReminder(parts[0], parts[1].toIntOrNull() ?: return null)
}

private data class StoredTrackingStatus(
    val profileId: String,
    val animeId: Int,
    val status: TrackingStatus,
    val updatedAtMs: Long
) {
    fun encoded(): String = "$profileId|$animeId|${status.name}|$updatedAtMs"
}

private fun decodeTrackingStatus(value: String): StoredTrackingStatus? {
    val parts = value.split('|')
    if (parts.size != 4) return null
    return StoredTrackingStatus(
        profileId = parts[0],
        animeId = parts[1].toIntOrNull() ?: return null,
        status = runCatching { TrackingStatus.valueOf(parts[2]) }.getOrNull() ?: return null,
        updatedAtMs = parts[3].toLongOrNull() ?: 0L
    )
}

class EngagementStore(private val context: Context) {
    private object Keys {
        val reactions = stringSetPreferencesKey("title_reactions")
        val reminders = stringSetPreferencesKey("airing_reminders")
        val trackingStatuses = stringSetPreferencesKey("tracking_statuses")
    }

    val reactions: Flow<Map<Int, TitleReaction>> = combine(
        context.engagementDataStore.data,
        ProfileSession.activeId
    ) { preferences, activeProfile ->
        preferences[Keys.reactions]
            .orEmpty()
            .mapNotNull(::decodeReaction)
            .filter { it.profileId == activeProfile }
            .sortedBy { it.updatedAtMs }
            .associate { it.animeId to it.reaction }
    }

    val reminders: Flow<Set<Int>> = combine(
        context.engagementDataStore.data,
        ProfileSession.activeId
    ) { preferences, activeProfile ->
        preferences[Keys.reminders]
            .orEmpty()
            .mapNotNull(::decodeReminder)
            .filter { it.profileId == activeProfile }
            .map { it.animeId }
            .toSet()
    }

    val trackingStatuses: Flow<Map<Int, TrackingStatus>> = combine(
        context.engagementDataStore.data,
        ProfileSession.activeId
    ) { preferences, activeProfile ->
        preferences[Keys.trackingStatuses]
            .orEmpty()
            .mapNotNull(::decodeTrackingStatus)
            .filter { it.profileId == activeProfile }
            .sortedBy { it.updatedAtMs }
            .associate { it.animeId to it.status }
    }

    suspend fun setReaction(animeId: Int, reaction: TitleReaction?) {
        val profileId = ProfileSession.activeId.value
        context.engagementDataStore.edit { preferences ->
            val all = preferences[Keys.reactions]
                .orEmpty()
                .mapNotNull(::decodeReaction)
                .filterNot { it.profileId == profileId && it.animeId == animeId }
                .toMutableList()
            if (reaction != null) {
                all += StoredReaction(profileId, animeId, reaction, System.currentTimeMillis())
            }
            preferences[Keys.reactions] = all.map(StoredReaction::encoded).toSet()
        }
    }

    suspend fun toggleReminder(animeId: Int) {
        val profileId = ProfileSession.activeId.value
        context.engagementDataStore.edit { preferences ->
            val all = preferences[Keys.reminders]
                .orEmpty()
                .mapNotNull(::decodeReminder)
                .toMutableList()
            val exists = all.any { it.profileId == profileId && it.animeId == animeId }
            all.removeAll { it.profileId == profileId && it.animeId == animeId }
            if (!exists) all += StoredReminder(profileId, animeId)
            preferences[Keys.reminders] = all.map(StoredReminder::encoded).toSet()
        }
    }

    suspend fun setTrackingStatus(animeId: Int, status: TrackingStatus?) {
        val profileId = ProfileSession.activeId.value
        context.engagementDataStore.edit { preferences ->
            val all = preferences[Keys.trackingStatuses]
                .orEmpty()
                .mapNotNull(::decodeTrackingStatus)
                .filterNot { it.profileId == profileId && it.animeId == animeId }
                .toMutableList()
            if (status != null) {
                all += StoredTrackingStatus(profileId, animeId, status, System.currentTimeMillis())
            }
            preferences[Keys.trackingStatuses] = all.map(StoredTrackingStatus::encoded).toSet()
        }
    }
}
