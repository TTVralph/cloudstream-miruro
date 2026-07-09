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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.ttvralph.miruroapp.data.ThemeMode
import com.ttvralph.miruroapp.data.AudioType
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

private const val EPISODE_GRID_COLUMNS = 3

@Composable
fun HomeScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    val state by viewModel.homeRows.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    when (val s = state) {
        is UiState.Loading -> LoadingState("Loading home rows…")
        is UiState.Error -> ErrorState(s.message) { viewModel.loadHome() }
        is UiState.Success -> {
            val rows = s.data
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    item { ContinueWatchingRow(unfinished, viewModel, onOpenDetails) }
                }
                items(rows, key = { it.title }) { row ->
                    PosterRow(
                        title = row.title,
                        items = row.items,
                        onClick = onOpenDetails,
                        badge = if (row.title == "Trending Now") "HOT" else null
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(progress: List<WatchProgress>, viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    Column {
        SectionTitle("Continue Watching", "RESUME")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
        ) {
            items(progress, key = { it.key }) { item ->
                val anime = viewModel.cachedItem(item.animeId)
                FocusableSurface(onClick = { onOpenDetails(item.animeId) }, modifier = Modifier.width(220.dp).height(128.dp)) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(anime?.title ?: "Anime #${item.animeId}", color = MiruroColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("S${item.seasonNumber} E${item.episodeNumber} • ${(item.percent * 100).toInt()}% watched", color = MiruroColors.Subtle, fontSize = 12.sp)
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
    badge: String? = null
) {
    Column {
        SectionTitle(title, badge)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)
        ) {
            items(items, key = { it.id }) { anime ->
                PosterCard(anime) { onClick(anime.id) }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<String?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(AnimeSort.SEARCH_MATCH) }
    var yearText by remember { mutableStateOf("") }
    val state by viewModel.searchResults.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            viewModel.clearSearch()
        } else {
            delay(350)
            viewModel.search(AnimeSearchFilters(trimmed, format, yearText.toIntOrNull(), genre, status, sort))
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
        FilterRow("Type", listOf(null to "All", "TV" to "Series", "MOVIE" to "Movies"), format) { format = it }
        FilterRow("Genre", listOf(null to "Any", "Action" to "Action", "Comedy" to "Comedy", "Drama" to "Drama", "Fantasy" to "Fantasy", "Romance" to "Romance", "Sci-Fi" to "Sci-Fi"), genre) { genre = it }
        FilterRow("Status", listOf(null to "Any", "RELEASING" to "Airing", "FINISHED" to "Finished", "NOT_YET_RELEASED" to "Upcoming"), status) { status = it }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(AnimeSort.values().toList(), key = { it.name }) { item -> SecondaryButton(if (sort == item) "✓ ${item.label}" else item.label, modifier = Modifier.width(170.dp), onClick = { sort = item }) }
            item { OutlinedTextField(value = yearText, onValueChange = { yearText = it.filter(Char::isDigit).take(4) }, placeholder = { Text("Year", color = MiruroColors.Subtle) }, singleLine = true, modifier = Modifier.width(130.dp)) }
        }
        if (recent.isNotEmpty() && query.isBlank()) {
            SectionTitle("Recent searches")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(recent, key = { it }) { term -> SecondaryButton(term, modifier = Modifier.width(180.dp), onClick = { query = term }) } }
        }
        when (val s = state) {
            null -> StateMessage(if (query.isBlank()) "Start typing to search AniList." else "Searching automatically as you type…")
            is UiState.Loading -> LoadingState("Searching…")
            is UiState.Error -> ErrorState(s.message) { viewModel.search(AnimeSearchFilters(query, format, yearText.toIntOrNull(), genre, status, sort)) }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    StateMessage("No results found.")
                } else {
                    PosterGrid(s.data, onOpenDetails, modifier = Modifier.weight(1f))
                }
            }
        }
    }
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
    BrowseScreen("Movies", state, onRetry = { viewModel.loadMovies(force = true) }, onOpenDetails = onOpenDetails)
}

@Composable
fun SeriesScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadSeries() }
    val state by viewModel.series.collectAsState()
    BrowseScreen("Series", state, onRetry = { viewModel.loadSeries(force = true) }, onOpenDetails = onOpenDetails)
}

@Composable
private fun BrowseScreen(
    title: String,
    state: UiState<List<AnimeItem>>,
    onRetry: () -> Unit,
    onOpenDetails: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle(title)
        when (state) {
            is UiState.Loading -> LoadingState("Loading $title…")
            is UiState.Error -> ErrorState(state.message, onRetry)
            is UiState.Success ->
                if (state.data.isEmpty()) StateMessage("Nothing found.")
                else PosterGrid(state.data, onOpenDetails, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PosterGrid(items: List<AnimeItem>, onOpenDetails: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(items, key = { it.id }) { anime ->
            PosterCard(anime) { onOpenDetails(anime.id) }
        }
    }
}

@Composable
fun FavoritesScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    val ids by viewModel.favoriteIds.collectAsState()
    LaunchedEffect(ids) { viewModel.resolveFavoriteMetadata(ids) }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle("Library")
        if (ids.isEmpty()) {
            StateMessage("Your library is empty. Add anime with \"+ Add to List\".")
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                items(ids.toList(), key = { it }) { id ->
                    val item = viewModel.cachedItem(id)
                    Column {
                        if (item != null) PosterCard(item) { onOpenDetails(id) } else StateMessage("Resolving Anime #$id…")
                        SecondaryButton("Remove", modifier = Modifier.width(160.dp), onClick = { viewModel.toggleFavorite(id) })
                    }
                }
            }
        }
    }
}

@Composable
fun GenresScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    val genres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural")
    var selected by remember { mutableStateOf(genres.first()) }
    var format by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(AnimeSort.POPULARITY) }
    var page by remember { mutableStateOf(1) }
    val state by viewModel.genreResults.collectAsState()
    LaunchedEffect(selected, format, sort, page) { viewModel.loadGenre(selected, format, page, sort) }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle("Genres")
        FilterRow("Type", listOf(null to "All", "TV" to "Series", "MOVIE" to "Movies"), format) { format = it; page = 1 }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(genres, key = { it }) { genre ->
                SecondaryButton(if (genre == selected) "✓ $genre" else genre, modifier = Modifier.width(150.dp), onClick = { selected = genre; page = 1 })
            }
        }
        when (val s = state) {
            null, is UiState.Loading -> LoadingState("Loading $selected anime…")
            is UiState.Error -> ErrorState(s.message) { viewModel.loadGenre(selected) }
            is UiState.Success -> if (s.data.isEmpty()) StateMessage("Nothing found for $selected.") else { PosterGrid(s.data, onOpenDetails, modifier = Modifier.weight(1f)); SecondaryButton("Load page ${page + 1}", modifier = Modifier.width(220.dp), onClick = { page += 1 }) }
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
        if (viewModel != null) SecondaryButton("Clear watch history", modifier = Modifier.width(240.dp), onClick = { viewModel.clearWatchProgress() })
    }
}

@Composable
fun DetailsScreen(
    viewModel: MiruroViewModel,
    animeId: Int,
    onOpenEpisode: (Int, Int, AudioType) -> Unit,
    onPlayEpisode: (Int, Int, AudioType) -> Unit
) {
    LaunchedEffect(animeId) { viewModel.loadDetails(animeId) }
    val state by viewModel.details.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingState("Loading details…")
        is UiState.Error -> ErrorState(s.message) { viewModel.loadDetails(animeId) }
        is UiState.Success -> {
            val details = s.data
            val firstPlayable = details.seasons
                .flatMap { season -> season.episodes.map { season.seasonNumber to it } }
                .firstOrNull { (_, ep) -> ep.sourceCandidates.isNotEmpty() }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    DetailsHero(
                        details = details,
                        inList = details.id in favorites,
                        playLabel = firstPlayable?.let { (season, ep) -> "Play S$season E${ep.episodeNumber}" },
                        onPlay = firstPlayable?.let { (season, ep) -> { onPlayEpisode(season, ep.episodeNumber, ep.audioType) } },
                        onToggleList = { viewModel.toggleFavorite(details.id) }
                    )
                }
                if (details.seasons.isEmpty()) {
                    item { StateMessage("No episodes available.") }
                } else {
                    item { SectionTitle("Episodes") }
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
                            season.episodes.chunked(EPISODE_GRID_COLUMNS),
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
    onToggleList: () -> Unit
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
            "Providers" to episode.sourceCandidates.joinToString { it.provider }.ifBlank { "None" },
            "Playback" to if (episode.sourceCandidates.isNotEmpty()) "Choose a provider or use Auto before playback." else "No playable source is available for this episode."
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
            PrimaryButton("Play", modifier = Modifier.width(180.dp), onClick = onPlay)
        }
    }
}
