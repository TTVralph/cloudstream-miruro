package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeSearchFilters
import com.ttvralph.miruroapp.data.AnimeSort
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.data.WatchlistSort
import com.ttvralph.miruroapp.ui.Badge
import com.ttvralph.miruroapp.ui.BodyText
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.GenreChip
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.RatingLabel
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.SectionTitle
import com.ttvralph.miruroapp.ui.StateMessage
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private const val EPISODE_GRID_COLUMNS = 3

@Composable
fun HomeScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit, onPlayProgress: (WatchProgress) -> Unit = { onOpenDetails(it.animeId) }) {
    val state by viewModel.homeRows.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val progressIds = remember(progress) { progress.map { it.animeId }.toSet() }
    LaunchedEffect(progressIds) { viewModel.resolveProgressMetadata(progress) }
    when (val s = state) {
        is UiState.Loading -> LoadingState("Loading home rows…")
        is UiState.Error -> ErrorState(s.message) { viewModel.loadHome() }
        is UiState.Success -> {
            val rows = s.data
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                rows.firstOrNull()?.items?.firstOrNull()?.let { hero ->
                    item {
                        HomeHero(
                            item = hero,
                            inList = hero.id in favorites,
                            onWatch = { onOpenDetails(hero.id) },
                            onToggleList = { viewModel.toggleFavorite(hero.id) }
                        )
                    }
                }
                val unfinished = progress.filter { !it.watched }.take(20)
                if (unfinished.isNotEmpty()) {
                    item { ContinueWatchingRow(unfinished, viewModel, onOpenDetails, onPlayProgress) }
                }
                items(rows, key = { it.title }) { row ->
                    PosterRow(
                        title = row.title,
                        items = row.items,
                        onClick = onOpenDetails,
                        gridDensity = settings.posterGridDensity,
                        badge = if (row.title == "Trending Now") "HOT" else null
                    )
                }
                item { TopButton { scope.launch { listState.animateScrollToItem(0) } } }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(progress: List<WatchProgress>, viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit, onPlayProgress: (WatchProgress) -> Unit) {
    Column {
        SectionTitle("Continue Watching", "RESUME")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
        ) {
            items(progress, key = { it.key }) { item ->
                val anime = viewModel.cachedItem(item.animeId)
                FocusableSurface(onClick = { onPlayProgress(item) }, modifier = Modifier.width(250.dp).height(150.dp)) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(anime?.title ?: "Anime #${item.animeId}", color = MiruroColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Column {
                            Text("Resume S${item.seasonNumber} E${item.episodeNumber}", color = MiruroColors.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${formatTimeShort(item.positionMs)} / ${formatTimeShort(item.durationMs)} • ${(item.percent * 100).toInt()}%", color = MiruroColors.Subtle, fontSize = 12.sp)
                            Text("Open details", color = MiruroColors.Subtle, fontSize = 11.sp, modifier = Modifier.clickable { onOpenDetails(item.animeId) }.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(item: AnimeItem, inList: Boolean, onWatch: () -> Unit, onToggleList: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiruroColors.Card)
    ) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to MiruroColors.Background
                        )
                    )
                )
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(36.dp).width(640.dp)) {
            Badge("TRENDING", container = MiruroColors.Accent2, content = Color.Black)
            Spacer(Modifier.height(14.dp))
            Text(
                item.title.uppercase(Locale.ROOT),
                color = MiruroColors.Text,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 46.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.score?.let {
                    RatingLabel(String.format(Locale.US, "%.1f", it / 10f))
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    listOfNotNull(item.year?.toString(), item.type.name).joinToString("   •   "),
                    color = MiruroColors.Subtle,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(24.dp))
            Row {
                PrimaryButton("Watch Now", modifier = Modifier.width(200.dp), onClick = onWatch)
                Spacer(Modifier.width(16.dp))
                SecondaryButton(if (inList) "In My List" else "+ Add to List", modifier = Modifier.width(200.dp), onClick = onToggleList)
            }
        }
    }
}

@Composable
private fun PosterRow(
    title: String,
    items: List<AnimeItem>,
    onClick: (Int) -> Unit,
    badge: String? = null,
    gridDensity: PosterGridDensity = PosterGridDensity.COMFORTABLE
) {
    Column {
        SectionTitle(title, badge)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
        ) {
            items(items, key = { it.id }) { anime ->
                PosterCard(anime, width = posterRowWidth(gridDensity)) { onClick(anime.id) }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<String?>(null) }
    var selectedGenres by remember { mutableStateOf(setOf<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(AnimeSort.SEARCH_MATCH) }
    var yearText by remember { mutableStateOf("") }
    val state by viewModel.searchResults.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var advancedVisible by remember { mutableStateOf(false) }
    var genrePickerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(query, format, selectedGenres, status, sort, yearText) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() && selectedGenres.isEmpty() && format == null && status == null && yearText.isBlank() && sort == AnimeSort.SEARCH_MATCH) {
            viewModel.clearSearch()
        } else {
            delay(350)
            viewModel.search(AnimeSearchFilters(trimmed, format, yearText.toIntOrNull(), selectedGenres.toList(), status, sort))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search anime by title", color = MiruroColors.Subtle) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotBlank()) {
                    Text(
                        "Clear",
                        color = MiruroColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            query = ""
                            viewModel.clearSearch()
                        }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MiruroColors.Text,
                unfocusedTextColor = MiruroColors.Text,
                focusedBorderColor = MiruroColors.Accent,
                unfocusedBorderColor = MiruroColors.Border,
                cursorColor = MiruroColors.Accent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            SecondaryButton(if (advancedVisible) "Hide advanced filters" else "Show advanced filters", modifier = Modifier.width(230.dp), onClick = { advancedVisible = !advancedVisible })
            if (selectedGenres.isNotEmpty() || format != null || status != null || yearText.isNotBlank() || sort != AnimeSort.SEARCH_MATCH) {
                SecondaryButton("Reset filters", modifier = Modifier.width(160.dp), onClick = { format = null; selectedGenres = emptySet(); status = null; sort = AnimeSort.SEARCH_MATCH; yearText = "" })
            }
        }
        if (advancedVisible) {
            FocusableSurface(onClick = { }, modifier = Modifier.fillMaxWidth(), unfocusedBackground = MiruroColors.Card) {
                Column(Modifier.padding(14.dp)) {
                    Text("Advanced search", color = MiruroColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    FilterRow("Type", listOf(null to "All", "TV" to "Series", "MOVIE" to "Movies"), format) { format = it }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("Genres:", color = MiruroColors.Subtle, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(82.dp))
                        SecondaryButton(if (selectedGenres.isEmpty()) "Any genre" else "${selectedGenres.size} selected", modifier = Modifier.width(180.dp), onClick = { genrePickerVisible = true })
                        Spacer(Modifier.width(10.dp))
                        SecondaryButton("Clear genres", modifier = Modifier.width(155.dp), onClick = { selectedGenres = emptySet() })
                    }
                    MultiGenreRow(selectedGenres) { selectedGenres = it }
                    FilterRow("Status", listOf(null to "Any", "RELEASING" to "Airing", "FINISHED" to "Finished", "NOT_YET_RELEASED" to "Upcoming"), status) { status = it }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        items(AnimeSort.values().toList(), key = { it.name }) { item -> SecondaryButton(if (sort == item) "✓ ${item.label}" else item.label, modifier = Modifier.width(170.dp), onClick = { sort = item }) }
                        item { OutlinedTextField(value = yearText, onValueChange = { yearText = it.filter(Char::isDigit).take(4) }, placeholder = { Text("Year", color = MiruroColors.Subtle) }, singleLine = true, modifier = Modifier.width(130.dp)) }
                    }
                }
            }
        }
        if (genrePickerVisible) GenrePickerDialog(selectedGenres, onDismiss = { genrePickerVisible = false }, onSelected = { selectedGenres = it })
        if (recent.isNotEmpty() && query.isBlank()) {
            SectionTitle("Recent searches")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(recent, key = { it }) { term -> SecondaryButton(term, modifier = Modifier.width(180.dp), onClick = { query = term }) } }
        }
        when (val s = state) {
            null -> StateMessage(if (query.isBlank()) "Start typing to search AniList." else "Searching automatically as you type…")
            is UiState.Loading -> LoadingState("Searching…")
            is UiState.Error -> ErrorState(s.message) { viewModel.search(AnimeSearchFilters(query, format, yearText.toIntOrNull(), selectedGenres.toList(), status, sort)) }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    StateMessage("No results found.")
                } else {
                    PosterGrid(s.data, onOpenDetails, settings.posterGridDensity, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}


private val AniListGenres = listOf("Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller")

@Composable
private fun MultiGenreRow(selected: Set<String>, onSelected: (Set<String>) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text("Genres:", color = MiruroColors.Subtle, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(82.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SecondaryButton(if (selected.isEmpty()) "✓ Any" else "Any", modifier = Modifier.width(145.dp), onClick = { onSelected(emptySet()) }) }
            items(AniListGenres, key = { it }) { genre ->
                SecondaryButton(if (genre in selected) "✓ $genre" else genre, modifier = Modifier.width(155.dp), onClick = { onSelected(if (genre in selected) selected - genre else selected + genre) })
            }
        }
    }
}

@Composable
private fun GenrePickerDialog(selected: Set<String>, onDismiss: () -> Unit, onSelected: (Set<String>) -> Unit) {
    var draft by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { PrimaryButton("Apply", modifier = Modifier.width(120.dp), onClick = { onSelected(draft); onDismiss() }) },
        dismissButton = { SecondaryButton("Cancel", modifier = Modifier.width(120.dp), onClick = onDismiss) },
        title = { Text("Pick genres", color = MiruroColors.Text, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SecondaryButton(if (draft.isEmpty()) "✓ Any genre" else "Any genre", modifier = Modifier.fillMaxWidth(), onClick = { draft = emptySet() }) }
                items(AniListGenres, key = { it }) { genre ->
                    SecondaryButton(if (genre in draft) "✓ $genre" else genre, modifier = Modifier.fillMaxWidth(), onClick = { draft = if (genre in draft) draft - genre else draft + genre })
                }
            }
        },
        containerColor = MiruroColors.Card
    )
}

@Composable
private fun FilterRow(label: String, options: List<Pair<String?, String>>, selected: String?, onSelected: (String?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text("$label:", color = MiruroColors.Subtle, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(82.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(options, key = { it.second }) { (value, text) ->
                SecondaryButton(if (value == selected) "✓ $text" else text, modifier = Modifier.width(145.dp), onClick = { onSelected(value) })
            }
        }
    }
}

@Composable
fun MoviesScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadMovies() }
    val state by viewModel.movies.collectAsState()
    val settings by viewModel.settings.collectAsState()
    BrowseScreen("Movies", state, onRetry = { viewModel.loadMovies(force = true) }, onLoadMore = { viewModel.loadMovies(nextPage = true) }, onOpenDetails = onOpenDetails, gridDensity = settings.posterGridDensity)
}

@Composable
fun SeriesScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadSeries() }
    val state by viewModel.series.collectAsState()
    val settings by viewModel.settings.collectAsState()
    BrowseScreen("Series", state, onRetry = { viewModel.loadSeries(force = true) }, onLoadMore = { viewModel.loadSeries(nextPage = true) }, onOpenDetails = onOpenDetails, gridDensity = settings.posterGridDensity)
}

@Composable
private fun BrowseScreen(
    title: String,
    state: UiState<List<AnimeItem>>,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetails: (Int) -> Unit,
    gridDensity: PosterGridDensity
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle(title)
        when (state) {
            is UiState.Loading -> LoadingState("Loading $title…")
            is UiState.Error -> ErrorState(state.message, onRetry)
            is UiState.Success ->
                if (state.data.isEmpty()) StateMessage("Nothing found.")
                else {
                    PosterGrid(state.data, onOpenDetails, gridDensity = gridDensity, modifier = Modifier.weight(1f))
                    SecondaryButton("Load more $title", modifier = Modifier.width(220.dp), onClick = onLoadMore)
                }
        }
    }
}

private fun posterRowWidth(gridDensity: PosterGridDensity) = when (gridDensity) {
    PosterGridDensity.COMPACT -> 135.dp
    PosterGridDensity.COMFORTABLE -> 160.dp
    PosterGridDensity.LARGE -> 210.dp
}

@Composable
private fun PosterGrid(items: List<AnimeItem>, onOpenDetails: (Int) -> Unit, gridDensity: PosterGridDensity = PosterGridDensity.COMFORTABLE, modifier: Modifier = Modifier) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val minSize = when (gridDensity) {
        PosterGridDensity.COMPACT -> 135.dp
        PosterGridDensity.COMFORTABLE -> 160.dp
        PosterGridDensity.LARGE -> 210.dp
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TopButton { scope.launch { gridState.animateScrollToItem(0) } }
        }
        LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
        state = gridState,
        modifier = Modifier.fillMaxWidth().weight(1f)
    ) {
        items(items, key = { it.id }) { anime ->
            PosterCard(anime) { onOpenDetails(anime.id) }
        }
    }
    }
}

@Composable
private fun TopButton(onClick: () -> Unit) {
    SecondaryButton("Top", modifier = Modifier.width(100.dp), onClick = onClick)
}

@Composable
fun FavoritesScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    val entries by viewModel.watchlistEntries.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val ids = entries.map { it.id }.toSet()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(ids) { viewModel.resolveFavoriteMetadata(ids) }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle("Library")
        if (ids.isEmpty()) {
            StateMessage("Your library is empty. Add anime with \"+ Add to List\".")
        } else {
            FilterRow("Sort", WatchlistSort.values().map { it.name as String? to it.name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }, settings.watchlistSort.name) { it?.let { v -> viewModel.updateWatchlistSort(WatchlistSort.valueOf(v)) } }
            val sortedEntries = when (settings.watchlistSort) {
                WatchlistSort.TITLE -> entries.sortedBy { entry -> viewModel.cachedItem(entry.id)?.title ?: entry.title ?: "" }
                WatchlistSort.PROGRESS -> entries.sortedByDescending { entry -> progressForAnime(progress, entry.id) }
                WatchlistSort.RECENTLY_ADDED -> entries.sortedByDescending { it.addedAtMs }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TopButton { scope.launch { gridState.animateScrollToItem(0) } } }
            LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(when (settings.posterGridDensity) { PosterGridDensity.COMPACT -> 150.dp; PosterGridDensity.COMFORTABLE -> 180.dp; PosterGridDensity.LARGE -> 230.dp }), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                items(sortedEntries, key = { it.id }) { entry ->
                    val id = entry.id
                    val item = viewModel.cachedItem(id) ?: entry.title?.let { AnimeItem(entry.id, it, entry.posterUrl, null, com.ttvralph.miruroapp.data.AnimeType.UNKNOWN) }
                    Column {
                        if (item != null) PosterCard(item) { onOpenDetails(id) } else StateMessage("Resolving Anime #$id…")
                        Text("Saved ${formatSavedDate(entry.addedAtMs)} • ${progressForAnime(progress, id)}% complete", color = MiruroColors.Subtle, fontSize = 12.sp)
                        SecondaryButton("Remove", modifier = Modifier.width(160.dp), onClick = { viewModel.toggleFavorite(id) })
                    }
                }
            }
        }
    }
}

@Composable
fun GenresScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var selectedGenres by remember { mutableStateOf(emptySet<String>()) }
    var format by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(AnimeSort.POPULARITY) }
    var status by remember { mutableStateOf<String?>(null) }
    var yearText by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }
    val settings by viewModel.settings.collectAsState()
    var advancedVisible by remember { mutableStateOf(true) }
    var genrePickerVisible by remember { mutableStateOf(false) }
    val state by viewModel.genreResults.collectAsState()
    LaunchedEffect(selectedGenres, format, sort, status, yearText, page) { viewModel.loadGenre(selectedGenres.toList(), format, page, sort, status, yearText.toIntOrNull()) }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle("Genres")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            SecondaryButton(if (advancedVisible) "Hide browse filters" else "Show browse filters", modifier = Modifier.width(220.dp), onClick = { advancedVisible = !advancedVisible })
            SecondaryButton("Clear genres", modifier = Modifier.width(155.dp), onClick = { selectedGenres = emptySet(); page = 1 })
            SecondaryButton("Genre picker", modifier = Modifier.width(165.dp), onClick = { genrePickerVisible = true })
        }
        if (genrePickerVisible) GenrePickerDialog(selectedGenres, onDismiss = { genrePickerVisible = false }, onSelected = { selectedGenres = it; page = 1 })
        if (advancedVisible) {
        FilterRow("Type", listOf(null to "All", "TV" to "Series", "MOVIE" to "Movies"), format) { format = it; page = 1 }
        MultiGenreRow(selectedGenres) { selectedGenres = it; page = 1 }
        FilterRow("Status", listOf(null to "Any", "RELEASING" to "Airing", "FINISHED" to "Finished", "NOT_YET_RELEASED" to "Upcoming"), status) { status = it; page = 1 }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(AnimeSort.values().toList(), key = { it.name }) { item -> SecondaryButton(if (sort == item) "✓ ${item.label}" else item.label, modifier = Modifier.width(170.dp), onClick = { sort = item; page = 1 }) }
            item { OutlinedTextField(value = yearText, onValueChange = { yearText = it.filter(Char::isDigit).take(4); page = 1 }, placeholder = { Text("Year", color = MiruroColors.Subtle) }, singleLine = true, modifier = Modifier.width(130.dp)) }
        }
        }
        when (val s = state) {
            null, is UiState.Loading -> LoadingState("Loading selected genres…")
            is UiState.Error -> ErrorState(s.message) { viewModel.loadGenre(selectedGenres.toList(), format, page, sort, status, yearText.toIntOrNull()) }
            is UiState.Success -> if (s.data.isEmpty()) StateMessage("Nothing found for selected genres.") else { PosterGrid(s.data, onOpenDetails, settings.posterGridDensity, modifier = Modifier.weight(1f)); SecondaryButton("Load page ${page + 1}", modifier = Modifier.width(220.dp), onClick = { page += 1 }) }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MiruroViewModel? = null) {
    val settings by (viewModel?.settings ?: kotlinx.coroutines.flow.MutableStateFlow(com.ttvralph.miruroapp.data.AppSettings())).collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle("Settings")
        StateMessage("Preferences are saved locally and applied to playback/source selection defaults.")
        SectionTitle("Playback preferences")
        FilterRow("Audio", listOf("SUB" as String? to "Sub", "DUB" as String? to "Dub"), settings.preferredAudio.name) { it?.let { v -> viewModel?.updatePreferredAudio(AudioType.valueOf(v)) } }
        FilterRow("Provider", listOf("Auto" as String? to "Auto", "zoro" as String? to "Zoro", "animepahe" as String? to "AnimePahe", "gogoanime" as String? to "Gogo", "kiwi" as String? to "Kiwi"), settings.preferredProvider) { viewModel?.updatePreferredProvider(it ?: "Auto") }
        StateMessage("Yume TV uses a dark-only interface, so light/system theme choices are hidden to keep settings consistent with the UI.")
        FilterRow("Grid", PosterGridDensity.values().map { it.name as String? to it.name.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }, settings.posterGridDensity.name) { it?.let { v -> viewModel?.updatePosterGridDensity(PosterGridDensity.valueOf(v)) } }
        FilterRow("Autoplay", listOf("true" as String? to "On", "false" as String? to "Off"), settings.autoPlayNext.toString()) { viewModel?.updateAutoPlayNext(it == "true") }
        FilterRow("Resume", listOf("true" as String? to "On", "false" as String? to "Off"), settings.resumePlayback.toString()) { viewModel?.updateResumePlayback(it == "true") }
        FilterRow("Subtitle", listOf("English" as String? to "English", "Spanish" as String? to "Spanish", "Japanese" as String? to "Japanese"), settings.subtitleLanguage) { viewModel?.updateSubtitleLanguage(it ?: "English") }
        FilterRow("Style", listOf("Default" as String? to "Default", "Large" as String? to "Large", "High Contrast" as String? to "High Contrast"), settings.subtitleStyle) { viewModel?.updateSubtitleStyle(it ?: "Default") }
        FocusableSurface(onClick = { }, modifier = Modifier.fillMaxWidth(), unfocusedBackground = MiruroColors.Card) { Text("Subtitle preview — ${settings.subtitleStyle}", color = if (settings.subtitleStyle == "High Contrast") Color.White else MiruroColors.Text, fontSize = if (settings.subtitleStyle == "Large") 24.sp else 18.sp, modifier = Modifier.background(if (settings.subtitleStyle == "High Contrast") Color.Black else Color.Transparent).padding(14.dp)) }
        FilterRow("Watched", listOf("false" as String? to "Show", "true" as String? to "Hide"), settings.hideWatchedEpisodes.toString()) { viewModel?.updateHideWatchedEpisodes(it == "true") }
        if (viewModel != null) SecondaryButton("Clear watch history", modifier = Modifier.width(240.dp), onClick = { viewModel.clearWatchProgress() })
    }
}

@Composable
fun DetailsScreen(
    viewModel: MiruroViewModel,
    animeId: Int,
    onBack: (() -> Unit)? = null,
    onOpenEpisode: (Int, Int, AudioType) -> Unit,
    onPlayEpisode: (Int, Int, AudioType) -> Unit
) {
    LaunchedEffect(animeId) { viewModel.loadDetails(animeId) }
    val state by viewModel.details.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var audioFilter by remember(animeId, settings.preferredAudio) { mutableStateOf<AudioType?>(settings.preferredAudio) }
    val detailsListState = rememberLazyListState()
    val detailsScope = rememberCoroutineScope()

    when (val s = state) {
        is UiState.Loading -> LoadingState("Loading details…")
        is UiState.Error -> ErrorState(s.message) { viewModel.loadDetails(animeId) }
        is UiState.Success -> {
            val details = s.data
            val playableEpisodes = details.seasons
                .flatMap { season -> season.episodes.map { season.seasonNumber to it } }
                .filter { (_, ep) -> ep.sourceCandidates.isNotEmpty() }
            val firstPlayable = playableEpisodes.firstOrNull { (_, ep) -> ep.audioType == settings.preferredAudio }
                ?: playableEpisodes.firstOrNull()
            LazyColumn(state = detailsListState, modifier = Modifier.fillMaxSize()) {
                item {
                    DetailsHero(
                        details = details,
                        inList = details.id in favorites,
                        playLabel = firstPlayable?.let { (season, ep) -> "Play S$season E${ep.episodeNumber}" },
                        onPlay = firstPlayable?.let { (season, ep) -> { onPlayEpisode(season, ep.episodeNumber, ep.audioType) } },
                        onToggleList = { viewModel.toggleFavorite(details.id) },
                        onBack = onBack
                    )
                }
                if (details.seasons.isEmpty()) {
                    item { StateMessage("No episodes available.") }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Episodes", progressSummary(progress, details))
                            Spacer(Modifier.width(12.dp))
                            SecondaryButton(if (audioFilter == null) "✓ All" else "All", modifier = Modifier.width(110.dp), onClick = { audioFilter = null })
                            Spacer(Modifier.width(8.dp))
                            SecondaryButton(if (audioFilter == AudioType.SUB) "✓ Sub" else "Sub", modifier = Modifier.width(110.dp), onClick = { audioFilter = AudioType.SUB })
                            Spacer(Modifier.width(8.dp))
                            SecondaryButton(if (audioFilter == AudioType.DUB) "✓ Dub" else "Dub", modifier = Modifier.width(110.dp), onClick = { audioFilter = AudioType.DUB })
                            Spacer(Modifier.width(8.dp))
                            SecondaryButton("Top", modifier = Modifier.width(100.dp), onClick = { detailsScope.launch { detailsListState.animateScrollToItem(0) } })
                        }
                    }
                    details.seasons.forEach { season ->
                        if (details.seasons.size > 1) {
                            item {
                                Text(
                                    "Season ${season.seasonNumber}: ${season.title}",
                                    color = MiruroColors.Subtle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
                                )
                            }
                        }
                        items(
                            season.episodes.filter { ep -> (audioFilter == null || ep.audioType == audioFilter) && (!settings.hideWatchedEpisodes || progress.none { it.animeId == ep.anilistId && it.seasonNumber == season.seasonNumber && it.episodeNumber == ep.episodeNumber && it.audioType == ep.audioType && it.watched }) }.chunked(EPISODE_GRID_COLUMNS),
                            key = { row -> row.first().let { "${season.seasonNumber}-${it.episodeNumber}-${it.audioType}" } }
                        ) { rowEpisodes ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                            ) {
                                rowEpisodes.forEach { ep ->
                                    val episodeProgress = progress.firstOrNull { it.animeId == ep.anilistId && it.seasonNumber == season.seasonNumber && it.episodeNumber == ep.episodeNumber && it.audioType == ep.audioType }
                                    EpisodeCard(
                                        ep = ep,
                                        progress = episodeProgress,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onOpenEpisode(season.seasonNumber, ep.episodeNumber, ep.audioType) }
                                    )
                                }
                                repeat(EPISODE_GRID_COLUMNS - rowEpisodes.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DetailsHero(
    details: AnimeDetails,
    inList: Boolean,
    playLabel: String?,
    onPlay: (() -> Unit)?,
    onToggleList: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiruroColors.Card)
    ) {
        AsyncImage(
            model = details.bannerUrl ?: details.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.4f to MiruroColors.Background.copy(alpha = 0.35f),
                            1f to MiruroColors.Background
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to MiruroColors.Background.copy(alpha = 0.85f),
                            0.55f to Color.Transparent,
                            1f to Color.Transparent
                        )
                    )
                )
        )
        onBack?.let { back ->
            SecondaryButton(
                "Back",
                modifier = Modifier.align(Alignment.TopStart).padding(36.dp).width(120.dp),
                onClick = back
            )
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(36.dp).width(720.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                details.rating?.let {
                    RatingLabel(it)
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    listOfNotNull(
                        details.year?.toString(),
                        "${details.seasons.size} Season${if (details.seasons.size == 1) "" else "s"}"
                    ).joinToString("   •   "),
                    color = MiruroColors.Text,
                    fontSize = 15.sp
                )
                details.status?.let {
                    Spacer(Modifier.width(16.dp))
                    Badge(it.uppercase(Locale.ROOT), container = MiruroColors.Focused, content = MiruroColors.Text)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                details.title,
                color = MiruroColors.Text,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 48.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                details.genres.take(4).forEach { GenreChip(it) }
            }
            Spacer(Modifier.height(14.dp))
            BodyText(details.description ?: "No synopsis available.", maxLines = 3)
            Spacer(Modifier.height(22.dp))
            Row {
                if (onPlay != null) {
                    PrimaryButton(playLabel ?: "Play", modifier = Modifier.width(220.dp), onClick = onPlay)
                    Spacer(Modifier.width(16.dp))
                }
                SecondaryButton(if (inList) "In My List" else "+ Add to List", modifier = Modifier.width(200.dp), onClick = onToggleList)
            }
        }
    }
}

@Composable
private fun EpisodeCard(ep: AnimeEpisode, progress: WatchProgress? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val categories = ep.sourceCandidates.map { it.category }.toSet()
    val audioLabel = when {
        "sub" in categories && "dub" in categories -> "Sub/Dub"
        "dub" in categories -> "Dub"
        "sub" in categories -> "Sub"
        else -> null
    }
    FocusableSurface(onClick = onClick, modifier = modifier, unfocusedBackground = MiruroColors.Card) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MiruroColors.CardHigh)) {
                if (ep.thumbnailUrl != null) {
                    AsyncImage(
                        model = ep.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "${ep.episodeNumber}",
                        color = Color.White.copy(alpha = 0.12f),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
                if (audioLabel != null) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                        EpisodeBadge(audioLabel)
                    }
                }
                progress?.let {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                        EpisodeBadge(if (it.watched) "Watched" else "${(it.percent * 100).toInt()}%")
                    }
                }
                ep.runtimeMinutes?.let {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                        EpisodeBadge("${it}m")
                    }
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text("E${ep.episodeNumber} • ${ep.audioType.name}", color = MiruroColors.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    ep.title ?: "Episode ${ep.episodeNumber}",
                    color = MiruroColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ep.releaseDate?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MiruroColors.Subtle, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EpisodeBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EpisodeDetailsScreen(episode: AnimeEpisode?, viewModel: MiruroViewModel? = null, onPlay: () -> Unit) {
    if (episode == null) {
        StateMessage("Episode not found.")
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        episode.thumbnailUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(400.dp).height(230.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(16.dp))
        }
        SectionTitle("Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}")
        listOf(
            "Title" to (episode.title ?: "Episode ${episode.episodeNumber}"),
            "Runtime" to (episode.runtimeMinutes?.let { "${it}m" } ?: "Unknown"),
            "Release date" to (episode.releaseDate ?: "Unknown"),
            "Audio type" to episode.audioType.name,
            "Providers" to providerAvailabilityLabel(episode),
            "Sub/Dub" to episode.sourceCandidates.groupBy { it.category.ifBlank { "unknown" } }.map { (category, candidates) -> "${category.uppercase(Locale.ROOT)} ${candidates.size}" }.joinToString(" • ").ifBlank { "Unknown" },
            "Playback" to if (episode.sourceCandidates.isNotEmpty()) "Choose a provider or use Auto before playback. Quality and health are checked when the source resolves." else "No playable source is available for this episode."
        ).forEach { (label, value) -> BodyText("$label: $value") }
        if (episode.sourceCandidates.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            val providers = listOf("Auto") + episode.sourceCandidates.map { it.provider }.distinct()
            val selected = viewModel?.settings?.collectAsState()?.value?.preferredProvider ?: "Auto"
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(providers, key = { it }) { provider ->
                    SecondaryButton(if (provider == selected) "✓ $provider" else provider, modifier = Modifier.width(170.dp), onClick = { viewModel?.updatePreferredProvider(provider) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Play", modifier = Modifier.width(180.dp), onClick = onPlay)
                val progress = viewModel?.watchProgress?.collectAsState()?.value?.firstOrNull { it.key == WatchProgress.makeKey(episode.anilistId, episode.seasonNumber, episode.episodeNumber, episode.audioType) }
                SecondaryButton(if (progress?.watched == true) "Mark unwatched" else "Mark watched", modifier = Modifier.width(210.dp), onClick = { viewModel?.setEpisodeWatched(episode, progress?.watched != true) })
            }
        }
    }
}

private fun providerAvailabilityLabel(episode: AnimeEpisode): String = episode.sourceCandidates
    .groupBy { it.provider }
    .map { (provider, candidates) -> "$provider (${candidates.map { it.category.uppercase(Locale.ROOT) }.distinct().joinToString("/")})" }
    .joinToString()
    .ifBlank { "None" }

private fun progressSummary(progress: List<WatchProgress>, details: AnimeDetails): String {
    val total = details.seasons.sumOf { it.episodes.size }.coerceAtLeast(1)
    val watched = progress.count { it.animeId == details.id && it.watched }
    return "$watched/$total WATCHED • ${progressForAnime(progress, details.id)}% COMPLETE"
}

private fun formatTimeShort(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun progressForAnime(progress: List<WatchProgress>, animeId: Int): Int {
    val items = progress.filter { it.animeId == animeId }
    if (items.isEmpty()) return 0
    return (items.map { it.percent }.average() * 100).toInt().coerceIn(0, 100)
}

private fun formatSavedDate(ms: Long): String = java.text.SimpleDateFormat("MMM d, yyyy", Locale.US).format(java.util.Date(ms))
