package com.ttvralph.miruroapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttvralph.miruroapp.data.AniListRepository
import com.ttvralph.miruroapp.data.AnimeSearchFilters
import com.ttvralph.miruroapp.data.AnimeSort
import com.ttvralph.miruroapp.data.AppSettings
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.data.SettingsStore
import com.ttvralph.miruroapp.data.ThemeMode
import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.SourceResolution
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.data.WatchProgressStore
import com.ttvralph.miruroapp.data.WatchlistEntry
import com.ttvralph.miruroapp.data.WatchlistStore
import com.ttvralph.miruroapp.data.WatchlistSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_DETAILS_CACHE_SIZE = 50
private const val MAX_ITEM_CACHE_SIZE = 500

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MiruroViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AniListRepository()
    private val store = WatchlistStore(application)
    private val progressStore = WatchProgressStore(application)
    private val settingsStore = SettingsStore(application)
    private val detailsCache = linkedMapOf<Int, AnimeDetails>()
    private val itemCache = linkedMapOf<Int, AnimeItem>()
    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var moviesJob: Job? = null
    private var seriesJob: Job? = null
    private var detailsJob: Job? = null
    private var genreJob: Job? = null
    private var playbackJob: Job? = null

    private val _homeRows = MutableStateFlow<UiState<List<HomeRow>>>(UiState.Loading)
    val homeRows: StateFlow<UiState<List<HomeRow>>> = _homeRows.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<List<AnimeItem>>?>(null)
    private val searchHistoryStore = com.ttvralph.miruroapp.data.SearchHistoryStore(application)
    val recentSearches: StateFlow<List<String>> =
        searchHistoryStore.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val searchResults: StateFlow<UiState<List<AnimeItem>>?> = _searchResults.asStateFlow()

    private val _details = MutableStateFlow<UiState<AnimeDetails>>(UiState.Loading)
    val details: StateFlow<UiState<AnimeDetails>> = _details.asStateFlow()

    private val _playback = MutableStateFlow<UiState<PlaybackSource>?>(null)
    val playback: StateFlow<UiState<PlaybackSource>?> = _playback.asStateFlow()

    private val _movies = MutableStateFlow<UiState<List<AnimeItem>>>(UiState.Loading)
    val movies: StateFlow<UiState<List<AnimeItem>>> = _movies.asStateFlow()

    private val _series = MutableStateFlow<UiState<List<AnimeItem>>>(UiState.Loading)
    val series: StateFlow<UiState<List<AnimeItem>>> = _series.asStateFlow()

    private val _genreResults = MutableStateFlow<UiState<List<AnimeItem>>?>(null)
    private var moviesPage = 0
    private var seriesPage = 0
    val genreResults: StateFlow<UiState<List<AnimeItem>>?> = _genreResults.asStateFlow()

    val watchProgress: StateFlow<List<WatchProgress>> =
        progressStore.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<Int>> =
        store.favoriteIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val watchlistEntries: StateFlow<List<WatchlistEntry>> =
        store.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    init {
        loadHome()
    }

    fun loadHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _homeRows.value = UiState.Loading
            val result = runCatching { repo.homeRows() }
            if (!isActive) return@launch
            result
                .onSuccess { rows ->
                    rememberItems(rows.flatMap { it.items })
                    _homeRows.value = if (rows.isEmpty()) UiState.Error("Home rows are empty.") else UiState.Success(rows)
                }
                .onFailure { _homeRows.value = UiState.Error("Could not load home rows.") }
        }
    }

    fun search(query: String) = search(AnimeSearchFilters(query = query))

    fun search(filters: AnimeSearchFilters) {
        val trimmed = filters.query.trim()
        if (trimmed.isEmpty() && filters.genres.isEmpty() && filters.format == null && filters.year == null && filters.status == null) {
            searchJob?.cancel()
            _searchResults.value = null
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchResults.value = UiState.Loading
            val result = runCatching { repo.search(filters.copy(query = trimmed)) }
            if (!isActive) return@launch
            result
                .onSuccess {
                    if (trimmed.isNotBlank()) searchHistoryStore.add(trimmed)
                    rememberItems(it); _searchResults.value = UiState.Success(it)
                }
                .onFailure { _searchResults.value = UiState.Error("Search failed. Please try again.") }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = null
    }

    fun loadMovies(force: Boolean = false, nextPage: Boolean = false) {
        if (!force && !nextPage && _movies.value is UiState.Success) return
        moviesJob?.cancel()
        moviesJob = viewModelScope.launch {
            val previous = (_movies.value as? UiState.Success)?.data.orEmpty().takeIf { nextPage }.orEmpty()
            val page = if (nextPage) moviesPage + 1 else 1
            _movies.value = UiState.Loading
            val result = runCatching { repo.browse("MOVIE", page) }
            if (!isActive) return@launch
            result
                .onSuccess { items ->
                    moviesPage = page
                    rememberItems(items)
                    _movies.value = UiState.Success((previous + items).distinctBy { it.id })
                }
                .onFailure { _movies.value = UiState.Error("Could not load movies.") }
        }
    }

    fun loadSeries(force: Boolean = false, nextPage: Boolean = false) {
        if (!force && !nextPage && _series.value is UiState.Success) return
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            val previous = (_series.value as? UiState.Success)?.data.orEmpty().takeIf { nextPage }.orEmpty()
            val page = if (nextPage) seriesPage + 1 else 1
            _series.value = UiState.Loading
            val result = runCatching { repo.browse("TV", page) }
            if (!isActive) return@launch
            result
                .onSuccess { items ->
                    seriesPage = page
                    rememberItems(items)
                    _series.value = UiState.Success((previous + items).distinctBy { it.id })
                }
                .onFailure { _series.value = UiState.Error("Could not load series.") }
        }
    }

    fun loadDetails(id: Int) {
        detailsJob?.cancel()
        detailsCache[id]?.let { _details.value = UiState.Success(it); return }
        detailsJob = viewModelScope.launch {
            _details.value = UiState.Loading
            val result = runCatching { repo.details(id) }
            if (!isActive) return@launch
            result
                .onSuccess {
                    rememberDetails(it)
                    rememberItems(listOf(AnimeItem(it.id, it.title, it.posterUrl, it.bannerUrl, com.ttvralph.miruroapp.data.AnimeType.UNKNOWN, it.year)))
                    _details.value = UiState.Success(it)
                }
                .onFailure { _details.value = UiState.Error("Could not load details.") }
        }
    }

    fun cachedDetails(id: Int): AnimeDetails? = detailsCache[id]
    fun cachedItem(id: Int): AnimeItem? = itemCache[id]

    fun loadGenre(genres: List<String>, format: String? = null, page: Int = 1, sort: AnimeSort = AnimeSort.POPULARITY, status: String? = null, year: Int? = null) {
        genreJob?.cancel()
        genreJob = viewModelScope.launch {
            val previous = (_genreResults.value as? UiState.Success)?.data.orEmpty()
            _genreResults.value = UiState.Loading
            val result = runCatching { repo.browseGenre(genres, format, page, sort, status, year) }
            if (!isActive) return@launch
            result
                .onSuccess { items ->
                    rememberItems(items)
                    _genreResults.value = UiState.Success(if (page > 1) (previous + items).distinctBy { it.id } else items)
                }
                .onFailure { _genreResults.value = UiState.Error("Could not load selected genres.") }
        }
    }

    fun saveProgress(episode: AnimeEpisode, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        viewModelScope.launch {
            progressStore.save(
                WatchProgress(
                    animeId = episode.anilistId,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    audioType = episode.audioType,
                    positionMs = positionMs.coerceIn(0L, durationMs),
                    durationMs = durationMs,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearWatchProgress() { viewModelScope.launch { progressStore.clear() } }

    fun setEpisodeWatched(episode: AnimeEpisode, watched: Boolean) {
        viewModelScope.launch {
            if (watched) {
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
            } else {
                progressStore.delete(episode.anilistId, episode.seasonNumber, episode.episodeNumber, episode.audioType)
            }
        }
    }

    private fun rememberDetails(details: AnimeDetails) {
        detailsCache[details.id] = details
        trimCache(detailsCache, MAX_DETAILS_CACHE_SIZE)
    }

    private fun rememberItems(items: List<AnimeItem>) {
        items.forEach { itemCache[it.id] = it }
        trimCache(itemCache, MAX_ITEM_CACHE_SIZE)
    }

    private fun <T> trimCache(cache: LinkedHashMap<Int, T>, maxSize: Int) {
        while (cache.size > maxSize) {
            cache.remove(cache.keys.first())
        }
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch { store.setFavorite(id, id !in favoriteIds.value, itemCache[id]) }
    }

    fun resolvePlayback(episode: AnimeEpisode, provider: String? = settings.value.preferredProvider) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            _playback.value = UiState.Loading
            val resolution = runCatching { repo.resolveEpisodeSource(episode, provider) }
            if (!isActive) return@launch
            val result = resolution.getOrElse { e ->
                Log.w("MiruroViewModel", "resolvePlayback failed for anilistId=${episode.anilistId} ep=${episode.episodeNumber}", e)
                SourceResolution.NotFound(e.message ?: "Unexpected error: ${e::class.simpleName}")
            }
            _playback.value = when (result) {
                is SourceResolution.Found -> UiState.Success(result.source)
                is SourceResolution.NotFound -> UiState.Error(result.reason)
            }
        }
    }

    fun clearPlayback() {
        playbackJob?.cancel()
        _playback.value = null
    }

    fun updatePreferredAudio(value: com.ttvralph.miruroapp.data.AudioType) { viewModelScope.launch { settingsStore.updatePreferredAudio(value) } }
    fun updatePreferredProvider(value: String) { viewModelScope.launch { settingsStore.updatePreferredProvider(value) } }
    fun updateThemeMode(value: ThemeMode) { viewModelScope.launch { settingsStore.updateThemeMode(value) } }
    fun updatePosterGridDensity(value: PosterGridDensity) { viewModelScope.launch { settingsStore.updatePosterGridDensity(value) } }
    fun updateAutoPlayNext(value: Boolean) { viewModelScope.launch { settingsStore.updateAutoPlayNext(value) } }
    fun updateResumePlayback(value: Boolean) { viewModelScope.launch { settingsStore.updateResumePlayback(value) } }
    fun updateSubtitleLanguage(value: String) { viewModelScope.launch { settingsStore.updateSubtitleLanguage(value) } }
    fun updateSubtitleStyle(value: String) { viewModelScope.launch { settingsStore.updateSubtitleStyle(value) } }
    fun updateSubtitleChoice(value: String) { viewModelScope.launch { settingsStore.updateSubtitleChoice(value) } }
    fun updateHideWatchedEpisodes(value: Boolean) { viewModelScope.launch { settingsStore.updateHideWatchedEpisodes(value) } }
    fun updateWatchlistSort(value: WatchlistSort) { viewModelScope.launch { settingsStore.updateWatchlistSort(value) } }

    fun resolveFavoriteMetadata(ids: Set<Int>) {
        ids.filter { it !in itemCache }.forEach { id ->
            viewModelScope.launch {
                runCatching { repo.details(id) }.onSuccess { details ->
                    rememberDetails(details)
                    rememberItems(listOf(AnimeItem(details.id, details.title, details.posterUrl, details.bannerUrl, com.ttvralph.miruroapp.data.AnimeType.UNKNOWN, details.year)))
                }
            }
        }
    }
}
