package com.ttvralph.miruroapp

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeSort
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ReliableHomeScreen(
    viewModel: MiruroViewModel,
    onHome: () -> Unit,
    onAnime: () -> Unit,
    onMovies: () -> Unit,
    onDiscover: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit
) {
    val state by viewModel.homeRows.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val metadataVersion by viewModel.itemMetadataVersion.collectAsState()
    val context = LocalContext.current
    val cache = remember(context) { HomeCatalogueCache(context.applicationContext) }
    var savedRows by remember(cache) { mutableStateOf(cache.read().collapseHomeFranchises()) }
    var automaticRetries by remember { mutableIntStateOf(0) }

    val networkRows = (state as? UiState.Success<List<HomeRow>>)?.data?.collapseHomeFranchises()
    val rows = networkRows?.takeIf { it.isNotEmpty() } ?: savedRows

    LaunchedEffect(networkRows) {
        if (!networkRows.isNullOrEmpty()) {
            automaticRetries = 0
            savedRows = networkRows
            cache.write(networkRows)
        }
    }
    LaunchedEffect(state) {
        if (state is UiState.Error && automaticRetries < 3) {
            val waitMs = listOf(1_500L, 3_500L, 7_000L)[automaticRetries]
            automaticRetries += 1
            delay(waitMs)
            if (viewModel.homeRows.value is UiState.Error) viewModel.loadHome()
        }
    }

    val unfinished = remember(progress, settings.preferredAudio, metadataVersion) {
        progress.filterNot { it.watched }
            .groupBy { Triple(it.animeId, it.seasonNumber, it.episodeNumber) }
            .mapNotNull { (_, versions) ->
                versions.firstOrNull { it.audioType == settings.preferredAudio }
                    ?: versions.maxByOrNull { it.updatedAtMs }
            }
            .sortedByDescending { it.updatedAtMs }
            .take(20)
    }
    LaunchedEffect(progress.map { it.animeId }.toSet()) {
        viewModel.resolveProgressMetadata(progress)
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

    val initial = rows.asSequence().flatMap { it.items.asSequence() }
        .firstOrNull { !it.bannerUrl.isNullOrBlank() }
        ?: rows.first().items.first()
    val resumeItems = remember(unfinished, metadataVersion) {
        unfinished.map { saved ->
            ReliableResumeItem(
                viewModel.cachedItem(saved.animeId) ?: AnimeItem(
                    saved.animeId,
                    "Anime #${saved.animeId}",
                    null,
                    null,
                    AnimeType.UNKNOWN
                ),
                saved
            )
        }
    }
    val allItems = remember(rows, resumeItems, initial) {
        (listOf(initial) + resumeItems.map { it.anime } + rows.flatMap { it.items }).distinctBy { it.id }
    }
    val listState = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    var activeRow by remember(initial.id) { mutableIntStateOf(-1) }
    var pendingHeroId by remember(initial.id) { mutableIntStateOf(initial.id) }
    var activeHeroId by remember(initial.id) { mutableIntStateOf(initial.id) }
    val activeHero = allItems.firstOrNull { it.id == activeHeroId } ?: initial
    val contentRowCount = rows.size + if (resumeItems.isNotEmpty()) 1 else 0

    LaunchedEffect(pendingHeroId) {
        delay(360L)
        activeHeroId = pendingHeroId
    }
    LaunchedEffect(activeRow, contentRowCount) {
        delay(110L)
        val target = if (activeRow < 0) 0 else (activeRow + 1).coerceIn(1, contentRowCount)
        if (listState.firstVisibleItemIndex != target) {
            runCatching { listState.scrollToItem(target) }
        }
    }
    LaunchedEffect(Unit) {
        delay(240L)
        runCatching { playFocus.requestFocus() }
    }

    val heroAlpha by animateFloatAsState(
        targetValue = if (activeRow < 0) 1f else 0f,
        animationSpec = tween(170),
        label = "reliableHeroAlpha"
    )
    val backdropDim by animateFloatAsState(
        targetValue = if (activeRow < 0) 0.26f else 0.86f,
        animationSpec = tween(190),
        label = "reliableBackdropDim"
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ReliableBackdrop(activeHero, backdropDim)
        ReliableHero(
            item = activeHero,
            inList = activeHero.id in favorites,
            alpha = heroAlpha,
            playFocus = playFocus,
            firstRowFocus = firstRowFocus,
            onFocused = { if (activeRow != -1) activeRow = -1 },
            onPlay = { onOpenDetails(activeHero.id) },
            onList = { viewModel.toggleFavorite(activeHero.id) }
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
                val row = rowNumber++
                item("reliable-resume") {
                    ReliableHomeRow("Continue Watching") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = ReliableSafeX, vertical = 12.dp)
                        ) {
                            itemsIndexed(resumeItems, key = { _, it -> it.progress.key }) { index, item ->
                                ReliableHomeCard(
                                    item = item.anime,
                                    progress = item.progress.percent,
                                    supportingText = "S${item.progress.seasonNumber} E${item.progress.episodeNumber}",
                                    focusRequester = if (index == 0) firstRowFocus else null,
                                    upFocusRequester = playFocus,
                                    onFocused = {
                                        if (activeRow != row) activeRow = row
                                        pendingHeroId = item.anime.id
                                    },
                                    onClick = { onPlayProgress(item.progress) }
                                )
                            }
                        }
                    }
                }
            }
            rows.forEach { homeRow ->
                val row = rowNumber++
                val first = row == 0
                item("reliable-row-${homeRow.title}") {
                    ReliableHomeRow(homeRow.title) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = ReliableSafeX, vertical = 12.dp)
                        ) {
                            itemsIndexed(homeRow.items, key = { _, it -> it.id }) { index, anime ->
                                ReliableHomeCard(
                                    item = anime,
                                    focusRequester = if (first && index == 0) firstRowFocus else null,
                                    upFocusRequester = if (first) playFocus else null,
                                    onFocused = {
                                        if (activeRow != row) activeRow = row
                                        pendingHeroId = anime.id
                                    },
                                    onClick = { onOpenDetails(anime.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        ReliableTopBar(
            current = "Home",
            onHome = onHome,
            onAnime = onAnime,
            onMovies = onMovies,
            onDiscover = onDiscover,
            onMyList = onMyList,
            onSearch = onSearch,
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.TopCenter).zIndex(10f)
        )
    }
}
