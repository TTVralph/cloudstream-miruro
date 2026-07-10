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
fun ReliableBrowseScreen(
    title: String,
    format: String,
    viewModel: MiruroViewModel,
    onOpenDetails: (Int) -> Unit
) {
    val moviesState by viewModel.movies.collectAsState()
    val seriesState by viewModel.series.collectAsState()
    val state = if (format == "MOVIE") moviesState else seriesState
    val settings by viewModel.settings.collectAsState()
    LaunchedEffect(format) {
        if (format == "MOVIE") viewModel.loadMovies() else viewModel.loadSeries()
    }
    Column(Modifier.fillMaxSize()) {
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))
        when (val current = state) {
            is UiState.Loading -> LoadingState("Loading $title…")
            is UiState.Error -> ErrorState(current.message) {
                if (format == "MOVIE") viewModel.loadMovies(force = true) else viewModel.loadSeries(force = true)
            }
            is UiState.Success -> {
                val visibleItems = if (format == "TV") current.data.collapseSeasonEntries() else current.data
                if (visibleItems.isEmpty()) {
                    StateMessage("Nothing found.")
                } else {
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
                        gridItems(visibleItems, key = { it.id }) { anime ->
                            PosterCard(anime) { onOpenDetails(anime.id) }
                        }
                        item {
                            SecondaryButton("Load more", Modifier.width(180.dp)) {
                                if (format == "MOVIE") viewModel.loadMovies(nextPage = true) else viewModel.loadSeries(nextPage = true)
                            }
                        }
                    }
                }
            }
        }
    }
}
