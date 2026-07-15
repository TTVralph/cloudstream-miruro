package com.ttvralph.miruroapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttvralph.miruroapp.data.AniListRepository
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.EngagementStore
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.LocalProfile
import com.ttvralph.miruroapp.data.NetflixFeatureRepository
import com.ttvralph.miruroapp.data.ProfileSession
import com.ttvralph.miruroapp.data.ProfileState
import com.ttvralph.miruroapp.data.PROFILE_AVATAR_IDS
import com.ttvralph.miruroapp.data.PROFILE_THEME_COLOR_IDS
import com.ttvralph.miruroapp.data.ProfileStore
import com.ttvralph.miruroapp.data.SettingsStore
import com.ttvralph.miruroapp.data.SkipInterval
import com.ttvralph.miruroapp.data.TitleExtras
import com.ttvralph.miruroapp.data.TitleReaction
import com.ttvralph.miruroapp.data.TrackingStatus
import com.ttvralph.miruroapp.data.UpcomingEpisode
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.data.WatchProgressStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetflixFeatureViewModel(application: Application) : AndroidViewModel(application) {
    private val profilesStore = ProfileStore(application)
    private val engagement = EngagementStore(application)
    private val progressStore = WatchProgressStore(application)
    private val settingsStore = SettingsStore(application)
    private val repository = NetflixFeatureRepository()
    private val animeRepository = AniListRepository()

    /** Null until DataStore has produced the real cold-start profile snapshot. */
    val loadedProfileState: StateFlow<ProfileState?> = profilesStore.state
        .map<ProfileState, ProfileState?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profileState: StateFlow<ProfileState> = loadedProfileState
        .map { it ?: ProfileState() }
        .stateIn(
            viewModelScope, SharingStarted.Eagerly, ProfileState()
        )
    val reactions: StateFlow<Map<Int, TitleReaction>> = engagement.reactions.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )
    val reminders: StateFlow<Set<Int>> = engagement.reminders.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptySet()
    )
    val trackingStatuses: StateFlow<Map<Int, TrackingStatus>> = engagement.trackingStatuses.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )

    private val _metadata = MutableStateFlow<Map<Int, AnimeItem>>(emptyMap())
    val metadata = _metadata.asStateFlow()
    private val _extras = MutableStateFlow<UiState<TitleExtras>?>(null)
    val extras = _extras.asStateFlow()
    private val _personalizedRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val personalizedRows = _personalizedRows.asStateFlow()
    private val _upcoming = MutableStateFlow<List<UpcomingEpisode>>(emptyList())
    val upcoming = _upcoming.asStateFlow()
    private val _skipIntervals = MutableStateFlow<Map<String, List<SkipInterval>>>(emptyMap())
    val skipIntervals = _skipIntervals.asStateFlow()
    private val skipRequestsInFlight = mutableSetOf<String>()
    private val skipRetryAfterMs = mutableMapOf<String, Long>()

    private var extrasJob: Job? = null
    private var homeJob: Job? = null
    private var lastHomeSignature: String? = null

    init {
        viewModelScope.launch {
            var previous: String? = null
            profileState.collect { state ->
                ProfileSession.activate(state.activeId)
                if (previous != null && previous != state.activeId) resetProfilePresentation()
                previous = state.activeId
            }
        }
    }

    fun createProfile(name: String? = null, avatarId: String? = null, themeColorId: String? = null) {
        val number = profileState.value.profiles.size + 1
        viewModelScope.launch {
            profilesStore.create(
                name = name?.trim().takeUnless { it.isNullOrBlank() } ?: "Profile $number",
                avatarId = avatarId?.takeIf { it in PROFILE_AVATAR_IDS }
                    ?: PROFILE_AVATAR_IDS[(number - 1) % PROFILE_AVATAR_IDS.size],
                themeColorId = themeColorId?.takeIf { it in PROFILE_THEME_COLOR_IDS }
                    ?: PROFILE_THEME_COLOR_IDS[(number - 1) % PROFILE_THEME_COLOR_IDS.size]
            )
        }
    }

    fun updateProfile(profile: LocalProfile, name: String, avatarId: String, themeColorId: String) {
        viewModelScope.launch { profilesStore.update(profile, name, avatarId, themeColorId) }
    }

    fun switchProfile(profile: LocalProfile) {
        ProfileSession.activate(profile.id)
        viewModelScope.launch { profilesStore.activate(profile.id) }
    }

    fun deleteProfile(profile: LocalProfile) {
        viewModelScope.launch { profilesStore.delete(profile.id) }
    }

    fun setReaction(animeId: Int, reaction: TitleReaction) {
        viewModelScope.launch {
            engagement.setReaction(animeId, reaction.takeUnless { reactions.value[animeId] == it })
            lastHomeSignature = null
        }
    }

    fun toggleReminder(animeId: Int) {
        viewModelScope.launch {
            engagement.toggleReminder(animeId)
            lastHomeSignature = null
        }
    }

    fun setTrackingStatus(animeId: Int, status: TrackingStatus?) {
        viewModelScope.launch {
            engagement.setTrackingStatus(animeId, status)
            lastHomeSignature = null
        }
    }

    fun updateNoSpoilerMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.updateNoSpoilerMode(enabled) }
    }

    fun loadMetadata(ids: Set<Int>) {
        val missing = ids.filterNot { it in _metadata.value }.toSet()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val items = try {
                repository.items(missing)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (!isActive) return@launch
            if (items.isNotEmpty()) _metadata.value += items.associateBy { it.id }
        }
    }

    fun loadExtras(animeId: Int) {
        extrasJob?.cancel()
        extrasJob = viewModelScope.launch {
            _extras.value = UiState.Loading
            try {
                val value = repository.titleExtras(animeId)
                if (!isActive) return@launch
                val items = value.related + value.recommendations + listOfNotNull(value.nextAiring?.anime)
                _metadata.value += items.associateBy { it.id }
                _extras.value = UiState.Success(value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isActive) _extras.value = UiState.Error("Could not load related anime right now.")
            }
        }
    }

    fun loadHomeFeatures(
        catalogue: List<AnimeItem>,
        progress: List<WatchProgress>,
        favorites: Set<Int>
    ) {
        val positive = reactions.value.filterValues {
            it == TitleReaction.LIKE || it == TitleReaction.LOVE
        }.keys
        val disliked = reactions.value.filterValues { it == TitleReaction.DISLIKE }.keys
        val seeds = (
            positive +
                favorites +
                progress.map { it.animeId } +
                reminders.value +
                trackingStatuses.value.keys
            ).filter { it > 0 }.toSet()
        val signature = listOf(
            profileState.value.activeId,
            seeds.sorted().joinToString(),
            disliked.sorted().joinToString(),
            trackingStatuses.value.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.name}" },
            catalogue.take(20).joinToString { it.id.toString() }
        ).joinToString("|")
        if (signature == lastHomeSignature) return
        lastHomeSignature = signature
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val requestedProfileId = profileState.value.activeId
            val rows = mutableListOf<HomeRow>()
            positive.take(3).forEach { id ->
                val value = try {
                    repository.titleExtras(id)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                } ?: return@forEach
                if (!isActive || profileState.value.activeId != requestedProfileId) return@launch
                val source = _metadata.value[id]?.title
                    ?: catalogue.firstOrNull { it.id == id }?.title
                    ?: "a title you liked"
                val items = value.recommendations
                    .filterNot { it.id == id || it.id in disliked }
                    .take(18)
                if (items.isNotEmpty()) rows += HomeRow("Because You Liked $source", items)
            }
            if (!isActive || profileState.value.activeId != requestedProfileId) return@launch
            if (rows.isEmpty()) {
                val items = catalogue.filterNot { it.id in disliked }
                    .sortedByDescending { it.score ?: 0 }
                    .distinctBy { it.id }
                    .take(18)
                if (items.isNotEmpty()) {
                    val profileName = profileState.value.profiles
                        .firstOrNull { it.id == requestedProfileId }
                        ?.name
                        ?: "You"
                    rows += HomeRow("Top Picks for $profileName", items)
                }
            }
            if (!isActive || profileState.value.activeId != requestedProfileId) return@launch
            _personalizedRows.value = rows.distinctBy { it.title }.take(3)
            val upcoming = try {
                repository.upcoming(seeds)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (!isActive || profileState.value.activeId != requestedProfileId) return@launch
            _upcoming.value = upcoming
            val found = rows.flatMap { it.items } + _upcoming.value.map { it.anime }
            _metadata.value += found.associateBy { it.id }
        }
    }

    fun loadSkipTimes(episode: AnimeEpisode, durationMs: Long) {
        if (durationMs <= 0L) return
        val key = skipKey(episode, durationMs)
        val now = System.currentTimeMillis()
        if (
            key in _skipIntervals.value ||
            key in skipRequestsInFlight ||
            now < (skipRetryAfterMs[key] ?: 0L)
        ) return
        skipRequestsInFlight += key
        viewModelScope.launch {
            try {
                val values = repository.skipTimes(
                    episode.anilistId,
                    episode.episodeNumber,
                    durationMs / 1_000.0
                )
                if (values.isEmpty()) {
                    // AniSkip can legitimately return no data, but that answer
                    // must not become a permanent miss if duration or upstream
                    // data stabilizes later.
                    skipRetryAfterMs[key] = System.currentTimeMillis() + SKIP_NO_DATA_RETRY_DELAY_MS
                } else {
                    _skipIntervals.value += key to values
                    skipRetryAfterMs.remove(key)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                skipRetryAfterMs[key] = System.currentTimeMillis() + SKIP_RETRY_DELAY_MS
            } finally {
                skipRequestsInFlight -= key
            }
        }
    }

    fun intervalsFor(episode: AnimeEpisode, durationMs: Long): List<SkipInterval> =
        _skipIntervals.value[skipKey(episode, durationMs)].orEmpty()

    fun removeProgress(progress: WatchProgress) {
        viewModelScope.launch {
            progressStore.delete(progress.animeId, progress.seasonNumber, progress.episodeNumber, progress.audioType)
        }
    }

    fun removeTitleProgress(animeIds: Set<Int>) {
        viewModelScope.launch { progressStore.deleteAnime(animeIds) }
    }

    fun setSeasonWatched(episodes: List<AnimeEpisode>, watched: Boolean) {
        viewModelScope.launch {
            if (watched) {
                selectUniqueEpisodes(episodes).forEach { episode ->
                    progressStore.save(
                        WatchProgress(
                            animeId = episode.anilistId,
                            seasonNumber = episode.seasonNumber,
                            episodeNumber = episode.episodeNumber,
                            audioType = episode.audioType,
                            positionMs = 9_000L,
                            durationMs = 10_000L,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                episodes.forEach { episode ->
                    progressStore.delete(
                        episode.anilistId,
                        episode.seasonNumber,
                        episode.episodeNumber,
                        episode.audioType
                    )
                }
            }
        }
    }

    fun setTitleWatched(animeId: Int, watched: Boolean) {
        viewModelScope.launch {
            val details = try {
                animeRepository.details(animeId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            if (!isActive) return@launch
            if (watched) {
                details.seasons.forEach { season ->
                    selectUniqueEpisodes(season.episodes).forEach { episode ->
                        progressStore.save(
                            WatchProgress(
                                animeId = episode.anilistId,
                                seasonNumber = episode.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                                audioType = episode.audioType,
                                positionMs = 9_000L,
                                durationMs = 10_000L,
                                updatedAtMs = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } else {
                progressStore.deleteAnime(details.seasons.map { it.id }.toSet() + details.id)
            }
        }
    }

    private fun selectUniqueEpisodes(episodes: List<AnimeEpisode>): List<AnimeEpisode> =
        episodes.groupBy { it.episodeNumber }.mapNotNull { (_, versions) ->
            versions.firstOrNull { it.sourceCandidates.isNotEmpty() }
                ?: versions.firstOrNull()
        }

    private fun resetProfilePresentation() {
        _personalizedRows.value = emptyList()
        _upcoming.value = emptyList()
        _extras.value = null
        _skipIntervals.value = emptyMap()
        skipRequestsInFlight.clear()
        skipRetryAfterMs.clear()
        lastHomeSignature = null
    }

    private fun skipKey(episode: AnimeEpisode, durationMs: Long): String =
        "${episode.anilistId}:${episode.episodeNumber}:${durationMs / 30_000L}"
}

private const val SKIP_RETRY_DELAY_MS = 15_000L
private const val SKIP_NO_DATA_RETRY_DELAY_MS = 2 * 60_000L
