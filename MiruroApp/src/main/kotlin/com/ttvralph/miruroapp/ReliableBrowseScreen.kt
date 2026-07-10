package com.ttvralph.miruroapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage
import kotlinx.coroutines.delay

@Composable
fun ReliableBrowseScreen(
    title: String,
    format: String,
    viewModel: MiruroViewModel,
    onOpenDetails: (Int) -> Unit
) {
    val moviesState by viewModel.movies.collectAsState()
    val seriesState by viewModel.series.collectAsState()
    val homeState by viewModel.homeRows.collectAsState()
    val state = if (format == "MOVIE") moviesState else seriesState
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val cache = remember(context, format) {
        BrowseCatalogueCache(context.applicationContext, format)
    }
    val persistedHomeFallback = remember(context, format) {
        catalogueItemsForFormat(
            HomeCatalogueCache(context.applicationContext).read(),
            format
        )
    }
    val liveHomeFallback = remember(homeState, format) {
        val rows = (homeState as? UiState.Success<List<HomeRow>>)?.data.orEmpty()
        catalogueItemsForFormat(rows, format)
    }
    var savedItems by remember(cache, format) {
        val cached = cache.read().ifEmpty { persistedHomeFallback }
        mutableStateOf(normalizeBrowseItems(cached, format))
    }
    var automaticRetries by remember(format) { mutableIntStateOf(0) }

    val networkItems = (state as? UiState.Success<List<AnimeItem>>)
        ?.data
        ?.let { normalizeBrowseItems(it, format) }
        .orEmpty()
    val visibleItems = networkItems
        .ifEmpty { savedItems }
        .ifEmpty { liveHomeFallback }

    LaunchedEffect(format) {
        if (format == "MOVIE") viewModel.loadMovies() else viewModel.loadSeries()
    }
    LaunchedEffect(networkItems) {
        if (networkItems.isNotEmpty()) {
            automaticRetries = 0
            savedItems = networkItems
            cache.write(networkItems)
        }
    }
    LaunchedEffect(state) {
        if (state is UiState.Error && automaticRetries < 3) {
            val waitMs = listOf(1_200L, 3_000L, 6_000L)[automaticRetries]
            automaticRetries += 1
            delay(waitMs)
            if (format == "MOVIE") viewModel.loadMovies(force = true)
            else viewModel.loadSeries(force = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
        )

        if (visibleItems.isEmpty()) {
            when (val current = state) {
                is UiState.Loading -> LoadingState("Loading $title…")
                is UiState.Error -> ErrorState(current.message) {
                    automaticRetries = 0
                    if (format == "MOVIE") viewModel.loadMovies(force = true)
                    else viewModel.loadSeries(force = true)
                }
                is UiState.Success -> StateMessage("Nothing found.")
            }
            return@Column
        }

        if (state is UiState.Error) {
            Text(
                "Showing available saved titles while AniList reconnects.",
                color = MiruroColors.Subtle,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(
                when (settings.posterGridDensity) {
                    PosterGridDensity.COMPACT -> 140.dp
                    PosterGridDensity.COMFORTABLE -> 165.dp
                    PosterGridDensity.LARGE -> 210.dp
                }
            ),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 86.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(visibleItems, key = { it.id }) { anime ->
                PosterCard(anime) { onOpenDetails(anime.id) }
            }
            item {
                SecondaryButton("Load more", Modifier.width(180.dp)) {
                    if (format == "MOVIE") viewModel.loadMovies(nextPage = true)
                    else viewModel.loadSeries(nextPage = true)
                }
            }
        }
    }
}

private fun catalogueItemsForFormat(rows: List<HomeRow>, format: String): List<AnimeItem> =
    normalizeBrowseItems(
        rows.flatMap { it.items }.filter { item ->
            if (format == "MOVIE") item.type == AnimeType.MOVIE
            else item.type == AnimeType.TV
        },
        format
    )

private fun normalizeBrowseItems(items: List<AnimeItem>, format: String): List<AnimeItem> =
    if (format == "TV") items.collapseSeasonEntries()
    else items.distinctBy { it.id }
