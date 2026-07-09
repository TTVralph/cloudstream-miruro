package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeSearchFilters
import com.ttvralph.miruroapp.data.AnimeSort
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.data.ThemeMode
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.data.WatchlistSort
import com.ttvralph.miruroapp.ui.Badge
import com.ttvralph.miruroapp.ui.BodyText
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.GenreChip
import com.ttvralph.miruroapp.ui.LandscapeCard
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.RatingLabel
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.SectionTitle
import com.ttvralph.miruroapp.ui.StateMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val EPISODE_GRID_COLUMNS = 3
private val AniListGenres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller")

@Composable
fun HomeScreen(
    viewModel: MiruroViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit = { onOpenDetails(it.animeId) }
) {
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
                item {
                    Column(Modifier.padding(horizontal = 58.dp)) {
                        val unfinished = progress.filter { !it.watched }.take(20)
                        if (unfinished.isNotEmpty()) {
                            ContinueWatchingRow(unfinished, viewModel, onPlayProgress)
                        }
                    }
                }
                items(rows, key = { it.title }) { row ->
                    Column(Modifier.padding(horizontal = 58.dp)) {
                        PosterRow(
                            title = row.title,
                            items = row.items,
                            onClick = onOpenDetails,
                            gridDensity = settings.posterGridDensity,
                            badge = if (row.title.contains("Trending", ignoreCase = true)) "HOT" else null
                        )
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 58.dp), horizontalArrangement = Arrangement.End) {
                        SecondaryButton("Top", modifier = Modifier.width(100.dp), onClick = { scope.launch { listState.animateScrollToItem(0) } })
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun HomeHero(item: AnimeItem, inList: Boolean, onWatch: () -> Unit, onToggleList: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp)
            .background(MiruroColors.Background)
    ) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MiruroColors.CardHigh)
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(MiruroColors.Background, MiruroColors.Background.copy(alpha = 0.86f), MiruroColors.Background.copy(alpha = 0.28f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, MiruroColors.Background))))
        Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 58.dp, top = 36.dp).width(760.dp)) {
            Badge("★ #1 in anime today")
            Spacer(Modifier.height(20.dp))
            Text(
                item.title.uppercase(Locale.ROOT),
                color = MiruroColors.Text,
                fontFamily = FontFamily.Serif,
                fontSize = 72.sp,
                lineHeight = 70.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.score?.let {
                    RatingLabel(String.format(Locale.US, "%.1f", it / 10f))
                    Spacer(Modifier.width(16.dp))
                }
                Text(listOfNotNull(item.year?.toString(), item.type.name.takeIf { it != "UNKNOWN" }).joinToString("   •   "), color = MiruroColors.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            BodyText("Continue into a cinematic anime catalog built for couch-distance browsing, fast D-pad movement, and one-click episode resume.", maxLines = 2)
            Spacer(Modifier.height(28.dp))
            Row {
                PrimaryButton("Play Now", modifier = Modifier.width(210.dp), onClick = onWatch)
                Spacer(Modifier.width(16.dp))
                SecondaryButton("ⓘ More Info", modifier = Modifier.width(190.dp), onClick = onWatch)
                Spacer(Modifier.width(16.dp))
                SecondaryButton(if (inList) "In My List" else "+ My List", modifier = Modifier.width(170.dp), onClick = onToggleList)
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(progress: List<WatchProgress>, viewModel: MiruroViewModel, onPlayProgress: (WatchProgress) -> Unit) {
    Column {
        SectionTitle("Continue Watching", "RESUME")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
        ) {
            items(progress, key = { it.key }) { item ->
                val anime = viewModel.cachedItem(item.animeId) ?: AnimeItem(
                    id = item.animeId,
                    title = "Anime #${item.animeId}",
                    posterUrl = null,
                    bannerUrl = null,
                    type = AnimeType.UNKNOWN
                )
                LandscapeCard(
                    item = anime,
                    width = 360.dp,
                    height = 180.dp,
                    progressPercent = item.percent.toFloat(),
                    onClick = { onPlayProgress(item) }
                )
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
            horizontalArrangement = Arrangement.spacedBy(18.dp),
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

    LaunchedEffect(query, format, selectedGenres, status, sort, yearText) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() && selectedGenres.isEmpty() && format == null && status == null && yearText.isBlank() && sort == AnimeSort.SEARCH_MATCH) {
            viewModel.clearSearch()
        } else {
            delay(350)
            viewModel.search(AnimeSearchFilters(trimmed, format, yearText.toIntOrNull(), selectedGenres.toList(), status, sort))
        }
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(modifier = Modifier.width(470.dp).fillMaxSize()) {
            SectionTitle("Search")
            SearchBox(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(16.dp))
            TvKeyboard(
                onKey = { key ->
                    query = when (key) {
                        "⌫" -> query.dropLast(1)
                        "SPACE" -> query + " "
                        "CLEAR" -> ""
                        "SEARCH" -> query
                        else -> query + key
                    }
                    if (key == "SEARCH") viewModel.search(query)
                }
            )
            Spacer(Modifier.height(22.dp))
            SectionTitle("Recent Searches")
            if (recent.isEmpty()) {
                StateMessage("Your recent searches will show here.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recent.take(5).forEach { term ->
                        SecondaryButton("◷ $term", modifier = Modifier.fillMaxWidth(), onClick = { query = term })
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionTitle("Quick Filters")
            QuickFilterGrid(
                selectedGenres = selectedGenres,
                onGenreToggle = { genre -> selectedGenres = if (genre in selectedGenres) selectedGenres - genre else selectedGenres + genre },
                onTrending = { sort = AnimeSort.POPULARITY },
                onMovies = { format = "MOVIE" },
                onNew = { sort = AnimeSort.RELEASE_DATE },
                onReset = { format = null; selectedGenres = emptySet(); status = null; sort = AnimeSort.SEARCH_MATCH; yearText = "" }
            )
        }

        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            SectionTitle("Top Results")
            when (val s = state) {
                null -> StateMessage("Use the TV keyboard, voice search, or filters to find anime.")
                is UiState.Loading -> LoadingState("Searching…")
                is UiState.Error -> ErrorState(s.message) { viewModel.search(AnimeSearchFilters(query, format, yearText.toIntOrNull(), selectedGenres.toList(), status, sort)) }
                is UiState.Success -> if (s.data.isEmpty()) StateMessage("No results found.") else PosterGrid(s.data, onOpenDetails, settings.posterGridDensity, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    FocusableSurface(onClick = { }, modifier = Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(18.dp), unfocusedBackground = MiruroColors.Panel) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⌕", color = if (focused) MiruroColors.AccentSoft else MiruroColors.Muted, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(16.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = MiruroColors.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Search anime, movies, genres", color = MiruroColors.Muted, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    inner()
                }
            )
            Spacer(Modifier.width(12.dp))
            Text("♩", color = MiruroColors.AccentSoft, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TvKeyboard(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("A", "B", "C", "D", "E", "F", "G", "⌫"),
        listOf("H", "I", "J", "K", "L", "M", "N"),
        listOf("O", "P", "Q", "R", "S", "T", "U"),
        listOf("V", "W", "X", "Y", "Z", "1", "2"),
        listOf("3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("SPACE", "CLEAR", "SEARCH")
    )
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { key ->
                    SecondaryButton(key, modifier = Modifier.width(if (key.length > 2) 128.dp else 48.dp), onClick = { onKey(key) })
                }
            }
        }
    }
}

@Composable
private fun QuickFilterGrid(
    selectedGenres: Set<String>,
    onGenreToggle: (String) -> Unit,
    onTrending: () -> Unit,
    onMovies: () -> Unit,
    onNew: () -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("🔥 Trending", modifier = Modifier.width(150.dp), onClick = onTrending)
            SecondaryButton("▣ Movies", modifier = Modifier.width(135.dp), onClick = onMovies)
            SecondaryButton("NEW Episodes", modifier = Modifier.width(160.dp), onClick = onNew)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AniListGenres.take(10), key = { it }) { genre ->
                GenreChip(genre, selected = genre in selectedGenres, onClick = { onGenreToggle(genre) })
            }
        }
        SecondaryButton("Reset filters", modifier = Modifier.width(170.dp), onClick = onReset)
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
    BrowseScreen("Anime", state, onRetry = { viewModel.loadSeries(force = true) }, onLoadMore = { viewModel.loadSeries(nextPage = true) }, onOpenDetails = onOpenDetails, gridDensity = settings.posterGridDensity)
}

@Composable
private fun BrowseScreen(title: String, state: UiState<List<AnimeItem>>, onRetry: () -> Unit, onLoadMore: () -> Unit, onOpenDetails: (Int) -> Unit, gridDensity: PosterGridDensity) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle(title)
        when (state) {
            is UiState.Loading -> LoadingState("Loading $title…")
            is UiState.Error -> ErrorState(state.message, onRetry)
            is UiState.Success -> if (state.data.isEmpty()) StateMessage("Nothing found.") else {
                PosterGrid(state.data, onOpenDetails, gridDensity = gridDensity, modifier = Modifier.weight(1f))
                SecondaryButton("Load more $title", modifier = Modifier.width(220.dp), onClick = onLoadMore)
            }
        }
    }
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
        SectionTitle("My List")
        if (ids.isEmpty()) {
            StateMessage("Your list is empty. Add anime with \"+ My List\".")
        } else {
            FilterRow("Sort", WatchlistSort.values().map { it.name as String? to it.name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }, settings.watchlistSort.name) { it?.let { v -> viewModel.updateWatchlistSort(WatchlistSort.valueOf(v)) } }
            val sortedEntries = when (settings.watchlistSort) {
                WatchlistSort.TITLE -> entries.sortedBy { entry -> viewModel.cachedItem(entry.id)?.title ?: entry.title ?: "" }
                WatchlistSort.PROGRESS -> entries.sortedByDescending { entry -> progressForAnime(progress, entry.id) }
                WatchlistSort.RECENTLY_ADDED -> entries.sortedByDescending { it.addedAtMs }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SecondaryButton("Top", modifier = Modifier.width(100.dp), onClick = { scope.launch { gridState.animateScrollToItem(0) } }) }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(posterRowWidth(settings.posterGridDensity)),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedEntries, key = { it.id }) { entry ->
                    val item = viewModel.cachedItem(entry.id) ?: entry.title?.let { AnimeItem(entry.id, it, entry.posterUrl, null, AnimeType.UNKNOWN) }
                    Column {
                        if (item != null) PosterCard(item) { onOpenDetails(entry.id) } else StateMessage("Resolving Anime #${entry.id}…")
                        Text("Saved ${formatSavedDate(entry.addedAtMs)} • ${progressForAnime(progress, entry.id)}% complete", color = MiruroColors.Muted, fontSize = 12.sp)
                        SecondaryButton("Remove", modifier = Modifier.width(160.dp), onClick = { viewModel.toggleFavorite(entry.id) })
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
    val state by viewModel.genreResults.collectAsState()

    LaunchedEffect(selectedGenres, format, sort, status, yearText, page) { viewModel.loadGenre(selectedGenres.toList(), format, page, sort, status, yearText.toIntOrNull()) }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle("New & Popular", "DISCOVER")
        FilterRow("Type", listOf(null to "All", "TV" to "Anime", "MOVIE" to "Movies"), format) { format = it; page = 1 }
        MultiGenreRow(selectedGenres) { selectedGenres = it; page = 1 }
        FilterRow("Status", listOf(null to "Any", "RELEASING" to "Airing", "FINISHED" to "Finished", "NOT_YET_RELEASED" to "Upcoming"), status) { status = it; page = 1 }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(AnimeSort.values().toList(), key = { it.name }) { item -> SecondaryButton(if (sort == item) "✓ ${item.label}" else item.label, modifier = Modifier.width(170.dp), onClick = { sort = item; page = 1 }) }
        }
        when (val s = state) {
            null, is UiState.Loading -> LoadingState("Loading selected genres…")
            is UiState.Error -> ErrorState(s.message) { viewModel.loadGenre(selectedGenres.toList(), format, page, sort, status, yearText.toIntOrNull()) }
            is UiState.Success -> if (s.data.isEmpty()) StateMessage("Nothing found for selected genres.") else {
                PosterGrid(s.data, onOpenDetails, settings.posterGridDensity, modifier = Modifier.weight(1f))
                SecondaryButton("Load page ${page + 1}", modifier = Modifier.width(220.dp), onClick = { page += 1 })
            }
        }
    }
}

@Composable
private fun MultiGenreRow(selected: Set<String>, onSelected: (Set<String>) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text("Genres:", color = MiruroColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(82.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { GenreChip("All", selected = selected.isEmpty(), onClick = { onSelected(emptySet()) }) }
            items(AniListGenres, key = { it }) { genre ->
                GenreChip(genre, selected = genre in selected, onClick = { onSelected(if (genre in selected) selected - genre else selected + genre) })
            }
        }
    }
}

@Composable
private fun FilterRow(label: String, options: List<Pair<String?, String>>, selected: String?, onSelected: (String?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text("$label:", color = MiruroColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(82.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(options, key = { it.second }) { (value, text) ->
                SecondaryButton(if (value == selected) "✓ $text" else text, modifier = Modifier.width(150.dp), onClick = { onSelected(value) })
            }
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
        FilterRow("Theme", ThemeMode.values().map { it.name as String? to it.name.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }, settings.themeMode.name) { it?.let { v -> viewModel?.updateThemeMode(ThemeMode.valueOf(v)) } }
        FilterRow("Grid", PosterGridDensity.values().map { it.name as String? to it.name.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }, settings.posterGridDensity.name) { it?.let { v -> viewModel?.updatePosterGridDensity(PosterGridDensity.valueOf(v)) } }
        FilterRow("Autoplay", listOf("true" as String? to "On", "false" as String? to "Off"), settings.autoPlayNext.toString()) { viewModel?.updateAutoPlayNext(it == "true") }
        FilterRow("Resume", listOf("true" as String? to "On", "false" as String? to "Off"), settings.resumePlayback.toString()) { viewModel?.updateResumePlayback(it == "true") }
        FilterRow("Subtitle", listOf("English" as String? to "English", "Spanish" as String? to "Spanish", "Japanese" as String? to "Japanese"), settings.subtitleLanguage) { viewModel?.updateSubtitleLanguage(it ?: "English") }
        FilterRow("Style", listOf("Default" as String? to "Default", "Large" as String? to "Large", "High Contrast" as String? to "High Contrast"), settings.subtitleStyle) { viewModel?.updateSubtitleStyle(it ?: "Default") }
        FilterRow("Watched", listOf("false" as String? to "Show", "true" as String? to "Hide"), settings.hideWatchedEpisodes.toString()) { viewModel?.updateHideWatchedEpisodes(it == "true") }
        if (viewModel != null) SecondaryButton("Clear watch history", modifier = Modifier.width(240.dp), onClick = { viewModel.clearWatchProgress() })
    }
}

@Composable
fun DetailsScreen(
    viewModel: MiruroViewModel,
    animeId: Int,
    onBack: () -> Unit,
    onOpenEpisode: (Int, Int, AudioType) -> Unit,
    onPlayEpisode: (Int, Int, AudioType) -> Unit
) {
    LaunchedEffect(animeId) { viewModel.loadDetails(animeId) }
    val state by viewModel.details.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var audioFilter by remember(animeId, settings.preferredAudio) { mutableStateOf<AudioType?>(settings.preferredAudio) }

    when (val s = state) {
        is UiState.Loading -> LoadingState("Loading details…")
        is UiState.Error -> ErrorState(s.message) { viewModel.loadDetails(animeId) }
        is UiState.Success -> DetailsModal(
            details = s.data,
            inList = s.data.id in favorites,
            progress = progress,
            audioFilter = audioFilter,
            hideWatched = settings.hideWatchedEpisodes,
            preferredAudio = settings.preferredAudio,
            onAudioFilter = { audioFilter = it },
            onBack = onBack,
            onToggleList = { viewModel.toggleFavorite(s.data.id) },
            onOpenEpisode = onOpenEpisode,
            onPlayEpisode = onPlayEpisode
        )
    }
}

@Composable
private fun DetailsModal(
    details: AnimeDetails,
    inList: Boolean,
    progress: List<WatchProgress>,
    audioFilter: AudioType?,
    hideWatched: Boolean,
    preferredAudio: AudioType,
    onAudioFilter: (AudioType?) -> Unit,
    onBack: () -> Unit,
    onToggleList: () -> Unit,
    onOpenEpisode: (Int, Int, AudioType) -> Unit,
    onPlayEpisode: (Int, Int, AudioType) -> Unit
) {
    val allEpisodes = details.seasons.flatMap { it.episodes }
    val visibleEpisodes = allEpisodes.filter { ep ->
        (audioFilter == null || ep.audioType == audioFilter) &&
            (!hideWatched || progress.none { it.animeId == ep.anilistId && it.seasonNumber == ep.seasonNumber && it.episodeNumber == ep.episodeNumber && it.audioType == ep.audioType && it.watched })
    }
    val firstPlayable = visibleEpisodes.firstOrNull { it.audioType == preferredAudio && it.sourceCandidates.isNotEmpty() }
        ?: visibleEpisodes.firstOrNull { it.sourceCandidates.isNotEmpty() }

    Box(modifier = Modifier.fillMaxSize().background(MiruroColors.Background)) {
        AsyncImage(model = details.bannerUrl ?: details.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(MiruroColors.CardHigh))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(MiruroColors.Background.copy(alpha = 0.96f), MiruroColors.Background.copy(alpha = 0.82f), MiruroColors.Background.copy(alpha = 0.42f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MiruroColors.Background.copy(alpha = 0.50f), MiruroColors.Background))))

        SecondaryButton("× Close", modifier = Modifier.align(Alignment.TopEnd).padding(28.dp).width(132.dp), onClick = onBack)

        Row(
            modifier = Modifier.fillMaxSize().padding(start = 58.dp, end = 58.dp, top = 92.dp, bottom = 42.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp)
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Badge(details.status ?: "New Episode", container = MiruroColors.Accent2, content = Color.Black)
                Spacer(Modifier.height(18.dp))
                Text(details.title.uppercase(Locale.ROOT), color = Color.White, fontFamily = FontFamily.Serif, fontSize = 62.sp, lineHeight = 60.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    details.rating?.let {
                        RatingLabel(it)
                        Spacer(Modifier.width(16.dp))
                    }
                    Text(listOfNotNull(details.year?.toString(), "${details.seasons.size} season${if (details.seasons.size == 1) "" else "s"}").joinToString("   •   "), color = MiruroColors.Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(details.genres.take(6), key = { it }) { GenreChip(it) }
                }
                Spacer(Modifier.height(20.dp))
                BodyText(details.description ?: "No synopsis available.", maxLines = 5)
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (firstPlayable != null) PrimaryButton("Play S${firstPlayable.seasonNumber} E${firstPlayable.episodeNumber}", modifier = Modifier.width(230.dp), onClick = { onPlayEpisode(firstPlayable.seasonNumber, firstPlayable.episodeNumber, firstPlayable.audioType) })
                    SecondaryButton(if (inList) "In My List" else "+ My List", modifier = Modifier.width(180.dp), onClick = onToggleList)
                    SecondaryButton("♡ Like", modifier = Modifier.width(140.dp), onClick = { })
                }
                Spacer(Modifier.height(30.dp))
                InfoGrid(details)
            }

            Column(
                modifier = Modifier
                    .width(560.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MiruroColors.Panel.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                    .border(1.dp, MiruroColors.Border, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Episodes", progressSummary(progress, details), trailing = null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    SecondaryButton(if (audioFilter == null) "✓ All" else "All", modifier = Modifier.width(100.dp), onClick = { onAudioFilter(null) })
                    SecondaryButton(if (audioFilter == AudioType.SUB) "✓ Sub" else "Sub", modifier = Modifier.width(100.dp), onClick = { onAudioFilter(AudioType.SUB) })
                    SecondaryButton(if (audioFilter == AudioType.DUB) "✓ Dub" else "Dub", modifier = Modifier.width(100.dp), onClick = { onAudioFilter(AudioType.DUB) })
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(visibleEpisodes, key = { "${it.seasonNumber}-${it.episodeNumber}-${it.audioType}" }) { ep ->
                        val episodeProgress = progress.firstOrNull { it.animeId == ep.anilistId && it.seasonNumber == ep.seasonNumber && it.episodeNumber == ep.episodeNumber && it.audioType == ep.audioType }
                        DetailEpisodeRow(ep = ep, progress = episodeProgress, onOpen = { onOpenEpisode(ep.seasonNumber, ep.episodeNumber, ep.audioType) }, onPlay = { onPlayEpisode(ep.seasonNumber, ep.episodeNumber, ep.audioType) })
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoGrid(details: AnimeDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoPill("Status", details.status ?: "Unknown")
            InfoPill("Year", details.year?.toString() ?: "Unknown")
            InfoPill("Episodes", details.seasons.sumOf { it.episodes.size }.toString())
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .border(1.dp, MiruroColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(label, color = MiruroColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = MiruroColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailEpisodeRow(ep: AnimeEpisode, progress: WatchProgress?, onOpen: () -> Unit, onPlay: () -> Unit) {
    FocusableSurface(onClick = onPlay, modifier = Modifier.fillMaxWidth().height(118.dp), unfocusedBackground = MiruroColors.Card.copy(alpha = 0.78f)) { focused ->
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(154.dp).height(86.dp).clip(RoundedCornerShape(12.dp)).background(MiruroColors.CardHigh)) {
                if (ep.thumbnailUrl != null) AsyncImage(model = ep.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Text("${ep.episodeNumber}", color = Color.White.copy(alpha = 0.16f), fontSize = 46.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
                progress?.let { pct ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((pct.percent.toFloat()).coerceIn(0f, 1f)).height(4.dp).background(MiruroColors.Accent))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("S${ep.seasonNumber} E${ep.episodeNumber} • ${ep.audioType.name}", color = MiruroColors.AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(ep.title ?: "Episode ${ep.episodeNumber}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(ep.runtimeMinutes?.let { "${it}m" }, progress?.let { if (it.watched) "Watched" else "${(it.percent * 100).toInt()}% watched" }).joinToString(" • "), color = MiruroColors.Muted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            if (focused) Text("▶", color = MiruroColors.AccentSoft, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { onOpen() })
        }
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
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(420.dp).height(236.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(16.dp))
        }
        SectionTitle("Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}")
        BodyText("Title: ${episode.title ?: "Episode ${episode.episodeNumber}"}")
        BodyText("Runtime: ${episode.runtimeMinutes?.let { "${it}m" } ?: "Unknown"}")
        BodyText("Audio: ${episode.audioType.name}")
        BodyText("Providers: ${providerAvailabilityLabel(episode)}")
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Play", modifier = Modifier.width(180.dp), onClick = onPlay)
            val progress = viewModel?.watchProgress?.collectAsState()?.value?.firstOrNull { it.key == WatchProgress.makeKey(episode.anilistId, episode.seasonNumber, episode.episodeNumber, episode.audioType) }
            SecondaryButton(if (progress?.watched == true) "Mark unwatched" else "Mark watched", modifier = Modifier.width(210.dp), onClick = { viewModel?.setEpisodeWatched(episode, progress?.watched != true) })
        }
    }
}

private fun posterRowWidth(gridDensity: PosterGridDensity) = when (gridDensity) {
    PosterGridDensity.COMPACT -> 135.dp
    PosterGridDensity.COMFORTABLE -> 166.dp
    PosterGridDensity.LARGE -> 210.dp
}

@Composable
private fun PosterGrid(items: List<AnimeItem>, onOpenDetails: (Int) -> Unit, gridDensity: PosterGridDensity = PosterGridDensity.COMFORTABLE, modifier: Modifier = Modifier) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SecondaryButton("Top", modifier = Modifier.width(100.dp), onClick = { scope.launch { gridState.animateScrollToItem(0) } })
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = posterRowWidth(gridDensity)),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(items, key = { it.id }) { anime -> PosterCard(anime) { onOpenDetails(anime.id) } }
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
    return "$watched/$total watched"
}

private fun progressForAnime(progress: List<WatchProgress>, animeId: Int): Int {
    val items = progress.filter { it.animeId == animeId }
    if (items.isEmpty()) return 0
    return (items.map { it.percent }.average() * 100).toInt().coerceIn(0, 100)
}

private fun formatSavedDate(ms: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms))
