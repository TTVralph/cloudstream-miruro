package com.ttvralph.miruroapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttvralph.miruroapp.data.AniListRepository
import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeSeason
import com.ttvralph.miruroapp.data.AnimeSearchFilters
import com.ttvralph.miruroapp.data.AnimeSort
import com.ttvralph.miruroapp.data.AppSettings
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.data.SettingsStore
import com.ttvralph.miruroapp.data.SourceResolution
import com.ttvralph.miruroapp.data.ThemeMode
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.data.WatchProgressStore
import com.ttvralph.miruroapp.data.WatchlistEntry
import com.ttvralph.miruroapp.data.WatchlistSort
import com.ttvralph.miruroapp.data.WatchlistStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_DETAILS_CACHE_SIZE = 50
private const val MAX_ITEM_CACHE_SIZE = 500
private const val MAX_METADATA_QUEUE_SIZE = 20

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
    private val pendingMetadataIds = linkedSetOf<Int>()
    private val _itemMetadataVersion = MutableStateFlow(0)
    val itemMetadataVersion: StateFlow<Int> = _itemMetadataVersion.asStateFlow()

    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var moviesJob: Job? = null
    private var seriesJob: Job? = null
    private var detailsJob: Job? = null
    private val seasonJobs = mutableMapOf<Int, Job>()
    private var ensureSeasonJob: Job? = null
    private var activeDetailsId: Int? = null
    private var genreJob: Job? = null
    private var playbackJob: Job? = null
    private var metadataJob: Job? = null
    @Volatile private var pendingPreferredProvider: String? = null

    private val _homeRows = MutableStateFlow<UiState<List<HomeRow>>>(UiState.Loading)
    val homeRows: StateFlow<UiState<List<HomeRow>>> = _homeRows.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<List<AnimeItem>>?>(null)
    private val searchHistoryStore = com.ttvralph.miruroapp.data.SearchHistoryStore(application)
    val recentSearches: StateFlow<List<String>> =
        searchHistoryStore.recentSearches.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    val searchResults: StateFlow<UiState<List<AnimeItem>>?> = _searchResults.asStateFlow()

    private val _details = MutableStateFlow<UiState<AnimeDetails>>(UiState.Loading)
    val details: StateFlow<UiState<AnimeDetails>> = _details.asStateFlow()

    private val _seasonLoading = MutableStateFlow<Set<Int>>(emptySet())
    val seasonLoading: StateFlow<Set<Int>> = _seasonLoading.asStateFlow()
    private val _seasonErrors = MutableStateFlow<Map<Int, String>>(emptyMap())
    val seasonErrors: StateFlow<Map<Int, String>> = _seasonErrors.asStateFlow()

    private val _playback = MutableStateFlow<EpisodePlaybackState?>(null)
    val playback: StateFlow<EpisodePlaybackState?> = _playback.asStateFlow()

    private val _movies = MutableStateFlow<UiState<List<AnimeItem>>>(UiState.Loading)
    val movies: StateFlow<UiState<List<AnimeItem>>> = _movies.asStateFlow()
    private val _moviesLoadingMore = MutableStateFlow(false)
    val moviesLoadingMore: StateFlow<Boolean> = _moviesLoadingMore.asStateFlow()
    private val _moviesLoadMoreError = MutableStateFlow<String?>(null)
    val moviesLoadMoreError: StateFlow<String?> = _moviesLoadMoreError.asStateFlow()

    private val _series = MutableStateFlow<UiState<List<AnimeItem>>>(UiState.Loading)
    val series: StateFlow<UiState<List<AnimeItem>>> = _series.asStateFlow()
    private val _seriesLoadingMore = MutableStateFlow(false)
    val seriesLoadingMore: StateFlow<Boolean> = _seriesLoadingMore.asStateFlow()
    private val _seriesLoadMoreError = MutableStateFlow<String?>(null)
    val seriesLoadMoreError: StateFlow<String?> = _seriesLoadMoreError.asStateFlow()

    private val _genreResults = MutableStateFlow<UiState<List<AnimeItem>>?>(null)
    private var moviesPage = 0
    private var seriesPage = 0
    val genreResults: StateFlow<UiState<List<AnimeItem>>?> = _genreResults.asStateFlow()

    val watchProgress: StateFlow<List<WatchProgress>> =
        progressStore.progress.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val favoriteIds: StateFlow<Set<Int>> =
        store.favoriteIds.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptySet()
        )

    val watchlistEntries: StateFlow<List<WatchlistEntry>> =
        store.entries.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings()
        )

    init {
        loadHome()
        viewModelScope.launch {
            delay(300L)
            loadSeries()
            loadMovies()
        }
    }

    fun loadHome() {
        homeJob?.cancel()
        val previous = (_homeRows.value as? UiState.Success)?.data
        homeJob = viewModelScope.launch {
            if (previous == null) _homeRows.value = UiState.Loading
            var lastFailure: Throwable? = null
            repeat(2) { attempt ->
                val result = runCatching { repo.homeRows() }
                if (!isActive) return@launch
                val rows = result.getOrNull().orEmpty()
                if (rows.isNotEmpty()) {
                    rememberItems(rows.flatMap { it.items })
                    _homeRows.value = UiState.Success(rows)
                    return@launch
                }
                lastFailure = result.exceptionOrNull()
                if (attempt == 0) delay(650L)
            }
            lastFailure?.let { Log.w("MiruroViewModel", "Home rows failed after retry", it) }
            _homeRows.value = previous?.let { UiState.Success(it) }
                ?: UiState.Error("Could not load home rows.")
        }
    }

    fun search(query: String) = search(AnimeSearchFilters(query = query))

    fun search(filters: AnimeSearchFilters) {
        val trimmed = filters.query.trim()
        if (
            trimmed.isEmpty() &&
            filters.genres.isEmpty() &&
            filters.format == null &&
            filters.year == null &&
            filters.status == null
        ) {
            searchJob?.cancel()
            _searchResults.value = null
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchResults.value = UiState.Loading
            val result = retryResult(attempts = 2) { repo.search(filters.copy(query = trimmed)) }
            if (!isActive) return@launch
            result
                .onSuccess {
                    if (trimmed.isNotBlank()) searchHistoryStore.add(trimmed)
                    rememberItems(it)
                    _searchResults.value = UiState.Success(it)
                }
                .onFailure {
                    _searchResults.value = UiState.Error("Search failed. Please try again.")
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = null
    }

    fun loadMovies(force: Boolean = false, nextPage: Boolean = false) {
        if (!force && !nextPage && _movies.value is UiState.Success) return
        if (nextPage && _moviesLoadingMore.value) return
        moviesJob?.cancel()
        moviesJob = viewModelScope.launch {
            val previous = (_movies.value as? UiState.Success)?.data.orEmpty()
                .takeIf { nextPage }
                .orEmpty()
            val page = if (nextPage) moviesPage + 1 else 1
            val appending = nextPage && previous.isNotEmpty()
            _moviesLoadMoreError.value = null
            if (appending) _moviesLoadingMore.value = true else _movies.value = UiState.Loading
            try {
                val result = retryResult { repo.browse("MOVIE", page) }
                if (!isActive) return@launch
                result
                    .onSuccess { items ->
                        moviesPage = page
                        rememberItems(items)
                        _movies.value = UiState.Success((previous + items).distinctBy { it.id })
                    }
                    .onFailure {
                        if (appending) {
                            _movies.value = UiState.Success(previous)
                            _moviesLoadMoreError.value = "Could not load more movies. Try again."
                        } else {
                            _movies.value = UiState.Error("Could not load movies.")
                        }
                    }
            } finally {
                if (appending) _moviesLoadingMore.value = false
            }
        }
    }

    fun loadSeries(force: Boolean = false, nextPage: Boolean = false) {
        if (!force && !nextPage && _series.value is UiState.Success) return
        if (nextPage && _seriesLoadingMore.value) return
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            val previous = (_series.value as? UiState.Success)?.data.orEmpty()
                .takeIf { nextPage }
                .orEmpty()
            val page = if (nextPage) seriesPage + 1 else 1
            val appending = nextPage && previous.isNotEmpty()
            _seriesLoadMoreError.value = null
            if (appending) _seriesLoadingMore.value = true else _series.value = UiState.Loading
            try {
                val result = retryResult { repo.browse("TV", page) }
                if (!isActive) return@launch
                result
                    .onSuccess { items ->
                        seriesPage = page
                        rememberItems(items)
                        _series.value = UiState.Success((previous + items).distinctBy { it.id })
                    }
                    .onFailure {
                        if (appending) {
                            _series.value = UiState.Success(previous)
                            _seriesLoadMoreError.value = "Could not load more anime. Try again."
                        } else {
                            _series.value = UiState.Error("Could not load series.")
                        }
                    }
            } finally {
                if (appending) _seriesLoadingMore.value = false
            }
        }
    }

    fun loadDetails(id: Int) {
        val visible = (_details.value as? UiState.Success)?.data
        if (visible?.id == id) return
        detailsCache[id]?.let {
            activeDetailsId = id
            _details.value = UiState.Success(it)
            return
        }

        if (activeDetailsId != id) {
            detailsJob?.cancel()
            ensureSeasonJob?.cancel()
            seasonJobs.values.forEach { it.cancel() }
            seasonJobs.clear()
            _seasonLoading.value = emptySet()
            _seasonErrors.value = emptyMap()
        }
        activeDetailsId = id
        detailsJob = viewModelScope.launch {
            _details.value = UiState.Loading
            val result = retryResult { repo.detailsShell(id) }
            if (!isActive || activeDetailsId != id) return@launch
            result
                .onSuccess { loaded ->
                    rememberDetails(loaded)
                    rememberItems(
                        listOf(
                            AnimeItem(
                                loaded.id,
                                loaded.title,
                                loaded.posterUrl,
                                loaded.bannerUrl,
                                com.ttvralph.miruroapp.data.AnimeType.UNKNOWN,
                                loaded.year
                            )
                        )
                    )
                    _details.value = UiState.Success(loaded)
                }
                .onFailure { error ->
                    Log.w("MiruroViewModel", "Fast details shell failed for id=$id", error)
                    _details.value = UiState.Error("Could not load details. Please retry.")
                }
        }
    }

    fun loadSeason(seasonId: Int, force: Boolean = false) {
        val root = (_details.value as? UiState.Success)?.data ?: return
        val season = root.seasons.firstOrNull { it.id == seasonId } ?: return
        if (season.episodesLoaded && !force) return
        if (seasonJobs[seasonId]?.isActive == true) return

        _seasonLoading.value = _seasonLoading.value + seasonId
        _seasonErrors.value = _seasonErrors.value - seasonId
        seasonJobs[seasonId] = viewModelScope.launch {
            val result = retryResult(attempts = 2) { repo.loadSeasonEpisodes(season) }
            if (!isActive) return@launch
            result
                .onSuccess { loaded ->
                    updateSeason(root.id, loaded)
                    _seasonLoading.value = _seasonLoading.value - seasonId
                    viewModelScope.launch {
                        val dates = repo.loadSeasonAirDates(seasonId)
                        if (dates.isNotEmpty()) {
                            val dated = loaded.copy(
                                episodes = loaded.episodes.map { episode ->
                                    episode.copy(releaseDate = dates[episode.episodeNumber])
                                }
                            )
                            updateSeason(root.id, dated)
                        }
                    }
                }
                .onFailure { error ->
                    Log.w("MiruroViewModel", "Season episodes failed for seasonId=$seasonId", error)
                    _seasonLoading.value = _seasonLoading.value - seasonId
                    _seasonErrors.value = _seasonErrors.value +
                        (seasonId to "Could not load this season's episodes. Select Retry episodes.")
                }
            seasonJobs.remove(seasonId)
        }
    }

    fun ensureSeasonLoaded(animeId: Int, seasonNumber: Int) {
        ensureSeasonJob?.cancel()
        ensureSeasonJob = viewModelScope.launch {
            activeDetailsId = animeId
            var root = detailsCache[animeId]
            if (root == null) {
                _details.value = UiState.Loading
                val shellResult = retryResult { repo.detailsShell(animeId) }
                root = shellResult.getOrElse { error ->
                    Log.w("MiruroViewModel", "Saved episode details failed for id=$animeId", error)
                    _details.value = UiState.Error("Could not load this saved episode.")
                    return@launch
                }
                rememberDetails(root)
            }

            val target = root.seasons.firstOrNull { it.seasonNumber == seasonNumber }
            if (target == null) {
                _details.value = UiState.Success(root)
                return@launch
            }
            if (target.episodesLoaded) {
                _details.value = UiState.Success(root)
                return@launch
            }

            _seasonLoading.value = _seasonLoading.value + target.id
            _seasonErrors.value = _seasonErrors.value - target.id
            _details.value = UiState.Success(root)
            val loadedResult = retryResult(attempts = 2) { repo.loadSeasonEpisodes(target) }
            loadedResult
                .onSuccess { loaded ->
                    updateSeason(animeId, loaded)
                    _seasonLoading.value = _seasonLoading.value - target.id
                    viewModelScope.launch {
                        val dates = repo.loadSeasonAirDates(target.id)
                        if (dates.isNotEmpty()) {
                            updateSeason(
                                animeId,
                                loaded.copy(
                                    episodes = loaded.episodes.map { episode ->
                                        episode.copy(releaseDate = dates[episode.episodeNumber])
                                    }
                                )
                            )
                        }
                    }
                }
                .onFailure { error ->
                    Log.w("MiruroViewModel", "Saved episode season failed for seasonId=${target.id}", error)
                    _seasonLoading.value = _seasonLoading.value - target.id
                    _seasonErrors.value = _seasonErrors.value +
                        (target.id to "Could not load this season's episodes.")
                }
        }
    }

    private fun updateSeason(rootId: Int, updated: AnimeSeason) {
        val current = detailsCache[rootId]
            ?: (_details.value as? UiState.Success)?.data?.takeIf { it.id == rootId }
            ?: return
        val merged = current.copy(
            seasons = current.seasons.map { season ->
                if (season.id == updated.id) updated else season
            }
        )
        rememberDetails(merged)
        if (activeDetailsId == rootId) _details.value = UiState.Success(merged)
    }

    private suspend fun <T> retryResult(
        attempts: Int = 3,
        block: suspend () -> T
    ): Result<T> {
        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            val result = runCatching { block() }
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
            if (attempt < attempts - 1) delay(450L * (attempt + 1))
        }
        return Result.failure(lastError ?: IllegalStateException("Request failed"))
    }

    fun cachedDetails(id: Int): AnimeDetails? = detailsCache[id]
    fun cachedItem(id: Int): AnimeItem? = itemCache[id]

    fun loadGenre(
        genres: List<String>,
        format: String? = null,
        page: Int = 1,
        sort: AnimeSort = AnimeSort.POPULARITY,
        status: String? = null,
        year: Int? = null
    ) {
        genreJob?.cancel()
        genreJob = viewModelScope.launch {
            val previous = (_genreResults.value as? UiState.Success)?.data.orEmpty()
            _genreResults.value = UiState.Loading
            val result = retryResult(attempts = 2) {
                repo.browseGenre(genres, format, page, sort, status, year)
            }
            if (!isActive) return@launch
            result
                .onSuccess { items ->
                    rememberItems(items)
                    _genreResults.value = UiState.Success(
                        if (page > 1) (previous + items).distinctBy { it.id } else items
                    )
                }
                .onFailure {
                    _genreResults.value = UiState.Error("Could not load selected genres.")
                }
        }
    }

    fun saveProgress(
        episode: AnimeEpisode,
        positionMs: Long,
        durationMs: Long,
        sourceProvider: String? = null,
        sourceLabel: String? = null
    ) {
        if (durationMs <= 0L) return
        val previous = watchProgress.value.firstOrNull {
            it.animeId == episode.anilistId &&
                it.seasonNumber == episode.seasonNumber &&
                it.episodeNumber == episode.episodeNumber &&
                it.audioType == episode.audioType
        }
        viewModelScope.launch {
            progressStore.save(
                WatchProgress(
                    animeId = episode.anilistId,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    audioType = episode.audioType,
                    positionMs = positionMs.coerceIn(0L, durationMs),
                    durationMs = durationMs,
                    updatedAtMs = System.currentTimeMillis(),
                    sourceProvider = sourceProvider ?: previous?.sourceProvider,
                    sourceLabel = sourceLabel ?: previous?.sourceLabel
                )
            )
        }
    }

    fun clearWatchProgress() {
        viewModelScope.launch { progressStore.clear() }
    }

    fun setEpisodeWatched(episode: AnimeEpisode, watched: Boolean) {
        val previous = watchProgress.value.firstOrNull {
            it.animeId == episode.anilistId &&
                it.seasonNumber == episode.seasonNumber &&
                it.episodeNumber == episode.episodeNumber &&
                it.audioType == episode.audioType
        }
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
                        updatedAtMs = System.currentTimeMillis(),
                        sourceProvider = previous?.sourceProvider,
                        sourceLabel = previous?.sourceLabel
                    )
                )
            } else {
                progressStore.delete(
                    episode.anilistId,
                    episode.seasonNumber,
                    episode.episodeNumber,
                    episode.audioType
                )
            }
        }
    }

    private fun rememberDetails(details: AnimeDetails) {
        detailsCache[details.id] = details
        trimCache(detailsCache, MAX_DETAILS_CACHE_SIZE)
    }

    private fun rememberItems(items: List<AnimeItem>) {
        if (items.isEmpty()) return
        items.forEach { itemCache[it.id] = it }
        trimCache(itemCache, MAX_ITEM_CACHE_SIZE)
        _itemMetadataVersion.value += 1
    }

    private fun <T> trimCache(cache: LinkedHashMap<Int, T>, maxSize: Int) {
        while (cache.size > maxSize) {
            cache.remove(cache.keys.first())
        }
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch {
            store.setFavorite(id, id !in favoriteIds.value, itemCache[id])
        }
    }

    fun resolvePlayback(
        episode: AnimeEpisode,
        provider: String? = null
    ) {
        val key = episode.playbackKey()
        val savedProvider = watchProgress.value.firstOrNull {
            it.animeId == episode.anilistId &&
                it.seasonNumber == episode.seasonNumber &&
                it.episodeNumber == episode.episodeNumber &&
                it.audioType == episode.audioType
        }?.sourceProvider
        val requestedProvider = provider
            ?: pendingPreferredProvider?.also { pendingPreferredProvider = null }
            ?: savedProvider
            ?: settings.value.preferredProvider
        playbackJob?.cancel()
        _playback.value = EpisodePlaybackState(key, UiState.Loading)
        playbackJob = viewModelScope.launch {
            val resolution = runCatching { repo.resolveEpisodeSource(episode, requestedProvider) }
            if (!isActive) return@launch
            val result = resolution.getOrElse { e ->
                Log.w(
                    "MiruroViewModel",
                    "resolvePlayback failed for anilistId=${episode.anilistId} ep=${episode.episodeNumber}",
                    e
                )
                SourceResolution.NotFound(e.message ?: "Unexpected error: ${e::class.simpleName}")
            }
            val state = when (result) {
                is SourceResolution.Found -> UiState.Success(result.source)
                is SourceResolution.NotFound -> UiState.Error(result.reason)
            }
            _playback.value = EpisodePlaybackState(key, state)
        }
    }

    fun clearPlayback(episode: AnimeEpisode) {
        // A disposed outgoing destination must never cancel the incoming episode's work.
        if (_playback.value?.key != episode.playbackKey()) return
        playbackJob?.cancel()
        playbackJob = null
        _playback.value = null
    }

    fun updatePreferredAudio(value: com.ttvralph.miruroapp.data.AudioType) {
        viewModelScope.launch { settingsStore.updatePreferredAudio(value) }
    }

    fun updatePreferredProvider(value: String) {
        pendingPreferredProvider = value
        viewModelScope.launch { settingsStore.updatePreferredProvider(value) }
    }

    fun updateThemeMode(value: ThemeMode) {
        viewModelScope.launch { settingsStore.updateThemeMode(value) }
    }

    fun updatePosterGridDensity(value: PosterGridDensity) {
        viewModelScope.launch { settingsStore.updatePosterGridDensity(value) }
    }

    fun updateAutoPlayNext(value: Boolean) {
        viewModelScope.launch { settingsStore.updateAutoPlayNext(value) }
    }

    fun updateResumePlayback(value: Boolean) {
        viewModelScope.launch { settingsStore.updateResumePlayback(value) }
    }

    fun updateSubtitleLanguage(value: String) {
        viewModelScope.launch { settingsStore.updateSubtitleLanguage(value) }
    }

    fun updateSubtitleStyle(value: String) {
        viewModelScope.launch { settingsStore.updateSubtitleStyle(value) }
    }

    fun updateSubtitleChoice(value: String) {
        viewModelScope.launch { settingsStore.updateSubtitleChoice(value) }
    }

    fun updateHideWatchedEpisodes(value: Boolean) {
        viewModelScope.launch { settingsStore.updateHideWatchedEpisodes(value) }
    }

    fun updateWatchlistSort(value: WatchlistSort) {
        viewModelScope.launch { settingsStore.updateWatchlistSort(value) }
    }

    fun resolveFavoriteMetadata(ids: Set<Int>) = resolveItemMetadata(ids)

    fun resolveProgressMetadata(progress: List<WatchProgress>) {
        resolveItemMetadata(progress.map { it.animeId }.toSet())
    }

    private fun resolveItemMetadata(ids: Set<Int>) {
        ids.asSequence()
            .filter { it !in itemCache && it !in pendingMetadataIds }
            .take(MAX_METADATA_QUEUE_SIZE - pendingMetadataIds.size)
            .forEach { pendingMetadataIds += it }

        if (metadataJob?.isActive == true || pendingMetadataIds.isEmpty()) return
        metadataJob = viewModelScope.launch {
            while (isActive && pendingMetadataIds.isNotEmpty()) {
                val id = pendingMetadataIds.first()
                pendingMetadataIds.remove(id)
                retryResult(attempts = 2) { repo.detailsShell(id) }.onSuccess { details ->
                    rememberDetails(details)
                    rememberItems(
                        listOf(
                            AnimeItem(
                                details.id,
                                details.title,
                                details.posterUrl,
                                details.bannerUrl,
                                com.ttvralph.miruroapp.data.AnimeType.UNKNOWN,
                                details.year
                            )
                        )
                    )
                }
                delay(100L)
            }
        }
    }
}
