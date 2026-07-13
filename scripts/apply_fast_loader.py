from pathlib import Path


def replace(path: str, old: str, new: str, count: int = 1):
    p = Path(path)
    text = p.read_text()
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:180]!r}")
    p.write_text(text.replace(old, new, count))


miruro = "MiruroApp/src/main/kotlin/com/ttvralph/miruroapp/data/MiruroRepository.kt"
replace(
    miruro,
    '''                        if (title != null || thumbnail != null) {
                            val current = metadataByEpisode[number]
                            metadataByEpisode[number] = EpisodeMetadata(
                                title = current?.title ?: title,
                                thumbnailUrl = current?.thumbnailUrl ?: thumbnail
                            )
                        }''',
    '''                        val synopsis = listOf("description", "synopsis", "overview", "summary", "desc")
                            .firstNotNullOfOrNull { key ->
                                episode.get(key)?.asText(null)
                                    ?: episode.path("metadata").get(key)?.asText(null)
                                    ?: episode.path("meta").get(key)?.asText(null)
                            }
                            ?.replace(Regex("(?i)<br\\s*/?>"), "\\n")
                            ?.replace(Regex("<[^>]*>"), "")
                            ?.replace("&amp;", "&")
                            ?.replace("&quot;", "\\\"")
                            ?.replace("&#39;", "'")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        if (title != null || thumbnail != null || synopsis != null) {
                            val current = metadataByEpisode[number]
                            metadataByEpisode[number] = EpisodeMetadata(
                                title = current?.title ?: title,
                                thumbnailUrl = current?.thumbnailUrl ?: thumbnail,
                                synopsis = current?.synopsis ?: synopsis
                            )
                        }'''
)

vm = "MiruroApp/src/main/kotlin/com/ttvralph/miruroapp/MiruroViewModel.kt"
replace(
    vm,
    "import com.ttvralph.miruroapp.data.AnimeItem\n",
    "import com.ttvralph.miruroapp.data.AnimeItem\nimport com.ttvralph.miruroapp.data.AnimeSeason\n"
)
replace(
    vm,
    '''    private var detailsJob: Job? = null
    private var genreJob: Job? = null''',
    '''    private var detailsJob: Job? = null
    private val seasonJobs = mutableMapOf<Int, Job>()
    private var ensureSeasonJob: Job? = null
    private var activeDetailsId: Int? = null
    private var genreJob: Job? = null'''
)
replace(
    vm,
    '''    private val _details = MutableStateFlow<UiState<AnimeDetails>>(UiState.Loading)
    val details: StateFlow<UiState<AnimeDetails>> = _details.asStateFlow()

    private val _playback''',
    '''    private val _details = MutableStateFlow<UiState<AnimeDetails>>(UiState.Loading)
    val details: StateFlow<UiState<AnimeDetails>> = _details.asStateFlow()

    private val _seasonLoading = MutableStateFlow<Set<Int>>(emptySet())
    val seasonLoading: StateFlow<Set<Int>> = _seasonLoading.asStateFlow()
    private val _seasonErrors = MutableStateFlow<Map<Int, String>>(emptyMap())
    val seasonErrors: StateFlow<Map<Int, String>> = _seasonErrors.asStateFlow()

    private val _playback'''
)
replace(
    vm,
    '''    init {
        loadHome()
    }''',
    '''    init {
        loadHome()
        viewModelScope.launch {
            delay(300L)
            loadSeries()
            loadMovies()
        }
    }'''
)
replace(vm, '            val result = runCatching { repo.search(filters.copy(query = trimmed)) }', '            val result = retryResult(attempts = 2) { repo.search(filters.copy(query = trimmed)) }')
replace(vm, '            val result = runCatching { repo.browse("MOVIE", page) }', '            val result = retryResult { repo.browse("MOVIE", page) }')
replace(vm, '            val result = runCatching { repo.browse("TV", page) }', '            val result = retryResult { repo.browse("TV", page) }')

replace(
    vm,
    '''    fun loadDetails(id: Int) {
        detailsJob?.cancel()
        detailsCache[id]?.let {
            _details.value = UiState.Success(it)
            return
        }
        detailsJob = viewModelScope.launch {
            _details.value = UiState.Loading
            val result = runCatching { repo.details(id) }
            if (!isActive) return@launch
            result
                .onSuccess {
                    rememberDetails(it)
                    rememberItems(
                        listOf(
                            AnimeItem(
                                it.id,
                                it.title,
                                it.posterUrl,
                                it.bannerUrl,
                                com.ttvralph.miruroapp.data.AnimeType.UNKNOWN,
                                it.year
                            )
                        )
                    )
                    _details.value = UiState.Success(it)
                }
                .onFailure { _details.value = UiState.Error("Could not load details.") }
        }
    }
''',
    '''    fun loadDetails(id: Int) {
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
'''
)
replace(
    vm,
    '''            val result = runCatching {
                repo.browseGenre(genres, format, page, sort, status, year)
            }''',
    '''            val result = retryResult(attempts = 2) {
                repo.browseGenre(genres, format, page, sort, status, year)
            }'''
)
replace(vm, '                runCatching { repo.details(id) }.onSuccess { details ->', '                retryResult(attempts = 2) { repo.detailsShell(id) }.onSuccess { details ->')

main = "MiruroApp/src/main/kotlin/com/ttvralph/miruroapp/MainActivity.kt"
replace(
    main,
    '''    val detailsState by viewModel.details.collectAsState()
    val metadataVersion by viewModel.itemMetadataVersion.collectAsState()
    var requestSettled''',
    '''    val detailsState by viewModel.details.collectAsState()
    val metadataVersion by viewModel.itemMetadataVersion.collectAsState()
    val seasonLoading by viewModel.seasonLoading.collectAsState()
    val seasonErrors by viewModel.seasonErrors.collectAsState()
    var requestSettled'''
)
replace(
    main,
    '''        if (findEpisode(viewModel, animeId, season, episodeNumber, audio) == null) {
            viewModel.loadDetails(animeId)
            delay(80L)
        }
        requestSettled = true''',
    '''        if (findEpisode(viewModel, animeId, season, episodeNumber, audio) == null) {
            viewModel.ensureSeasonLoaded(animeId, season)
            delay(120L)
        }
        requestSettled = true'''
)
replace(
    main,
    '''    val detailsAvailable = remember(animeId, detailsState, metadataVersion) {
        viewModel.cachedDetails(animeId) != null
    }

    if (episode == null) BackHandler(onBack = onBack)

    when {
        episode != null -> {''',
    '''    val detailsAvailable = remember(animeId, detailsState, metadataVersion) {
        viewModel.cachedDetails(animeId) != null
    }
    val targetSeasonId = remember(animeId, season, detailsState, metadataVersion) {
        viewModel.cachedDetails(animeId)
            ?.seasons
            ?.firstOrNull { it.seasonNumber == season }
            ?.id
    }
    val targetSeasonLoading = targetSeasonId?.let { it in seasonLoading } == true
    val targetSeasonError = targetSeasonId?.let { seasonErrors[it] }

    if (episode == null) BackHandler(onBack = onBack)

    when {
        episode != null -> {'''
)
replace(
    main,
    '''        detailsAvailable -> StateMessage(
            "This saved episode is no longer available. Press Back to choose another episode."
        )
        !requestSettled || detailsState is UiState.Loading -> LoadingState("Loading saved episode…")
        detailsState is UiState.Error -> ErrorState("Could not load this saved episode.") {
            viewModel.loadDetails(animeId)
        }
        else -> LoadingState("Loading saved episode…")''',
    '''        !requestSettled || detailsState is UiState.Loading || targetSeasonLoading ->
            LoadingState("Loading saved episode…")
        targetSeasonError != null -> ErrorState(targetSeasonError) {
            viewModel.ensureSeasonLoaded(animeId, season)
        }
        detailsState is UiState.Error -> ErrorState("Could not load this saved episode.") {
            viewModel.ensureSeasonLoaded(animeId, season)
        }
        detailsAvailable -> StateMessage(
            "This saved episode is no longer available. Press Back to choose another episode."
        )
        else -> LoadingState("Loading saved episode…")'''
)
