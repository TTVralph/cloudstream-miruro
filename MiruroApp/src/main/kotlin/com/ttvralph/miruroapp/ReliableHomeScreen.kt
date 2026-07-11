package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.TitleReaction
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.StateMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOME_PROGRESS_METADATA_LIMIT = 8

@Composable
fun ReliableHomeScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit
) {
    val state by viewModel.homeRows.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val metadataVersion by viewModel.itemMetadataVersion.collectAsState()
    val resumeMetadata by ContinueWatchingMetadataStore.items.collectAsState()
    val personalizedRows by features.personalizedRows.collectAsState()
    val upcoming by features.upcoming.collectAsState()
    val profileState by features.profileState.collectAsState()
    val reactions by features.reactions.collectAsState()
    var automaticRetries by remember { mutableIntStateOf(0) }

    val rows = remember(state) {
        (state as? UiState.Success<List<HomeRow>>)
            ?.data
            ?.takeIf { it.isNotEmpty() }
            ?.let(HomePresentationRows::collapsed)
            .orEmpty()
    }

    LaunchedEffect(state) {
        if (state is UiState.Error && automaticRetries < 3) {
            val waitMs = listOf(1_500L, 3_500L, 7_000L)[automaticRetries]
            automaticRetries += 1
            delay(waitMs)
            if (viewModel.homeRows.value is UiState.Error) viewModel.loadHome()
        }
    }

    val unfinished = remember(progress, settings.preferredAudio) {
        progress.filterNot { it.watched }
            .groupBy { Triple(it.animeId, it.seasonNumber, it.episodeNumber) }
            .mapNotNull { (_, versions) ->
                versions.firstOrNull { it.audioType == settings.preferredAudio }
                    ?: versions.maxByOrNull { it.updatedAtMs }
            }
            .sortedByDescending { it.updatedAtMs }
            .take(HOME_PROGRESS_METADATA_LIMIT)
    }

    LaunchedEffect(unfinished) {
        if (unfinished.isNotEmpty()) {
            delay(300L)
            ContinueWatchingMetadataStore.resolve(unfinished.map { it.animeId }.toSet())
        }
    }

    LaunchedEffect(rows, progress, favorites, reactions, profileState.activeId) {
        if (rows.isNotEmpty()) {
            // Keep recommendation work behind the first Home frame and focus hand-off.
            delay(650L)
            features.loadHomeFeatures(rows.flatMap { it.items }, progress, favorites)
        }
    }

    if (rows.isEmpty()) {
        when (val current = state) {
            is UiState.Loading -> LoadingState("Loading AniStream…")
            is UiState.Error -> ErrorState(current.message) {
                automaticRetries = 0
                viewModel.loadHome()
            }
            is UiState.Success -> StateMessage("The home catalogue did not return any titles.")
        }
        return
    }

    val hiddenIds = remember(reactions) {
        reactions.filterValues { it == TitleReaction.DISLIKE }.keys
    }
    val featureRows = remember(personalizedRows, upcoming, hiddenIds, profileState.activeId) {
        val upcomingRow = upcoming
            .map { it.anime }
            .filterNot { it.id in hiddenIds }
            .distinctBy { it.id }
            .take(18)
            .takeIf { it.isNotEmpty() }
            ?.let { HomeRow("Upcoming for ${profileState.activeProfile.name}", it) }
        (personalizedRows + listOfNotNull(upcomingRow))
            .map { row -> row.copy(items = row.items.filterNot { it.id in hiddenIds }) }
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.title }
    }
    val displayRows = remember(rows, featureRows, hiddenIds) {
        val catalogue = rows
            .map { row -> row.copy(items = row.items.filterNot { it.id in hiddenIds }) }
            .filter { it.items.isNotEmpty() }
        catalogue + featureRows
    }

    val initial = remember(rows, hiddenIds) {
        val visible = rows.flatMap { it.items }.filterNot { it.id in hiddenIds }
        visible.firstOrNull { !it.bannerUrl.isNullOrBlank() } ?: visible.firstOrNull()
            ?: rows.asSequence().flatMap { it.items.asSequence() }.first()
    }
    val resumeItems = remember(unfinished, metadataVersion, resumeMetadata) {
        unfinished.map { saved ->
            ReliableResumeItem(
                resumeMetadata[saved.animeId]
                    ?: viewModel.cachedItem(saved.animeId)
                    ?: AnimeItem(
                        saved.animeId,
                        "Saved anime",
                        null,
                        null,
                        AnimeType.UNKNOWN
                    ),
                saved
            )
        }
    }

    val listState = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var browsingRows by rememberSaveable(initial.id) { mutableStateOf(false) }
    var movingToRows by remember(initial.id) { mutableStateOf(false) }
    var movingToHero by remember(initial.id) { mutableStateOf(false) }

    val showHero: () -> Unit = show@{
        if (!browsingRows || movingToHero) return@show
        movingToHero = true
        scope.launch {
            try {
                runCatching { listState.scrollToItem(0) }
                browsingRows = false
            } finally {
                movingToHero = false
            }
        }
    }
    val moveToFirstRow: () -> Unit = move@{
        if (movingToRows) return@move
        movingToRows = true
        scope.launch {
            try {
                browsingRows = true
                listState.scrollToItem(1)
                delay(48L)
                runCatching { firstRowFocus.requestFocus() }
            } finally {
                movingToRows = false
            }
        }
    }

    LaunchedEffect(initial.id, browsingRows) {
        if (!browsingRows) {
            delay(160L)
            runCatching { playFocus.requestFocus() }
        }
    }

    val heroAlpha = if (browsingRows) 0f else 1f
    val backdropDim = if (browsingRows) 0.90f else 0.30f

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ReliableBackdrop(initial, backdropDim)
        ReliableHero(
            item = initial,
            inList = initial.id in favorites,
            alpha = heroAlpha,
            playFocus = playFocus,
            firstRowFocus = firstRowFocus,
            onFocused = showHero,
            onMoveDown = moveToFirstRow,
            onPlay = { onOpenDetails(initial.id) },
            onList = { viewModel.toggleFavorite(initial.id) }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = ReliableNavHeight),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item("reliable-hero-space") { Spacer(Modifier.height(405.dp)) }
            var rowNumber = 0
            if (resumeItems.isNotEmpty()) {
                val first = rowNumber == 0
                rowNumber += 1
                item("reliable-resume") {
                    ReliableHomeRow("Continue Watching") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = ReliableSafeX, vertical = 12.dp)
                        ) {
                            itemsIndexed(resumeItems, key = { _, item -> item.progress.key }) { index, item ->
                                ReliableHomeCard(
                                    item = item.anime,
                                    progress = item.progress.percent,
                                    supportingText = "S${item.progress.seasonNumber} E${item.progress.episodeNumber}",
                                    focusRequester = if (first && index == 0) firstRowFocus else null,
                                    upFocusRequester = if (first) playFocus else null,
                                    onFocused = { if (!browsingRows) browsingRows = true },
                                    onClick = { onPlayProgress(item.progress) }
                                )
                            }
                        }
                    }
                }
            }
            displayRows.forEach { homeRow ->
                val first = rowNumber == 0
                rowNumber += 1
                item("reliable-row-${homeRow.title}") {
                    ReliableHomeRow(homeRow.title) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = ReliableSafeX, vertical = 12.dp)
                        ) {
                            itemsIndexed(homeRow.items, key = { _, item -> item.id }) { index, anime ->
                                ReliableHomeCard(
                                    item = anime,
                                    focusRequester = if (first && index == 0) firstRowFocus else null,
                                    upFocusRequester = if (first) playFocus else null,
                                    onFocused = { if (!browsingRows) browsingRows = true },
                                    onClick = { onOpenDetails(anime.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
