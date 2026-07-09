package com.ttvralph.miruroapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttvralph.miruroapp.data.AniListRepository
import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.SourceResolution
import com.ttvralph.miruroapp.data.WatchlistStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MiruroViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AniListRepository()
    private val store = WatchlistStore(application)
    private val detailsCache = linkedMapOf<Int, AnimeDetails>()

    private val _homeRows = MutableStateFlow<UiState<List<HomeRow>>>(UiState.Loading)
    val homeRows: StateFlow<UiState<List<HomeRow>>> = _homeRows.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<List<AnimeItem>>?>(null)
    val searchResults: StateFlow<UiState<List<AnimeItem>>?> = _searchResults.asStateFlow()

    private val _details = MutableStateFlow<UiState<AnimeDetails>>(UiState.Loading)
    val details: StateFlow<UiState<AnimeDetails>> = _details.asStateFlow()

    private val _playback = MutableStateFlow<UiState<PlaybackSource>?>(null)
    val playback: StateFlow<UiState<PlaybackSource>?> = _playback.asStateFlow()

    val favoriteIds: StateFlow<Set<Int>> =
        store.favoriteIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            _homeRows.value = UiState.Loading
            runCatching { repo.homeRows() }
                .onSuccess { rows ->
                    _homeRows.value = if (rows.isEmpty()) UiState.Error("Home rows are empty.") else UiState.Success(rows)
                }
                .onFailure { _homeRows.value = UiState.Error("Could not load home rows.") }
        }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = null
            return
        }
        viewModelScope.launch {
            _searchResults.value = UiState.Loading
            runCatching { repo.search(trimmed) }
                .onSuccess { _searchResults.value = UiState.Success(it) }
                .onFailure { _searchResults.value = UiState.Error("Search failed. Please try again.") }
        }
    }

    fun clearSearch() {
        _searchResults.value = null
    }

    fun loadDetails(id: Int) {
        detailsCache[id]?.let { _details.value = UiState.Success(it); return }
        viewModelScope.launch {
            _details.value = UiState.Loading
            runCatching { repo.details(id) }
                .onSuccess { detailsCache[id] = it; _details.value = UiState.Success(it) }
                .onFailure { _details.value = UiState.Error("Could not load details.") }
        }
    }

    fun cachedDetails(id: Int): AnimeDetails? = detailsCache[id]

    fun toggleFavorite(id: Int) {
        viewModelScope.launch { store.setFavorite(id, id !in favoriteIds.value) }
    }

    fun resolvePlayback(episode: AnimeEpisode) {
        viewModelScope.launch {
            _playback.value = UiState.Loading
            val result = runCatching { repo.resolveEpisodeSource(episode) }
                .getOrElse { e ->
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
        _playback.value = null
    }
}
