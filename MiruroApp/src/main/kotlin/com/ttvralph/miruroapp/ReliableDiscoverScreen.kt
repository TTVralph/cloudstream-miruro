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

private val ReliableGenres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror",
    "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"
)

@Composable
fun ReliableDiscoverScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var selectedGenres by remember { mutableStateOf(emptySet<String>()) }
    var format by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(AnimeSort.POPULARITY) }
    var year by remember { mutableStateOf<Int?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var dialogVisible by remember { mutableStateOf(false) }
    val state by viewModel.genreResults.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(selectedGenres, format, status, sort, year, page) {
        viewModel.loadGenre(selectedGenres.toList(), format, page, sort, status, year)
    }
    if (dialogVisible) {
        ReliableDiscoverDialog(
            selectedGenres = selectedGenres,
            format = format,
            status = status,
            sort = sort,
            year = year,
            onDismiss = { dialogVisible = false },
            onApply = { nextGenres, nextFormat, nextStatus, nextSort, nextYear ->
                selectedGenres = nextGenres
                format = nextFormat
                status = nextStatus
                sort = nextSort
                year = nextYear
                page = 1
                dialogVisible = false
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 24.dp, bottom = 14.dp)) {
            Text("Discover", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            SecondaryButton("Filters", Modifier.width(150.dp)) { dialogVisible = true }
            Spacer(Modifier.width(10.dp))
            if (selectedGenres.isNotEmpty() || format != null || status != null || year != null || sort != AnimeSort.POPULARITY) {
                SecondaryButton("Clear", Modifier.width(120.dp)) {
                    selectedGenres = emptySet()
                    format = null
                    status = null
                    sort = AnimeSort.POPULARITY
                    year = null
                    page = 1
                }
            }
        }
        val summary = buildList {
            if (selectedGenres.isNotEmpty()) add(selectedGenres.joinToString())
            format?.let { add(if (it == "TV") "Anime" else "Movies") }
            status?.let { add(it.lowercase(Locale.ROOT).replace('_', ' ')) }
            year?.let { add(it.toString()) }
            add(sort.label)
        }.joinToString(" • ")
        Text(summary, color = MiruroColors.Subtle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))

        when (val current = state) {
            null, is UiState.Loading -> LoadingState("Loading catalogue…")
            is UiState.Error -> ErrorState(current.message) {
                viewModel.loadGenre(selectedGenres.toList(), format, page, sort, status, year)
            }
            is UiState.Success -> {
                val visibleItems = current.data.collapseSeasonEntries()
                if (visibleItems.isEmpty()) {
                    StateMessage("Nothing matched these filters. Open Filters to broaden the results.")
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
                            SecondaryButton("Load page ${page + 1}", Modifier.width(190.dp)) { page += 1 }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReliableDiscoverDialog(
    selectedGenres: Set<String>,
    format: String?,
    status: String?,
    sort: AnimeSort,
    year: Int?,
    onDismiss: () -> Unit,
    onApply: (Set<String>, String?, String?, AnimeSort, Int?) -> Unit
) {
    var draftGenres by remember(selectedGenres) { mutableStateOf(selectedGenres) }
    var draftFormat by remember(format) { mutableStateOf(format) }
    var draftStatus by remember(status) { mutableStateOf(status) }
    var draftSort by remember(sort) { mutableStateOf(sort) }
    var draftYear by remember(year) { mutableStateOf(year) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discover filters", color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(
                modifier = Modifier.height(540.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ReliableDialogLabel("Type")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ReliableChoice("All", draftFormat == null) { draftFormat = null } }
                        item { ReliableChoice("Anime", draftFormat == "TV") { draftFormat = "TV" } }
                        item { ReliableChoice("Movies", draftFormat == "MOVIE") { draftFormat = "MOVIE" } }
                    }
                }
                item {
                    ReliableDialogLabel("Status")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ReliableChoice("Any", draftStatus == null) { draftStatus = null } }
                        item { ReliableChoice("Airing", draftStatus == "RELEASING") { draftStatus = "RELEASING" } }
                        item { ReliableChoice("Finished", draftStatus == "FINISHED") { draftStatus = "FINISHED" } }
                        item { ReliableChoice("Upcoming", draftStatus == "NOT_YET_RELEASED") { draftStatus = "NOT_YET_RELEASED" } }
                    }
                }
                item {
                    ReliableDialogLabel("Sort")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimeSort.values().filter { it != AnimeSort.SEARCH_MATCH }.forEach { option ->
                            item { ReliableChoice(option.label, draftSort == option) { draftSort = option } }
                        }
                    }
                }
                item {
                    ReliableDialogLabel("Year")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ReliableChoice("Any", draftYear == null) { draftYear = null } }
                        (0..5).forEach { offset ->
                            val option = currentYear - offset
                            item { ReliableChoice(option.toString(), draftYear == option) { draftYear = option } }
                        }
                    }
                }
                item { ReliableDialogLabel("Genres (${draftGenres.size} selected)") }
                items(ReliableGenres.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { genre ->
                            ReliableChoice(genre, genre in draftGenres) {
                                draftGenres = if (genre in draftGenres) draftGenres - genre else draftGenres + genre
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton("Apply", Modifier.width(130.dp)) {
                onApply(draftGenres, draftFormat, draftStatus, draftSort, draftYear)
            }
        },
        dismissButton = { SecondaryButton("Cancel", Modifier.width(130.dp), onDismiss) },
        containerColor = Color(0xFF111111)
    )
}

@Composable
private fun ReliableDialogLabel(text: String) {
    Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun ReliableChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.height(44.dp).width((text.length * 10 + 42).coerceIn(78, 175).dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent else Color.White.copy(alpha = 0.06f),
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (selected) "✓ $text" else text,
                color = if (focused) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
