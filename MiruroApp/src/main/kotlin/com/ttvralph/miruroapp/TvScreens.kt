package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
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
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

private const val TV_EPISODE_COLUMNS = 3
private val TvGenres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror",
    "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"
)

@Composable
fun TvTopBar(
    current: String,
    onHome: () -> Unit,
    onAnime: () -> Unit,
    onMovies: () -> Unit,
    onNewPopular: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MiruroColors.Background.copy(alpha = 0.99f),
                        MiruroColors.Background.copy(alpha = 0.78f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo()
        Spacer(Modifier.width(38.dp))
        TvNavItem("Home", current == "Home", onHome)
        TvNavItem("Anime", current == "Anime", onAnime)
        TvNavItem("Movies", current == "Movies", onMovies)
        TvNavItem("New & Popular", current == "New & Popular", onNewPopular)
        TvNavItem("My List", current == "My List", onMyList)
        Spacer(Modifier.weight(1f))
        TvHeaderIcon(Icons.Filled.Search, "Search", current == "Search", onSearch)
        Spacer(Modifier.width(14.dp))
        TvHeaderIcon(Icons.Filled.Settings, "Settings", current == "Settings", onSettings)
    }
}

@Composable
private fun TvNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val width = if (label.length > 8) 150.dp else 90.dp
    Column(
        modifier = Modifier
            .width(width)
            .height(50.dp)
            .scale(if (focused) 1.06f else 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = when {
                focused -> MiruroColors.AccentSoft
                selected -> Color.White
                else -> MiruroColors.Muted
            },
            fontSize = 16.sp,
            fontWeight = if (selected || focused) FontWeight.Black else FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(if (selected || focused) 34.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (focused) MiruroColors.AccentSoft else MiruroColors.Accent)
        )
    }
}

@Composable
private fun TvHeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.05f),
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (focused) Color.Black else Color.White)
        }
    }
}

@Composable
fun TvHomeScreen(
    viewModel: MiruroViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit
) {
    val state by viewModel.homeRows.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val metadataVersion by viewModel.itemMetadataVersion.collectAsState()

    val unfinished = remember(progress, settings.preferredAudio, metadataVersion) {
        progress
            .filterNot { it.watched }
            .groupBy { Triple(it.animeId, it.seasonNumber, it.episodeNumber) }
            .mapNotNull { (_, entries) ->
                entries.firstOrNull { it.audioType == settings.preferredAudio }
                    ?: entries.maxByOrNull { it.updatedAtMs }
            }
            .sortedByDescending { it.updatedAtMs }
            .take(20)
    }
    LaunchedEffect(progress.map { it.animeId }.toSet()) {
        viewModel.resolveProgressMetadata(progress)
    }

    when (val current = state) {
        is UiState.Loading -> LoadingState("Loading AniStream…")
        is UiState.Error -> ErrorState(current.message) { viewModel.loadHome() }
        is UiState.Success -> {
            val rows = current.data
            val hero = rows.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { !it.bannerUrl.isNullOrBlank() }
                ?: rows.firstOrNull()?.items?.firstOrNull()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                hero?.let { item ->
                    item {
                        TvHomeHero(
                            item = item,
                            inList = item.id in favorites,
                            onWatch = { onOpenDetails(item.id) },
                            onToggleList = { viewModel.toggleFavorite(item.id) }
                        )
                    }
                }
                if (unfinished.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 58.dp)) {
                            SectionTitle("Continue Watching", "RESUME")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp)
                            ) {
                                items(unfinished, key = { it.key }) { entry ->
                                    val cached = viewModel.cachedItem(entry.animeId)
                                    val item = cached ?: AnimeItem(
                                        entry.animeId,
                                        "Anime #${entry.animeId}",
                                        null,
                                        null,
                                        AnimeType.UNKNOWN
                                    )
                                    Column(Modifier.width(360.dp)) {
                                        LandscapeCard(
                                            item = item,
                                            width = 360.dp,
                                            height = 190.dp,
                                            progressPercent = entry.percent,
                                            onClick = { onPlayProgress(entry) }
                                        )
                                        Text(
                                            "Resume S${entry.seasonNumber} E${entry.episodeNumber} • ${(entry.percent * 100).toInt()}%",
                                            color = MiruroColors.AccentSoft,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                items(rows, key = { it.title }) { row ->
                    Column(Modifier.padding(horizontal = 58.dp)) {
                        SectionTitle(row.title, if (row.title == "Trending Now") "HOT" else null)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp)
                        ) {
                            items(row.items, key = { it.id }) { anime ->
                                PosterCard(
                                    item = anime,
                                    width = when (settings.posterGridDensity) {
                                        PosterGridDensity.COMPACT -> 138.dp
                                        PosterGridDensity.COMFORTABLE -> 166.dp
                                        PosterGridDensity.LARGE -> 210.dp
                                    },
                                    onClick = { onOpenDetails(anime.id) }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(38.dp)) }
            }
        }
    }
}

@Composable
private fun TvHomeHero(
    item: AnimeItem,
    inList: Boolean,
    onWatch: () -> Unit,
    onToggleList: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .background(MiruroColors.Background)
    ) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MiruroColors.Background,
                            MiruroColors.Background.copy(alpha = 0.78f),
                            Color.Transparent,
                            MiruroColors.Background.copy(alpha = 0.12f)
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Transparent,
                            MiruroColors.Background.copy(alpha = 0.94f),
                            MiruroColors.Background
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 58.dp, bottom = 54.dp)
                .width(700.dp)
        ) {
            Badge("FEATURED", container = MiruroColors.Accent2, content = Color.Black)
            Spacer(Modifier.height(14.dp))
            Text(
                item.title,
                color = Color.White,
                fontSize = 48.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.score?.let {
                    RatingLabel(String.format(Locale.US, "%.1f", it / 10f))
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    listOfNotNull(item.year?.toString(), item.type.name.takeIf { it != "UNKNOWN" })
                        .joinToString(" • "),
                    color = MiruroColors.Muted,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton("Watch Now", modifier = Modifier.width(210.dp), onClick = onWatch)
                SecondaryButton(
                    if (inList) "In My List" else "+ Add to List",
                    modifier = Modifier.width(210.dp),
                    onClick = onToggleList
                )
            }
        }
    }
}

@Composable
fun TvSearchScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<String?>(null) }
    var genres by remember { mutableStateOf(emptySet<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(AnimeSort.SEARCH_MATCH) }
    var year by remember { mutableStateOf<Int?>(null) }
    var filtersVisible by remember { mutableStateOf(false) }
    val state by viewModel.searchResults.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(query, format, genres, status, sort, year) {
        val trimmed = query.trim()
        val noFilters = format == null && genres.isEmpty() && status == null && year == null
        if (trimmed.isBlank() && noFilters) {
            viewModel.clearSearch()
        } else {
            delay(350)
            viewModel.search(
                AnimeSearchFilters(
                    query = trimmed,
                    format = format,
                    year = year,
                    genres = genres.toList(),
                    status = status,
                    sort = sort
                )
            )
        }
    }

    if (filtersVisible) {
        TvBrowseFilterDialog(
            title = "Search filters",
            selectedGenres = genres,
            format = format,
            status = status,
            sort = sort,
            year = year,
            allowBestMatch = true,
            onDismiss = { filtersVisible = false },
            onApply = { nextGenres, nextFormat, nextStatus, nextSort, nextYear ->
                genres = nextGenres
                format = nextFormat
                status = nextStatus
                sort = nextSort
                year = nextYear
                filtersVisible = false
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(Modifier.width(430.dp)) {
            Text("Search", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            TvSearchBox(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvCompactChoice("All", format == null) { format = null }
                TvCompactChoice("Anime", format == "TV") { format = "TV" }
                TvCompactChoice("Movies", format == "MOVIE") { format = "MOVIE" }
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = buildString {
                    append("Filters")
                    val count = genres.size + listOf(status, year).count { it != null } + if (sort != AnimeSort.SEARCH_MATCH) 1 else 0
                    if (count > 0) append(" ($count)")
                },
                modifier = Modifier.fillMaxWidth(),
                onClick = { filtersVisible = true }
            )
            Spacer(Modifier.height(18.dp))
            TvKeyboard(
                onCharacter = { query += it },
                onBackspace = { if (query.isNotEmpty()) query = query.dropLast(1) },
                onSpace = { if (query.isNotEmpty() && !query.endsWith(' ')) query += " " },
                onClear = { query = "" },
                onSearch = {
                    viewModel.search(
                        AnimeSearchFilters(query, format, year, genres.toList(), status, sort)
                    )
                }
            )
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Recent", color = MiruroColors.Subtle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent.take(6), key = { it }) { item ->
                        TvCompactChoice(item, false) { query = item }
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Top Results", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                if (query.isNotBlank()) {
                    Text("“${query.trim()}”", color = MiruroColors.Subtle, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            when (val current = state) {
                null -> StateMessage("Type a title or use the TV keyboard to search.")
                is UiState.Loading -> LoadingState("Searching anime…")
                is UiState.Error -> ErrorState(current.message) {
                    viewModel.search(AnimeSearchFilters(query, format, year, genres.toList(), status, sort))
                }
                is UiState.Success -> {
                    if (current.data.isEmpty()) {
                        StateMessage("No matching anime found. Try fewer filters or another title.")
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
                            contentPadding = PaddingValues(bottom = 28.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(current.data, key = { it.id }) { anime ->
                                PosterCard(anime) { onOpenDetails(anime.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSearchBox(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else MiruroColors.Card)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MiruroColors.Accent else MiruroColors.Border,
                RoundedCornerShape(12.dp)
            )
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = if (focused) Color.Black else Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (focused) Color.Black else MiruroColors.Subtle,
                        modifier = Modifier.size(25.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) {
                            Text(
                                "Search anime by title",
                                color = if (focused) Color.DarkGray else MiruroColors.Subtle,
                                fontSize = 19.sp
                            )
                        }
                        inner()
                    }
                }
            }
        )
    }
}

@Composable
private fun TvKeyboard(
    onCharacter: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { character ->
                    FocusableSurface(
                        onClick = { onCharacter(character.toString()) },
                        modifier = Modifier.size(width = 36.dp, height = 42.dp),
                        shape = RoundedCornerShape(7.dp),
                        unfocusedBackground = Color.White.copy(alpha = 0.07f),
                        focusedBackground = Color.White
                    ) { focused ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                character.toString(),
                                color = if (focused) Color.Black else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("⌫", Modifier.width(74.dp), onBackspace)
            SecondaryButton("Space", Modifier.width(126.dp), onSpace)
            SecondaryButton("Clear", Modifier.width(102.dp), onClear)
            PrimaryButton("Search", Modifier.width(112.dp), onSearch)
        }
    }
}

@Composable
private fun TvCompactChoice(text: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
fun TvGenresScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var selectedGenres by remember { mutableStateOf(emptySet<String>()) }
    var format by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(AnimeSort.POPULARITY) }
    var year by remember { mutableStateOf<Int?>(null) }
    var page by remember { mutableStateOf(1) }
    var dialogVisible by remember { mutableStateOf(false) }
    val state by viewModel.genreResults.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(selectedGenres, format, status, sort, year, page) {
        viewModel.loadGenre(selectedGenres.toList(), format, page, sort, status, year)
    }

    if (dialogVisible) {
        TvBrowseFilterDialog(
            title = "Browse filters",
            selectedGenres = selectedGenres,
            format = format,
            status = status,
            sort = sort,
            year = year,
            allowBestMatch = false,
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
            Text("New & Popular", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
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
                if (current.data.isEmpty()) {
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
                        items(current.data, key = { it.id }) { anime ->
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
private fun TvBrowseFilterDialog(
    title: String,
    selectedGenres: Set<String>,
    format: String?,
    status: String?,
    sort: AnimeSort,
    year: Int?,
    allowBestMatch: Boolean,
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
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(
                modifier = Modifier.height(540.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    TvDialogLabel("Type")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { TvCompactChoice("All", draftFormat == null) { draftFormat = null } }
                        item { TvCompactChoice("Anime", draftFormat == "TV") { draftFormat = "TV" } }
                        item { TvCompactChoice("Movies", draftFormat == "MOVIE") { draftFormat = "MOVIE" } }
                    }
                }
                item {
                    TvDialogLabel("Status")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { TvCompactChoice("Any", draftStatus == null) { draftStatus = null } }
                        item { TvCompactChoice("Airing", draftStatus == "RELEASING") { draftStatus = "RELEASING" } }
                        item { TvCompactChoice("Finished", draftStatus == "FINISHED") { draftStatus = "FINISHED" } }
                        item { TvCompactChoice("Upcoming", draftStatus == "NOT_YET_RELEASED") { draftStatus = "NOT_YET_RELEASED" } }
                    }
                }
                item {
                    TvDialogLabel("Sort")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimeSort.values()
                            .filter { allowBestMatch || it != AnimeSort.SEARCH_MATCH }
                            .forEach { option ->
                                item {
                                    TvCompactChoice(option.label, draftSort == option) { draftSort = option }
                                }
                            }
                    }
                }
                item {
                    TvDialogLabel("Year")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { TvCompactChoice("Any", draftYear == null) { draftYear = null } }
                        (0..5).forEach { offset ->
                            val option = currentYear - offset
                            item { TvCompactChoice(option.toString(), draftYear == option) { draftYear = option } }
                        }
                    }
                }
                item {
                    TvDialogLabel("Genres (${draftGenres.size} selected)")
                }
                items(TvGenres.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { genre ->
                            TvCompactChoice(genre, genre in draftGenres) {
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
        dismissButton = {
            SecondaryButton("Cancel", Modifier.width(130.dp), onDismiss)
        },
        containerColor = MiruroColors.Panel
    )
}

@Composable
private fun TvDialogLabel(text: String) {
    Text(
        text,
        color = MiruroColors.AccentSoft,
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(bottom = 7.dp)
    )
}

@Composable
fun TvSettingsScreen(viewModel: MiruroViewModel) {
    val settings by viewModel.settings.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("Settings") }
        item { TvSettingsSection("Playback") }
        item {
            TvSettingsChoiceRow(
                "Preferred audio",
                listOf("SUB" to "Sub", "DUB" to "Dub"),
                settings.preferredAudio.name
            ) { viewModel.updatePreferredAudio(AudioType.valueOf(it)) }
        }
        item {
            TvSettingsChoiceRow(
                "Preferred provider",
                listOf(
                    "Auto" to "Auto",
                    "zoro" to "Zoro",
                    "animepahe" to "AnimePahe",
                    "gogoanime" to "Gogo",
                    "kiwi" to "Kiwi"
                ),
                settings.preferredProvider,
                itemWidth = 150
            ) { viewModel.updatePreferredProvider(it) }
        }
        item {
            TvSettingsChoiceRow(
                "Autoplay next episode",
                listOf("true" to "On", "false" to "Off"),
                settings.autoPlayNext.toString()
            ) { viewModel.updateAutoPlayNext(it == "true") }
        }
        item {
            TvSettingsChoiceRow(
                "Resume unfinished episodes",
                listOf("true" to "On", "false" to "Off"),
                settings.resumePlayback.toString()
            ) { viewModel.updateResumePlayback(it == "true") }
        }
        item { TvSettingsSection("Subtitles") }
        item {
            TvSettingsChoiceRow(
                "Preferred language",
                listOf("English" to "English", "Spanish" to "Spanish", "Japanese" to "Japanese"),
                settings.subtitleLanguage,
                itemWidth = 160
            ) { viewModel.updateSubtitleLanguage(it) }
        }
        item {
            TvSettingsChoiceRow(
                "Default subtitles",
                listOf("Auto" to "Auto", "Off" to "Off"),
                settings.subtitleChoice
            ) { viewModel.updateSubtitleChoice(it) }
        }
        item {
            TvSettingsChoiceRow(
                "Subtitle style",
                listOf("Default" to "Default", "Large" to "Large", "High Contrast" to "High Contrast"),
                settings.subtitleStyle,
                itemWidth = 180
            ) { viewModel.updateSubtitleStyle(it) }
        }
        item { TvSettingsSection("Library & display") }
        item {
            TvSettingsChoiceRow(
                "Poster size",
                PosterGridDensity.values().map { it.name to it.name.lowercase(Locale.ROOT).replaceFirstChar { char -> char.titlecase(Locale.ROOT) } },
                settings.posterGridDensity.name,
                itemWidth = 165
            ) { viewModel.updatePosterGridDensity(PosterGridDensity.valueOf(it)) }
        }
        item {
            TvSettingsChoiceRow(
                "Watched episodes",
                listOf("false" to "Show", "true" to "Hide"),
                settings.hideWatchedEpisodes.toString()
            ) { viewModel.updateHideWatchedEpisodes(it == "true") }
        }
        item {
            TvSettingsChoiceRow(
                "My List sorting",
                WatchlistSort.values().map {
                    it.name to it.name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
                },
                settings.watchlistSort.name,
                itemWidth = 180
            ) { viewModel.updateWatchlistSort(WatchlistSort.valueOf(it)) }
        }
        item { TvSettingsSection("History") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Watch history", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Removes Continue Watching and all locally saved progress.", color = MiruroColors.Subtle, fontSize = 13.sp)
                }
                SecondaryButton("Clear watch history", Modifier.width(230.dp)) { viewModel.clearWatchProgress() }
            }
        }
        item { TvSettingsSection("App & release") }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiruroColors.Card)
                    .border(1.dp, MiruroColors.Border, RoundedCornerShape(12.dp))
                    .padding(18.dp)
            ) {
                Text("AniStream TV", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Release channel: Stable", color = MiruroColors.AccentSoft, fontSize = 14.sp)
                Text("Version ${BuildConfig.VERSION_NAME}", color = MiruroColors.Subtle, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("The interface uses a TV-optimized dark theme.", color = MiruroColors.Subtle, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TvSettingsSection(title: String) {
    Text(
        title.uppercase(Locale.ROOT),
        color = MiruroColors.AccentSoft,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun TvSettingsChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    itemWidth: Int = 145,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiruroColors.Card)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(280.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(options, key = { it.first }) { (value, text) ->
                FocusableSurface(
                    onClick = { onSelected(value) },
                    modifier = Modifier.width(itemWidth.dp).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    unfocusedBackground = if (selected == value) MiruroColors.Accent else Color.White.copy(alpha = 0.06f),
                    focusedBackground = Color.White
                ) { focused ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selected == value) "✓ $text" else text,
                            color = if (focused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private data class TvEpisodeTarget(val seasonNumber: Int, val episode: AnimeEpisode)
private enum class TvPlayAction { PLAY, RESUME, NEXT, REWATCH }
private data class TvSmartPlay(val target: TvEpisodeTarget, val action: TvPlayAction)

@Composable
fun TvDetailsScreen(
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

    when (val current = state) {
        is UiState.Loading -> LoadingState("Loading details…")
        is UiState.Error -> ErrorState(current.message) { viewModel.loadDetails(animeId) }
        is UiState.Success -> {
            val details = current.data
            val uniqueAll = remember(details, settings.preferredAudio) {
                uniqueEpisodeTargets(details, settings.preferredAudio, playableOnly = false)
            }
            val playable = remember(details, settings.preferredAudio) {
                uniqueEpisodeTargets(details, settings.preferredAudio, playableOnly = true)
            }
            val seasonIds = details.seasons.map { it.id }.toSet()
            val relevantProgress = progress.filter { it.animeId in seasonIds }
            val watchedKeys = relevantProgress
                .filter { it.watched }
                .map { Triple(it.animeId, it.seasonNumber, it.episodeNumber) }
                .toSet()
            val smartPlay = smartPlayTarget(playable, relevantProgress, watchedKeys)
            val watchedCount = uniqueAll.count {
                Triple(it.episode.anilistId, it.seasonNumber, it.episode.episodeNumber) in watchedKeys
            }
            val total = uniqueAll.size
            val completion = if (total > 0) watchedCount * 100 / total else 0

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    TvDetailsHero(
                        details = details,
                        inList = details.id in favorites,
                        smartPlay = smartPlay,
                        onBack = onBack,
                        onPlay = { target ->
                            onPlayEpisode(target.seasonNumber, target.episode.episodeNumber, target.episode.audioType)
                        },
                        onToggleList = { viewModel.toggleFavorite(details.id) }
                    )
                }
                if (smartPlay != null && smartPlay.action != TvPlayAction.PLAY) {
                    item {
                        Column(Modifier.padding(horizontal = 58.dp)) {
                            SectionTitle(if (smartPlay.action == TvPlayAction.RESUME) "Continue Watching" else "Up Next")
                            TvUpNextCard(smartPlay) {
                                onPlayEpisode(
                                    smartPlay.target.seasonNumber,
                                    smartPlay.target.episode.episodeNumber,
                                    smartPlay.target.episode.audioType
                                )
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 58.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Episodes", "$watchedCount/$total WATCHED • $completion% COMPLETE")
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { TvCompactChoice("All", audioFilter == null) { audioFilter = null } }
                            item { TvCompactChoice("Sub", audioFilter == AudioType.SUB) { audioFilter = AudioType.SUB } }
                            item { TvCompactChoice("Dub", audioFilter == AudioType.DUB) { audioFilter = AudioType.DUB } }
                        }
                    }
                }
                details.seasons.forEach { season ->
                    val displayed = season.episodes
                        .filter { audioFilter == null || it.audioType == audioFilter }
                        .filterNot { episode ->
                            settings.hideWatchedEpisodes && Triple(
                                episode.anilistId,
                                season.seasonNumber,
                                episode.episodeNumber
                            ) in watchedKeys
                        }
                    if (details.seasons.size > 1) {
                        item {
                            Text(
                                "Season ${season.seasonNumber}: ${season.title}",
                                color = MiruroColors.AccentSoft,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 58.dp, vertical = 16.dp)
                            )
                        }
                    }
                    items(displayed.chunked(TV_EPISODE_COLUMNS)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            row.forEach { episode ->
                                val episodeProgress = relevantProgress
                                    .filter {
                                        it.animeId == episode.anilistId &&
                                            it.seasonNumber == season.seasonNumber &&
                                            it.episodeNumber == episode.episodeNumber
                                    }
                                    .maxByOrNull { it.updatedAtMs }
                                TvEpisodeCard(
                                    episode = episode,
                                    progress = episodeProgress,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onOpenEpisode(
                                            season.seasonNumber,
                                            episode.episodeNumber,
                                            episode.audioType
                                        )
                                    }
                                )
                            }
                            repeat(TV_EPISODE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                item { Spacer(Modifier.height(38.dp)) }
            }
        }
    }
}

private fun uniqueEpisodeTargets(
    details: AnimeDetails,
    preferredAudio: AudioType,
    playableOnly: Boolean
): List<TvEpisodeTarget> = details.seasons.flatMap { season ->
    season.episodes
        .groupBy { it.episodeNumber }
        .mapNotNull { (_, versions) ->
            val candidates = if (playableOnly) versions.filter { it.sourceCandidates.isNotEmpty() } else versions
            val selected = candidates.firstOrNull { it.audioType == preferredAudio }
                ?: candidates.firstOrNull { it.sourceCandidates.isNotEmpty() }
                ?: candidates.firstOrNull()
            selected?.let { TvEpisodeTarget(season.seasonNumber, it) }
        }
}.sortedWith(compareBy<TvEpisodeTarget> { it.seasonNumber }.thenBy { it.episode.episodeNumber })

private fun smartPlayTarget(
    playable: List<TvEpisodeTarget>,
    progress: List<WatchProgress>,
    watchedKeys: Set<Triple<Int, Int, Int>>
): TvSmartPlay? {
    if (playable.isEmpty()) return null
    val partial = progress
        .filter { !it.watched && it.positionMs > 0L }
        .maxByOrNull { it.updatedAtMs }
    if (partial != null) {
        val target = playable.firstOrNull {
            it.episode.anilistId == partial.animeId &&
                it.seasonNumber == partial.seasonNumber &&
                it.episode.episodeNumber == partial.episodeNumber &&
                it.episode.audioType == partial.audioType
        } ?: playable.firstOrNull {
            it.episode.anilistId == partial.animeId &&
                it.seasonNumber == partial.seasonNumber &&
                it.episode.episodeNumber == partial.episodeNumber
        }
        if (target != null) return TvSmartPlay(target, TvPlayAction.RESUME)
    }

    val next = playable.firstOrNull {
        Triple(it.episode.anilistId, it.seasonNumber, it.episode.episodeNumber) !in watchedKeys
    }
    return when {
        next != null && watchedKeys.isNotEmpty() -> TvSmartPlay(next, TvPlayAction.NEXT)
        next != null -> TvSmartPlay(next, TvPlayAction.PLAY)
        else -> TvSmartPlay(playable.first(), TvPlayAction.REWATCH)
    }
}

@Composable
private fun TvDetailsHero(
    details: AnimeDetails,
    inList: Boolean,
    smartPlay: TvSmartPlay?,
    onBack: () -> Unit,
    onPlay: (TvEpisodeTarget) -> Unit,
    onToggleList: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(510.dp).background(MiruroColors.Background)) {
        AsyncImage(
            model = details.bannerUrl ?: details.posterUrl,
            contentDescription = details.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(MiruroColors.Background, MiruroColors.Background.copy(alpha = 0.82f), Color.Transparent)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, MiruroColors.Background))
            )
        )
        SecondaryButton("Back", Modifier.align(Alignment.TopStart).padding(36.dp).width(120.dp), onBack)
        Column(Modifier.align(Alignment.BottomStart).padding(start = 58.dp, bottom = 42.dp).width(760.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                details.rating?.let {
                    RatingLabel(it)
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    listOfNotNull(details.year?.toString(), "${details.seasons.size} season${if (details.seasons.size == 1) "" else "s"}")
                        .joinToString(" • "),
                    color = MiruroColors.Muted,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                details.title,
                color = Color.White,
                fontSize = 46.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(details.genres.take(5), key = { it }) { GenreChip(it) }
            }
            Spacer(Modifier.height(12.dp))
            BodyText(details.description ?: "No synopsis available.", maxLines = 3)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                smartPlay?.let { smart ->
                    val label = when (smart.action) {
                        TvPlayAction.PLAY -> "Play S${smart.target.seasonNumber} E${smart.target.episode.episodeNumber}"
                        TvPlayAction.RESUME -> "Resume S${smart.target.seasonNumber} E${smart.target.episode.episodeNumber}"
                        TvPlayAction.NEXT -> "Play Next: S${smart.target.seasonNumber} E${smart.target.episode.episodeNumber}"
                        TvPlayAction.REWATCH -> "Rewatch S${smart.target.seasonNumber} E${smart.target.episode.episodeNumber}"
                    }
                    PrimaryButton(label, Modifier.width(270.dp)) { onPlay(smart.target) }
                }
                SecondaryButton(
                    if (inList) "In My List" else "+ Add to List",
                    Modifier.width(210.dp),
                    onToggleList
                )
            }
        }
    }
}

@Composable
private fun TvUpNextCard(smartPlay: TvSmartPlay, onPlay: () -> Unit) {
    val episode = smartPlay.target.episode
    FocusableSurface(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().height(180.dp),
        unfocusedBackground = MiruroColors.Card
    ) { focused ->
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(320.dp).fillMaxHeight().background(MiruroColors.CardHigh)) {
                episode.thumbnailUrl?.let {
                    AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = MiruroColors.Accent,
                    trackColor = Color.Transparent
                )
            }
            Column(Modifier.weight(1f).padding(22.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    if (smartPlay.action == TvPlayAction.RESUME) "CONTINUE WATCHING" else "RECOMMENDED NEXT EPISODE",
                    color = if (focused) Color.Black else MiruroColors.AccentSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Season ${smartPlay.target.seasonNumber} • Episode ${episode.episodeNumber}",
                    color = if (focused) Color.Black else Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    episode.title ?: "Episode ${episode.episodeNumber}",
                    color = if (focused) Color.DarkGray else MiruroColors.Subtle,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvEpisodeCard(
    episode: AnimeEpisode,
    progress: WatchProgress?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FocusableSurface(onClick = onClick, modifier = modifier, unfocusedBackground = MiruroColors.Card) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MiruroColors.CardHigh)) {
                if (episode.thumbnailUrl != null) {
                    AsyncImage(episode.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        episode.episodeNumber.toString(),
                        color = Color.White.copy(alpha = 0.15f),
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
                    )
                )
                TvEpisodeBadge(
                    episode.audioType.name,
                    container = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                )
                progress?.let {
                    TvEpisodeBadge(
                        if (it.watched) "WATCHED" else "${(it.percent * 100).toInt()}%",
                        container = if (it.watched) MiruroColors.Accent else Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    )
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    "E${episode.episodeNumber} • ${episode.audioType.name}",
                    color = MiruroColors.AccentSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    episode.title ?: "Episode ${episode.episodeNumber}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvEpisodeBadge(
    text: String,
    container: Color,
    modifier: Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}
