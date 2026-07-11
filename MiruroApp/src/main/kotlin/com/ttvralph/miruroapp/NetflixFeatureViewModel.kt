package com.ttvralph.miruroapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.EngagementStore
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.LocalProfile
import com.ttvralph.miruroapp.data.NetflixFeatureRepository
import com.ttvralph.miruroapp.data.ProfileSession
import com.ttvralph.miruroapp.data.ProfileState
import com.ttvralph.miruroapp.data.ProfileStore
import com.ttvralph.miruroapp.data.SkipInterval
import com.ttvralph.miruroapp.data.TitleExtras
import com.ttvralph.miruroapp.data.TitleReaction
import com.ttvralph.miruroapp.data.UpcomingEpisode
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.data.WatchProgressStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NetflixFeatureViewModel(application: Application) : AndroidViewModel(application) {
    private val profilesStore = ProfileStore(application)
    private val engagement = EngagementStore(application)
    private val progressStore = WatchProgressStore(application)
    private val repository = NetflixFeatureRepository()

    val profileState: StateFlow<ProfileState> = profilesStore.state.stateIn(
        viewModelScope, SharingStarted.Eagerly, ProfileState()
    )
    val reactions: StateFlow<Map<Int, TitleReaction>> = engagement.reactions.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyMap()
    )
    val reminders: StateFlow<Set<Int>> = engagement.reminders.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptySet()
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

    fun createProfile() {
        val number = profileState.value.profiles.size + 1
        viewModelScope.launch { profilesStore.create("Profile $number") }
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

    fun loadMetadata(ids: Set<Int>) {
        val missing = ids.filterNot { it in _metadata.value }.toSet()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val items = runCatching { repository.items(missing) }.getOrDefault(emptyList())
            if (items.isNotEmpty()) _metadata.value += items.associateBy { it.id }
        }
    }

    fun loadExtras(animeId: Int) {
        extrasJob?.cancel()
        extrasJob = viewModelScope.launch {
            _extras.value = UiState.Loading
            runCatching { repository.titleExtras(animeId) }
                .onSuccess { value ->
                    val items = value.related + value.recommendations + listOfNotNull(value.nextAiring?.anime)
                    _metadata.value += items.associateBy { it.id }
                    _extras.value = UiState.Success(value)
                }
                .onFailure { _extras.value = UiState.Error("Could not load related anime right now.") }
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
        val seeds = (positive + favorites + progress.map { it.animeId } + reminders.value)
            .filter { it > 0 }.toSet()
        val signature = listOf(
            profileState.value.activeId,
            seeds.sorted().joinToString(),
            disliked.sorted().joinToString(),
            catalogue.take(20).joinToString { it.id.toString() }
        ).joinToString("|")
        if (signature == lastHomeSignature) return
        lastHomeSignature = signature
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val rows = mutableListOf<HomeRow>()
            positive.take(3).forEach { id ->
                val value = runCatching { repository.titleExtras(id) }.getOrNull() ?: return@forEach
                val source = _metadata.value[id]?.title
                    ?: catalogue.firstOrNull { it.id == id }?.title
                    ?: "a title you liked"
                val items = value.recommendations
                    .filterNot { it.id == id || it.id in disliked }
                    .take(18)
                if (items.isNotEmpty()) rows += HomeRow("Because You Liked $source", items)
            }
            if (rows.isEmpty()) {
                val items = catalogue.filterNot { it.id in disliked }
                    .sortedByDescending { it.score ?: 0 }
                    .distinctBy { it.id }
                    .take(18)
                if (items.isNotEmpty()) {
                    rows += HomeRow("Top Picks for ${profileState.value.activeProfile.name}", items)
                }
            }
            _personalizedRows.value = rows.distinctBy { it.title }.take(3)
            _upcoming.value = runCatching { repository.upcoming(seeds) }.getOrDefault(emptyList())
            val found = rows.flatMap { it.items } + _upcoming.value.map { it.anime }
            _metadata.value += found.associateBy { it.id }
        }
    }

    fun loadSkipTimes(episode: AnimeEpisode, durationMs: Long) {
        if (durationMs <= 0L) return
        val key = skipKey(episode, durationMs)
        if (key in _skipIntervals.value) return
        _skipIntervals.value += key to emptyList()
        viewModelScope.launch {
            val values = runCatching {
                repository.skipTimes(episode.anilistId, episode.episodeNumber, durationMs / 1_000.0)
            }.getOrDefault(emptyList())
            _skipIntervals.value += key to values
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

    private fun resetProfilePresentation() {
        _personalizedRows.value = emptyList()
        _upcoming.value = emptyList()
        _extras.value = null
        _skipIntervals.value = emptyMap()
        lastHomeSignature = null
    }

    private fun skipKey(episode: AnimeEpisode, durationMs: Long): String =
        "${episode.anilistId}:${episode.episodeNumber}:${durationMs / 30_000L}"
}
