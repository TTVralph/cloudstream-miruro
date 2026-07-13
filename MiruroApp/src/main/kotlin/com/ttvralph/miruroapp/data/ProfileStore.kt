package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore("anistream_profiles")

/**
 * Lightweight bridge used by the existing progress, watchlist, and settings stores.
 * It defaults to the legacy profile so existing data remains visible before ProfileStore loads.
 */
object ProfileSession {
    private val _activeId = MutableStateFlow(DEFAULT_PROFILE_ID)
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    fun activate(id: String) {
        _activeId.value = id.ifBlank { DEFAULT_PROFILE_ID }
    }
}

class ProfileStore(private val context: Context) {
    private object Keys {
        val profiles = stringSetPreferencesKey("profiles")
        val active = stringPreferencesKey("active_profile")
    }

    private val defaultProfile = LocalProfile(DEFAULT_PROFILE_ID, "Main", 0L)

    val state: Flow<ProfileState> = context.profileDataStore.data.map { preferences ->
        val profiles = preferences[Keys.profiles]
            .orEmpty()
            .mapNotNull(::decodeProfile)
            .plus(defaultProfile)
            .distinctBy { it.id }
            .sortedWith(compareBy<LocalProfile> { it.id != DEFAULT_PROFILE_ID }.thenBy { it.createdAtMs })
        val requested = preferences[Keys.active]
        val active = requested?.takeIf { id -> profiles.any { it.id == id } } ?: DEFAULT_PROFILE_ID
        ProfileState(profiles, active)
    }

    suspend fun create(name: String, avatarId: String = "crimson"): LocalProfile {
        val cleaned = name.trim().take(24).ifBlank { "Profile" }
        val profile = LocalProfile(
            id = "profile_${System.currentTimeMillis().toString(36)}",
            name = cleaned,
            createdAtMs = System.currentTimeMillis(),
            avatarId = avatarId.takeIf { it in PROFILE_AVATAR_IDS } ?: "crimson"
        )
        context.profileDataStore.edit { preferences ->
            val existing = preferences[Keys.profiles].orEmpty().mapNotNull(::decodeProfile)
            preferences[Keys.profiles] = (existing + profile).map(::encodeProfile).toSet()
            preferences[Keys.active] = profile.id
        }
        ProfileSession.activate(profile.id)
        return profile
    }

    suspend fun update(profile: LocalProfile, name: String, avatarId: String) {
        val updated = profile.copy(
            name = name.trim().take(24).ifBlank { profile.name },
            avatarId = avatarId.takeIf { it in PROFILE_AVATAR_IDS } ?: profile.avatarId
        )
        context.profileDataStore.edit { preferences ->
            val existing = preferences[Keys.profiles]
                .orEmpty()
                .mapNotNull(::decodeProfile)
                .filterNot { it.id == profile.id }
            preferences[Keys.profiles] = (existing + updated).map(::encodeProfile).toSet()
        }
    }

    suspend fun activate(id: String) {
        context.profileDataStore.edit { preferences -> preferences[Keys.active] = id }
        ProfileSession.activate(id)
    }

    suspend fun delete(id: String) {
        if (id == DEFAULT_PROFILE_ID) return
        context.profileDataStore.edit { preferences ->
            val remaining = preferences[Keys.profiles]
                .orEmpty()
                .mapNotNull(::decodeProfile)
                .filterNot { it.id == id }
            preferences[Keys.profiles] = remaining.map(::encodeProfile).toSet()
            if (preferences[Keys.active] == id) preferences[Keys.active] = DEFAULT_PROFILE_ID
        }
        if (ProfileSession.activeId.value == id) ProfileSession.activate(DEFAULT_PROFILE_ID)
    }

    private fun encodeProfile(profile: LocalProfile): String = listOf(
        profile.id.escapeProfileField(),
        profile.name.escapeProfileField(),
        profile.createdAtMs.toString(),
        profile.avatarId.escapeProfileField()
    ).joinToString("|")

    private fun decodeProfile(value: String): LocalProfile? {
        val parts = value.split('|')
        if (parts.size != 3 && parts.size != 4) return null
        val id = parts[0].unescapeProfileField().takeIf { it.isNotBlank() } ?: return null
        val name = parts[1].unescapeProfileField().takeIf { it.isNotBlank() } ?: return null
        val created = parts[2].toLongOrNull() ?: 0L
        val avatarId = parts.getOrNull(3)
            ?.unescapeProfileField()
            ?.takeIf { it in PROFILE_AVATAR_IDS }
            ?: if (id == DEFAULT_PROFILE_ID) "crimson" else PROFILE_AVATAR_IDS[(id.hashCode() and Int.MAX_VALUE) % PROFILE_AVATAR_IDS.size]
        return LocalProfile(id, name, created, avatarId)
    }
}

private fun String.escapeProfileField(): String = replace("%", "%25").replace("|", "%7C")
private fun String.unescapeProfileField(): String = replace("%7C", "|").replace("%25", "%")
