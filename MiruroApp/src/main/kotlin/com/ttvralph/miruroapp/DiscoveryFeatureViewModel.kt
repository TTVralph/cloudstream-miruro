package com.ttvralph.miruroapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.DiscoveryMode
import com.ttvralph.miruroapp.data.DiscoveryPick
import com.ttvralph.miruroapp.data.DiscoveryRepository
import com.ttvralph.miruroapp.data.DiscoverySearchFilters
import com.ttvralph.miruroapp.data.DiscoveryTitleInfo
import com.ttvralph.miruroapp.data.StudioOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DiscoveryFeatureViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiscoveryRepository()

    private val _searchResults = MutableStateFlow<UiState<List<AnimeItem>>?>(null)
    val searchResults: StateFlow<UiState<List<AnimeItem>>?> = _searchResults.asStateFlow()

    private val _studios = MutableStateFlow<List<StudioOption>>(emptyList())
    val studios: StateFlow<List<StudioOption>> = _studios.asStateFlow()

    private val _pick = MutableStateFlow<UiState<DiscoveryPick>?>(null)
    val pick: StateFlow<UiState<DiscoveryPick>?> = _pick.asStateFlow()

    private val _titleInfo = MutableStateFlow<UiState<DiscoveryTitleInfo>?>(null)
    val titleInfo: StateFlow<UiState<DiscoveryTitleInfo>?> = _titleInfo.asStateFlow()

    private var searchJob: Job? = null
    private var studiosJob: Job? = null
    private var pickJob: Job? = null
    private var titleJob: Job? = null

    fun loadStudios() {
        if (_studios.value.isNotEmpty() || studiosJob?.isActive == true) return
        studiosJob = viewModelScope.launch {
            try {
                val values = repository.studioOptions()
                if (isActive) _studios.value = values
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Search remains usable without studio suggestions.
            }
        }
    }

    fun search(filters: DiscoverySearchFilters, favoriteIds: Set<Int>) {
        searchJob?.cancel()
        if (!filters.hasCriteria) {
            _searchResults.value = null
            return
        }
        searchJob = viewModelScope.launch {
            _searchResults.value = UiState.Loading
            try {
                val values = repository.search(filters, favoriteIds)
                if (isActive) _searchResults.value = UiState.Success(values)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isActive) {
                    _searchResults.value = UiState.Error(
                        if (filters.dubbedOnly) {
                            "Search failed while checking dubbed availability. Try again or turn off Dubbed Only."
                        } else {
                            "Advanced search failed. Please try again."
                        }
                    )
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = null
    }

    fun pick(mode: DiscoveryMode, excludedIds: Set<Int>) {
        if (mode == DiscoveryMode.CONTINUE_SOMETHING) return
        pickJob?.cancel()
        pickJob = viewModelScope.launch {
            _pick.value = UiState.Loading
            try {
                val value = repository.pick(mode, excludedIds)
                if (isActive) _pick.value = UiState.Success(value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isActive) _pick.value = UiState.Error("Could not choose a title right now.")
            }
        }
    }

    fun setLocalPick(value: DiscoveryPick) {
        pickJob?.cancel()
        _pick.value = UiState.Success(value)
    }

    fun clearPick() {
        pickJob?.cancel()
        _pick.value = null
    }

    fun loadTitleInfo(animeId: Int) {
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            _titleInfo.value = UiState.Loading
            try {
                val value = repository.titleInfo(animeId)
                if (isActive) _titleInfo.value = UiState.Success(value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isActive) _titleInfo.value = UiState.Error("Could not load the full anime guide right now.")
            }
        }
    }
}
