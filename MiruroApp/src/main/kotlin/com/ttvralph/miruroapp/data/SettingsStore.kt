package com.ttvralph.miruroapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    val watchlistSort: WatchlistSort = WatchlistSort.RECENTLY_ADDED
)

private val Context.settingsDataStore by preferencesDataStore("miruro_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val preferredAudio = stringPreferencesKey("preferred_audio")
        val preferredProvider = stringPreferencesKey("preferred_provider")
        val themeMode = stringPreferencesKey("theme_mode")
        val posterGridDensity = stringPreferencesKey("poster_grid_density")
        val autoPlayNext = booleanPreferencesKey("auto_play_next")
        val resumePlayback = booleanPreferencesKey("resume_playback")
        val subtitleLanguage = stringPreferencesKey("subtitle_language")
        val subtitleStyle = stringPreferencesKey("subtitle_style")
        val subtitleChoice = stringPreferencesKey("subtitle_choice")
        val hideWatchedEpisodes = booleanPreferencesKey("hide_watched_episodes")
        val watchlistSort = stringPreferencesKey("watchlist_sort")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            preferredAudio = prefs[Keys.preferredAudio]?.let { runCatching { AudioType.valueOf(it) }.getOrNull() } ?: AudioType.SUB,
            // Provider availability changes per title and episode. Always resolve in Auto mode so
            // an old saved provider cannot strand playback on a dead source such as Kiwi.
            preferredProvider = "Auto",
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            posterGridDensity = prefs[Keys.posterGridDensity]?.let { runCatching { PosterGridDensity.valueOf(it) }.getOrNull() } ?: PosterGridDensity.COMFORTABLE,
            autoPlayNext = prefs[Keys.autoPlayNext] ?: false,
            resumePlayback = prefs[Keys.resumePlayback] ?: true,
            subtitleLanguage = prefs[Keys.subtitleLanguage] ?: "English",
            subtitleStyle = prefs[Keys.subtitleStyle] ?: "Default",
            subtitleChoice = prefs[Keys.subtitleChoice] ?: "Auto",
            hideWatchedEpisodes = prefs[Keys.hideWatchedEpisodes] ?: false,
            watchlistSort = prefs[Keys.watchlistSort]?.let { runCatching { WatchlistSort.valueOf(it) }.getOrNull() } ?: WatchlistSort.RECENTLY_ADDED
        )
    }

    suspend fun updatePreferredAudio(value: AudioType) { context.settingsDataStore.edit { it[Keys.preferredAudio] = value.name } }
    suspend fun updatePreferredProvider(value: String) {
        // Keep the key for backwards compatibility, but Auto is the only global mode. Provider
        // choices are exposed by the player after all providers for the episode have resolved.
        context.settingsDataStore.edit { it[Keys.preferredProvider] = "Auto" }
    }
    suspend fun updateThemeMode(value: ThemeMode) { context.settingsDataStore.edit { it[Keys.themeMode] = value.name } }
    suspend fun updatePosterGridDensity(value: PosterGridDensity) { context.settingsDataStore.edit { it[Keys.posterGridDensity] = value.name } }
    suspend fun updateAutoPlayNext(value: Boolean) { context.settingsDataStore.edit { it[Keys.autoPlayNext] = value } }
    suspend fun updateResumePlayback(value: Boolean) { context.settingsDataStore.edit { it[Keys.resumePlayback] = value } }
    suspend fun updateSubtitleLanguage(value: String) { context.settingsDataStore.edit { it[Keys.subtitleLanguage] = value } }
    suspend fun updateSubtitleStyle(value: String) { context.settingsDataStore.edit { it[Keys.subtitleStyle] = value } }
    suspend fun updateSubtitleChoice(value: String) { context.settingsDataStore.edit { it[Keys.subtitleChoice] = value } }
    suspend fun updateHideWatchedEpisodes(value: Boolean) { context.settingsDataStore.edit { it[Keys.hideWatchedEpisodes] = value } }
    suspend fun updateWatchlistSort(value: WatchlistSort) { context.settingsDataStore.edit { it[Keys.watchlistSort] = value.name } }
}
