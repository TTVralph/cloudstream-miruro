package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

private val FollowupGenres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror",
    "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"
)

@Composable
fun FollowupTopBar(
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
                    listOf(Color.Black, Color.Black.copy(alpha = 0.94f), Color.Black.copy(alpha = 0.72f))
                )
            )
            .padding(horizontal = 58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo()
        Spacer(Modifier.width(38.dp))
        FollowupNavItem("Home", current == "Home", onHome)
        FollowupNavItem("Anime", current == "Anime", onAnime)
        FollowupNavItem("Movies", current == "Movies", onMovies)
        FollowupNavItem("New & Popular", current == "New & Popular", onNewPopular)
        FollowupNavItem("My List", current == "My List", onMyList)
        Spacer(Modifier.weight(1f))
        FollowupHeaderIcon(Icons.Filled.Search, "Search", current == "Search", onSearch)
        Spacer(Modifier.width(14.dp))
        FollowupHeaderIcon(Icons.Filled.Settings, "Settings", current == "Settings", onSettings)
    }
}

@Composable
private fun FollowupNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .width(if (label.length > 8) 150.dp else 90.dp)
            .height(50.dp)
            .scale(if (focused) 1.05f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused && !selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = when {
                selected -> Color.White
                focused -> MiruroColors.AccentSoft
                else -> MiruroColors.Muted
            },
            fontSize = 16.sp,
            fontWeight = if (selected || focused) FontWeight.Black else FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(if (selected) 34.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) MiruroColors.Accent else Color.Transparent)
        )
    }
}

@Composable
private fun FollowupHeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.06f),
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (focused) Color.Black else Color.White)
        }
    }
}

@Composable
fun FollowupHomeScreen(
    viewModel: MiruroViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit
) {
    val state by viewModel.homeRows.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val metadataVersion by viewModel.itemMetadataVersion.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
        is UiState.Loading -> LoadingState("Loading Yume…")
        is UiState.Error -> ErrorState(current.message) { viewModel.loadHome() }
        is UiState.Success -> {
            val rows = current.data
            val hero = rows.asSequence().flatMap { it.items.asSequence() }
                .firstOrNull { !it.bannerUrl.isNullOrBlank() }
                ?: rows.firstOrNull()?.items?.firstOrNull()

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                hero?.let { item ->
                    item(key = "home-hero") {
                        FollowupHomeHero(
                            item = item,
                            inList = item.id in favorites,
                            onWatch = { onOpenDetails(item.id) },
                            onToggleList = { viewModel.toggleFavorite(item.id) },
                            onHeroFocused = { scope.launch { listState.animateScrollToItem(0) } }
                        )
                    }
                }

                var nextIndex = if (hero != null) 1 else 0
                if (unfinished.isNotEmpty()) {
                    val sectionIndex = nextIndex++
                    item(key = "continue-watching") {
                        FollowupHomeSection(
                            onSectionFocused = { scope.launch { listState.animateScrollToItem(sectionIndex) } }
                        ) {
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

                rows.forEach { row ->
                    val sectionIndex = nextIndex++
                    item(key = "row-${row.title}") {
                        FollowupHomeSection(
                            onSectionFocused = { scope.launch { listState.animateScrollToItem(sectionIndex) } }
                        ) {
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
                }
                item { Spacer(Modifier.height(42.dp)) }
            }
        }
    }
}

@Composable
private fun FollowupHomeSection(
    onSectionFocused: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .onFocusChanged { if (it.hasFocus) onSectionFocused() }
            .background(Color.Black)
            .padding(horizontal = 58.dp),
        content = content
    )
}

@Composable
private fun FollowupHomeHero(
    item: AnimeItem,
    inList: Boolean,
    onWatch: () -> Unit,
    onToggleList: () -> Unit,
    onHeroFocused: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(560.dp)
            .background(Color.Black)
    ) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black,
                        0.32f to Color.Black.copy(alpha = 0.92f),
                        0.66f to Color.Black.copy(alpha = 0.26f),
                        1f to Color.Black.copy(alpha = 0.52f)
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.28f),
                        0.55f to Color.Transparent,
                        0.82f to Color.Black.copy(alpha = 0.92f),
                        1f to Color.Black
                    )
                )
            )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 58.dp, bottom = 58.dp)
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
                PrimaryButton(
                    "Watch Now",
                    modifier = Modifier.width(210.dp).onFocusChanged { if (it.hasFocus) onHeroFocused() },
                    onClick = onWatch
                )
                SecondaryButton(
                    if (inList) "In My List" else "+ Add to List",
                    modifier = Modifier.width(210.dp).onFocusChanged { if (it.hasFocus) onHeroFocused() },
                    onClick = onToggleList
                )
            }
        }
    }
}

@Composable
fun FollowupSearchScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
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
            viewModel.search(AnimeSearchFilters(trimmed, format, year, genres.toList(), status, sort))
        }
    }

    if (filtersVisible) {
        FollowupFilterDialog(
            selectedGenres = genres,
            format = format,
            status = status,
            sort = sort,
            year = year,
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
        LazyColumn(
            modifier = Modifier.width(430.dp).fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("Search", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black) }
            item { FollowupSearchBox(query, onQueryChange = { query = it }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FollowupChoice("All", format == null) { format = null }
                    FollowupChoice("Anime", format == "TV") { format = "TV" }
                    FollowupChoice("Movies", format == "MOVIE") { format = "MOVIE" }
                }
            }
            item {
                SecondaryButton(
                    text = buildString {
                        append("Filters")
                        val count = genres.size + listOf(status, year).count { it != null } +
                            if (sort != AnimeSort.SEARCH_MATCH) 1 else 0
                        if (count > 0) append(" ($count)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { filtersVisible = true }
                )
            }
            item {
                FollowupKeyboard(
                    onCharacter = { query += it },
                    onBackspace = { if (query.isNotEmpty()) query = query.dropLast(1) },
                    onSpace = { if (query.isNotEmpty() && !query.endsWith(' ')) query += " " },
                    onClear = { query = "" },
                    onSearch = {
                        viewModel.search(AnimeSearchFilters(query, format, year, genres.toList(), status, sort))
                    }
                )
            }
            if (recent.isNotEmpty()) {
                item {
                    Text("Recent searches", color = MiruroColors.Subtle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recent.take(6), key = { it }) { item -> FollowupChoice(item, false) { query = item } }
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Top Results", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                if (query.isNotBlank()) Text("“${query.trim()}”", color = MiruroColors.Subtle, fontSize = 15.sp)
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
                        StateMessage("No matching anime found. Try another spelling or clear the filters.")
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
                            contentPadding = PaddingValues(bottom = 36.dp),
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
private fun FollowupSearchBox(query: String, onQueryChange: (String) -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else MiruroColors.Card)
            .border(if (focused) 3.dp else 1.dp, if (focused) MiruroColors.Accent else MiruroColors.Border, RoundedCornerShape(12.dp))
            .clickable { requester.requestFocus() }
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
            modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = if (focused) Color.Black else MiruroColors.Subtle, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) Text("Search anime by title", color = if (focused) Color.DarkGray else MiruroColors.Subtle, fontSize = 19.sp)
                        inner()
                    }
                }
            }
        )
    }
}

@Composable
private fun FollowupKeyboard(
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
                        unfocusedBackground = MiruroColors.Card,
                        focusedBackground = Color.White
                    ) { focused ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(character.toString(), color = if (focused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
private fun FollowupChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.height(44.dp).width((text.length * 10 + 42).coerceIn(78, 175).dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent else MiruroColors.Card,
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
private fun FollowupFilterDialog(
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
        title = { Text("Search filters", color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.height(520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    FollowupDialogLabel("Type")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FollowupChoice("All", draftFormat == null) { draftFormat = null } }
                        item { FollowupChoice("Anime", draftFormat == "TV") { draftFormat = "TV" } }
                        item { FollowupChoice("Movies", draftFormat == "MOVIE") { draftFormat = "MOVIE" } }
                    }
                }
                item {
                    FollowupDialogLabel("Status")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FollowupChoice("Any", draftStatus == null) { draftStatus = null } }
                        item { FollowupChoice("Airing", draftStatus == "RELEASING") { draftStatus = "RELEASING" } }
                        item { FollowupChoice("Finished", draftStatus == "FINISHED") { draftStatus = "FINISHED" } }
                        item { FollowupChoice("Upcoming", draftStatus == "NOT_YET_RELEASED") { draftStatus = "NOT_YET_RELEASED" } }
                    }
                }
                item {
                    FollowupDialogLabel("Sort")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimeSort.values().forEach { option ->
                            item { FollowupChoice(option.label, draftSort == option) { draftSort = option } }
                        }
                    }
                }
                item {
                    FollowupDialogLabel("Year")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FollowupChoice("Any", draftYear == null) { draftYear = null } }
                        (0..5).forEach { offset ->
                            val option = currentYear - offset
                            item { FollowupChoice(option.toString(), draftYear == option) { draftYear = option } }
                        }
                    }
                }
                item { FollowupDialogLabel("Genres (${draftGenres.size} selected)") }
                items(FollowupGenres.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { genre ->
                            FollowupChoice(genre, genre in draftGenres) {
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
        containerColor = MiruroColors.Panel
    )
}

@Composable
private fun FollowupDialogLabel(text: String) {
    Text(text, color = MiruroColors.AccentSoft, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 7.dp))
}

@Composable
fun FollowupSettingsScreen(viewModel: MiruroViewModel) {
    val settings by viewModel.settings.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear watch history?", color = Color.White, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "This removes Continue Watching and all locally saved episode progress. This cannot be undone.",
                    color = MiruroColors.Muted,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                PrimaryButton("Clear history", Modifier.width(180.dp)) {
                    viewModel.clearWatchProgress()
                    confirmClear = false
                }
            },
            dismissButton = { SecondaryButton("Cancel", Modifier.width(130.dp)) { confirmClear = false } },
            containerColor = MiruroColors.Panel
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("Settings") }
        item { FollowupSettingsSection("Playback") }
        item {
            FollowupSettingsChoiceRow("Preferred audio", listOf("SUB" to "Sub", "DUB" to "Dub"), settings.preferredAudio.name) {
                viewModel.updatePreferredAudio(AudioType.valueOf(it))
            }
        }
        item {
            FollowupSettingsChoiceRow(
                "Preferred provider",
                listOf("Auto" to "Auto", "zoro" to "Zoro", "animepahe" to "AnimePahe", "gogoanime" to "Gogo", "kiwi" to "Kiwi"),
                settings.preferredProvider,
                150
            ) { viewModel.updatePreferredProvider(it) }
        }
        item {
            FollowupSettingsChoiceRow("Autoplay next episode", listOf("true" to "On", "false" to "Off"), settings.autoPlayNext.toString()) {
                viewModel.updateAutoPlayNext(it == "true")
            }
        }
        item {
            FollowupSettingsChoiceRow("Resume unfinished episodes", listOf("true" to "On", "false" to "Off"), settings.resumePlayback.toString()) {
                viewModel.updateResumePlayback(it == "true")
            }
        }
        item { FollowupSettingsSection("Subtitles") }
        item {
            FollowupSettingsChoiceRow(
                "Preferred language",
                listOf("English" to "English", "Spanish" to "Spanish", "Japanese" to "Japanese"),
                settings.subtitleLanguage,
                160
            ) { viewModel.updateSubtitleLanguage(it) }
        }
        item {
            FollowupSettingsChoiceRow("Default subtitles", listOf("Auto" to "Auto", "Off" to "Off"), settings.subtitleChoice) {
                viewModel.updateSubtitleChoice(it)
            }
        }
        item {
            FollowupSettingsChoiceRow(
                "Subtitle style",
                listOf("Default" to "Default", "Large" to "Large", "High Contrast" to "High Contrast"),
                settings.subtitleStyle,
                180
            ) { viewModel.updateSubtitleStyle(it) }
        }
        item { FollowupSettingsSection("Library & display") }
        item {
            FollowupSettingsChoiceRow(
                "Poster size",
                PosterGridDensity.values().map { it.name to it.name.lowercase(Locale.ROOT).replaceFirstChar { char -> char.titlecase(Locale.ROOT) } },
                settings.posterGridDensity.name,
                165
            ) { viewModel.updatePosterGridDensity(PosterGridDensity.valueOf(it)) }
        }
        item {
            FollowupSettingsChoiceRow("Watched episodes", listOf("false" to "Show", "true" to "Hide"), settings.hideWatchedEpisodes.toString()) {
                viewModel.updateHideWatchedEpisodes(it == "true")
            }
        }
        item {
            FollowupSettingsChoiceRow(
                "My List sorting",
                WatchlistSort.values().map {
                    it.name to it.name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
                },
                settings.watchlistSort.name,
                180
            ) { viewModel.updateWatchlistSort(WatchlistSort.valueOf(it)) }
        }
        item { FollowupSettingsSection("History") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MiruroColors.Card).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Watch history", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Removes Continue Watching and all locally saved progress.", color = MiruroColors.Subtle, fontSize = 13.sp)
                }
                SecondaryButton("Clear watch history", Modifier.width(230.dp)) { confirmClear = true }
            }
        }
        item { FollowupSettingsSection("App & release") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MiruroColors.Card)
                    .border(1.dp, MiruroColors.Border, RoundedCornerShape(12.dp)).padding(18.dp)
            ) {
                Text("Yume TV", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Release channel: Stable", color = MiruroColors.AccentSoft, fontSize = 14.sp)
                Text("Version ${BuildConfig.VERSION_NAME}", color = MiruroColors.Subtle, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("True-black TV interface with local playback and library preferences.", color = MiruroColors.Subtle, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FollowupSettingsSection(title: String) {
    Text(
        title.uppercase(Locale.ROOT),
        color = MiruroColors.AccentSoft,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun FollowupSettingsChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    itemWidth: Int = 145,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MiruroColors.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(280.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(options, key = { it.first }) { (value, text) ->
                FocusableSurface(
                    onClick = { onSelected(value) },
                    modifier = Modifier.width(itemWidth.dp).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    unfocusedBackground = if (selected == value) MiruroColors.Accent else MiruroColors.Panel,
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

@Composable
fun FollowupEpisodeDetailsScreen(
    episode: AnimeEpisode?,
    viewModel: MiruroViewModel,
    onPlay: () -> Unit
) {
    if (episode == null) {
        StateMessage("Episode not found.")
        return
    }
    val settings by viewModel.settings.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val saved = progress.firstOrNull {
        it.animeId == episode.anilistId &&
            it.seasonNumber == episode.seasonNumber &&
            it.episodeNumber == episode.episodeNumber &&
            it.audioType == episode.audioType
    }
    val providers = remember(episode) { listOf("Auto") + episode.sourceCandidates.map { it.provider }.distinct() }
    val availability = remember(episode) {
        episode.sourceCandidates.groupBy { it.provider }.map { (provider, candidates) ->
            "$provider (${candidates.map { it.category.uppercase(Locale.ROOT) }.distinct().joinToString("/")})"
        }.joinToString("  •  ").ifBlank { "None" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 58.dp, end = 58.dp, top = 18.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            episode.thumbnailUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(12.dp))
                )
            }
        }
        item {
            Text(
                "Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(episode.title ?: "Episode ${episode.episodeNumber}", color = MiruroColors.Muted, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MiruroColors.Card).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FollowupMetadata("Runtime", episode.runtimeMinutes?.let { "${it}m" } ?: "Unknown")
                FollowupMetadata("Release date", episode.releaseDate ?: "Unknown")
                FollowupMetadata("Audio type", episode.audioType.name)
                FollowupMetadata("Providers", availability)
                FollowupMetadata(
                    "Playback",
                    if (episode.sourceCandidates.isNotEmpty()) {
                        "Select Auto or a provider, then press Play. Source quality is checked when playback starts."
                    } else {
                        "No playable source is currently available for this episode."
                    }
                )
            }
        }
        if (episode.sourceCandidates.isNotEmpty()) {
            item {
                Text("Provider", color = MiruroColors.AccentSoft, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(providers, key = { it }) { provider ->
                        SecondaryButton(
                            if (provider == settings.preferredProvider) "✓ $provider" else provider,
                            modifier = Modifier.width(170.dp),
                            onClick = { viewModel.updatePreferredProvider(provider) }
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Play episode", modifier = Modifier.width(210.dp), onClick = onPlay)
                    SecondaryButton(
                        if (saved?.watched == true) "Mark unwatched" else "Mark watched",
                        modifier = Modifier.width(220.dp),
                        onClick = { viewModel.setEpisodeWatched(episode, saved?.watched != true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowupMetadata(label: String, value: String) {
    Text(label.uppercase(Locale.ROOT), color = MiruroColors.AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Black)
    BodyText(value)
}
