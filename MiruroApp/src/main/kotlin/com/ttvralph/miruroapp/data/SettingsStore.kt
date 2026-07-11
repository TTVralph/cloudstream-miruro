package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class PosterGridDensity { COMPACT, COMFORTABLE, LARGE }
enum class WatchlistSort { RECENTLY_ADDED, TITLE, PROGRESS }

data class AppSettings(
    val preferredAudio: AudioType = AudioType.SUB,
    val preferredProvider: String = "Auto",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val posterGridDensity: PosterGridDensity = PosterGridDensity.COMFORTABLE,
    val autoPlayNext: Boolean = false,
    val resumePlayback: Boolean = true,
    val subtitleLanguage: String = "English",
    val subtitleStyle: String = "Default",
    val subtitleChoice: String = "Auto",
    val hideWatchedEpisodes: Boolean = false,
    val noSpoilerMode: Boolean = false,
    val watchlistSort: WatchlistSort = WatchlistSort.RECENTLY_ADDED
)

private val Context.settingsDataStore by preferencesDataStore("miruro_settings")

private fun scopedSettingName(base: String, profileId: String): String =
    if (profileId == DEFAULT_PROFILE_ID) base else "${base}__${profileId}"

class SettingsStore(private val context: Context) {
    private object Names {
        const val preferredAudio = "preferred_audio"
        const val preferredProvider = "preferred_provider"
        const val themeMode = "theme_mode"
        const val posterGridDensity = "poster_grid_density"
        const val autoPlayNext = "auto_play_next"
        const val resumePlayback = "resume_playback"
        const val subtitleLanguage = "subtitle_language"
        const val subtitleStyle = "subtitle_style"
        const val subtitleChoice = "subtitle_choice"
        const val hideWatchedEpisodes = "hide_watched_episodes"
        const val noSpoilerMode = "no_spoiler_mode"
        const val watchlistSort = "watchlist_sort"
    }

    val settings: Flow<AppSettings> = combine(
        context.settingsDataStore.data,
        ProfileSession.activeId
    ) { preferences, profileId ->
        fun string(name: String): String? = preferences[stringPreferencesKey(scopedSettingName(name, profileId))]
        fun boolean(name: String): Boolean? = preferences[booleanPreferencesKey(scopedSettingName(name, profileId))]

        AppSettings(
            preferredAudio = string(Names.preferredAudio)
                ?.let { runCatching { AudioType.valueOf(it) }.getOrNull() }
                ?: AudioType.SUB,
            // Provider availability changes per title and episode. Always resolve in Auto mode.
            preferredProvider = "Auto",
            themeMode = string(Names.themeMode)
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            posterGridDensity = string(Names.posterGridDensity)
                ?.let { runCatching { PosterGridDensity.valueOf(it) }.getOrNull() }
                ?: PosterGridDensity.COMFORTABLE,
            autoPlayNext = boolean(Names.autoPlayNext) ?: false,
            resumePlayback = boolean(Names.resumePlayback) ?: true,
            subtitleLanguage = string(Names.subtitleLanguage) ?: "English",
            subtitleStyle = string(Names.subtitleStyle) ?: "Default",
            subtitleChoice = string(Names.subtitleChoice) ?: "Auto",
            hideWatchedEpisodes = boolean(Names.hideWatchedEpisodes) ?: false,
            noSpoilerMode = boolean(Names.noSpoilerMode) ?: false,
            watchlistSort = string(Names.watchlistSort)
                ?.let { runCatching { WatchlistSort.valueOf(it) }.getOrNull() }
                ?: WatchlistSort.RECENTLY_ADDED
        )
    }

    suspend fun updatePreferredAudio(value: AudioType) = updateString(Names.preferredAudio, value.name)

    suspend fun updatePreferredProvider(value: String) {
        // Keep the value for backwards compatibility, but Auto is the only global mode.
        updateString(Names.preferredProvider, "Auto")
    }

    suspend fun updateThemeMode(value: ThemeMode) = updateString(Names.themeMode, value.name)
    suspend fun updatePosterGridDensity(value: PosterGridDensity) = updateString(Names.posterGridDensity, value.name)
    suspend fun updateAutoPlayNext(value: Boolean) = updateBoolean(Names.autoPlayNext, value)
    suspend fun updateResumePlayback(value: Boolean) = updateBoolean(Names.resumePlayback, value)
    suspend fun updateSubtitleLanguage(value: String) = updateString(Names.subtitleLanguage, value)
    suspend fun updateSubtitleStyle(value: String) = updateString(Names.subtitleStyle, value)
    suspend fun updateSubtitleChoice(value: String) = updateString(Names.subtitleChoice, value)
    suspend fun updateHideWatchedEpisodes(value: Boolean) = updateBoolean(Names.hideWatchedEpisodes, value)
    suspend fun updateNoSpoilerMode(value: Boolean) = updateBoolean(Names.noSpoilerMode, value)
    suspend fun updateWatchlistSort(value: WatchlistSort) = updateString(Names.watchlistSort, value.name)

    private suspend fun updateString(name: String, value: String) {
        val profileId = ProfileSession.activeId.value
        context.settingsDataStore.edit { preferences ->
            preferences[stringPreferencesKey(scopedSettingName(name, profileId))] = value
        }
    }

    private suspend fun updateBoolean(name: String, value: Boolean) {
        val profileId = ProfileSession.activeId.value
        context.settingsDataStore.edit { preferences ->
            preferences[booleanPreferencesKey(scopedSettingName(name, profileId))] = value
        }
    }
}
