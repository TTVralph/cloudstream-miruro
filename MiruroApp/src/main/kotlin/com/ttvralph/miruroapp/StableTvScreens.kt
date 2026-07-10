package com.ttvralph.miruroapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeSearchFilters
import com.ttvralph.miruroapp.data.AnimeSort
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.BodyText
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.GenreChip
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage
import java.util.Locale
import kotlinx.coroutines.delay

private val StableSafeX = 45.dp
private val StableNavHeight = 70.dp
private val StableCardWidth = 228.dp
private val StableCardHeight = 128.dp
private val StableCardGap = 12.dp
private val StableRadius = 4.dp

private data class StableResumeItem(val anime: AnimeItem, val progress: WatchProgress)
private data class StableEpisodeTarget(val season: Int, val episode: AnimeEpisode)

@Composable
fun StableTopBar(
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
            .height(76.dp)
            .background(Color.Black)
            .padding(horizontal = StableSafeX),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo()
        Spacer(Modifier.width(30.dp))
        StableNavText("Home", current == "Home", onHome)
        StableNavText("Anime", current == "Anime", onAnime)
        StableNavText("Movies", current == "Movies", onMovies)
        StableNavText("New & Popular", current == "New & Popular", onNewPopular, 132.dp)
        StableNavText("My List", current == "My List", onMyList)
        Spacer(Modifier.weight(1f))
        StableNavIcon(Icons.Filled.Search, "Search", current == "Search", onSearch)
        Spacer(Modifier.width(10.dp))
        StableNavIcon(Icons.Filled.Settings, "Settings", current == "Settings", onSettings)
    }
}

@Composable
private fun StableNavText(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    width: Dp = 82.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .width(width)
            .height(46.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = when {
                focused -> Color.White
                selected -> Color.White
                else -> Color.White.copy(alpha = 0.68f)
            },
            fontSize = 14.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(if (selected) 26.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) MiruroColors.Accent else Color.Transparent)
        )
    }
}

@Composable
private fun StableNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent.copy(alpha = 0.35f) else Color.Transparent,
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (focused) Color.Black else Color.White)
        }
    }
}

@Composable
fun StableHomeScreen(
    viewModel: MiruroViewModel,
    onHome: () -> Unit,
    onAnime: () -> Unit,
    onMovies: () -> Unit,
    onNewPopular: () -> Unit,
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
    val listState = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }

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

    when (val current = state) {
        is UiState.Loading -> LoadingState("Loading AniStream…")
        is UiState.Error -> ErrorState(current.message) { viewModel.loadHome() }
        is UiState.Success -> {
            val rows = current.data
            val initial = rows.asSequence().flatMap { it.items.asSequence() }
                .firstOrNull { !it.bannerUrl.isNullOrBlank() }
                ?: rows.firstOrNull()?.items?.firstOrNull()

            if (initial == null) {
                StateMessage("The home catalogue did not return any titles.")
                return
            }

            val resumeItems = remember(unfinished, metadataVersion) {
                unfinished.map { saved ->
                    StableResumeItem(
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
            val allItems = remember(rows, resumeItems) {
                (listOf(initial) + resumeItems.map { it.anime } + rows.flatMap { it.items }).distinctBy { it.id }
            }
            var activeRow by remember(initial.id) { mutableStateOf(-1) }
            var pendingHeroId by remember(initial.id) { mutableStateOf(initial.id) }
            var activeHeroId by remember(initial.id) { mutableStateOf(initial.id) }
            val activeHero = allItems.firstOrNull { it.id == activeHeroId } ?: initial

            LaunchedEffect(pendingHeroId) {
                delay(260)
                if (activeHeroId != pendingHeroId) activeHeroId = pendingHeroId
            }
            LaunchedEffect(activeRow) {
                if (activeRow < 0) {
                    listState.scrollToItem(0)
                } else {
                    listState.scrollToItem(activeRow + 1)
                }
            }
            LaunchedEffect(Unit) {
                delay(220)
                runCatching { playFocus.requestFocus() }
            }

            val heroAlpha by animateFloatAsState(
                targetValue = if (activeRow < 0) 1f else 0f,
                animationSpec = tween(170),
                label = "stableHeroAlpha"
            )
            val backdropDim by animateFloatAsState(
                targetValue = if (activeRow < 0) 0.28f else 0.82f,
                animationSpec = tween(180),
                label = "stableBackdropDim"
            )

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                StableBackdrop(activeHero, backdropDim)
                StableHero(
                    item = activeHero,
                    inList = activeHero.id in favorites,
                    alpha = heroAlpha,
                    playFocus = playFocus,
                    firstRowFocus = firstRowFocus,
                    onFocused = {
                        activeRow = -1
                        pendingHeroId = activeHero.id
                    },
                    onPlay = { onOpenDetails(activeHero.id) },
                    onList = { viewModel.toggleFavorite(activeHero.id) }
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(top = StableNavHeight),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    item("stable-hero-space") { Spacer(Modifier.height(405.dp)) }
                    var rowNumber = 0

                    if (resumeItems.isNotEmpty()) {
                        val row = rowNumber++
                        item("stable-resume") {
                            StableHomeRow("Continue Watching") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(StableCardGap),
                                    contentPadding = PaddingValues(horizontal = StableSafeX, vertical = 12.dp)
                                ) {
                                    itemsIndexed(resumeItems, key = { _, it -> it.progress.key }) { index, item ->
                                        StableLandscapeCard(
                                            item = item.anime,
                                            progress = item.progress.percent,
                                            supportingText = "S${item.progress.seasonNumber} E${item.progress.episodeNumber}",
                                            focusRequester = if (index == 0) firstRowFocus else null,
                                            upFocusRequester = if (index == 0) playFocus else null,
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
                        item("stable-row-${homeRow.title}") {
                            StableHomeRow(homeRow.title) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(if (homeRow.title.equals("Trending Now", true)) 18.dp else StableCardGap),
                                    contentPadding = PaddingValues(horizontal = StableSafeX, vertical = 12.dp)
                                ) {
                                    itemsIndexed(homeRow.items, key = { _, it -> it.id }) { index, anime ->
                                        val requester = if (first && index == 0) firstRowFocus else null
                                        val upRequester = if (first && index == 0) playFocus else null
                                        if (homeRow.title.equals("Trending Now", true) && index < 10) {
                                            StableRankedCard(
                                                rank = index + 1,
                                                item = anime,
                                                focusRequester = requester,
                                                upFocusRequester = upRequester,
                                                onFocused = {
                                                    if (activeRow != row) activeRow = row
                                                    pendingHeroId = anime.id
                                                },
                                                onClick = { onOpenDetails(anime.id) }
                                            )
                                        } else {
                                            StableLandscapeCard(
                                                item = anime,
                                                focusRequester = requester,
                                                upFocusRequester = upRequester,
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
                }

                StableHomeNav(
                    solid = activeRow >= 0,
                    onHome = onHome,
                    onAnime = onAnime,
                    onMovies = onMovies,
                    onNewPopular = onNewPopular,
                    onMyList = onMyList,
                    onSearch = onSearch,
                    onSettings = onSettings,
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(10f)
                )
            }
        }
    }
}

@Composable
private fun StableBackdrop(item: AnimeItem, dim: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black,
                        Color.Black.copy(alpha = 0.95f),
                        Color.Black.copy(alpha = 0.70f),
                        Color.Black.copy(alpha = 0.18f),
                        Color.Black.copy(alpha = 0.10f)
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.40f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.58f),
                        Color.Black.copy(alpha = 0.95f),
                        Color.Black
                    )
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

@Composable
private fun StableHero(
    item: AnimeItem,
    inList: Boolean,
    alpha: Float,
    playFocus: FocusRequester,
    firstRowFocus: FocusRequester,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onList: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(start = StableSafeX, top = 156.dp)
            .width(520.dp)
            .graphicsLayer { this.alpha = alpha }
            .zIndex(4f)
    ) {
        Text(
            "ANISTREAM  •  FEATURED",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            item.title,
            color = Color.White,
            fontSize = 40.sp,
            lineHeight = 43.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.score?.let {
                Text("$it% Match", color = Color(0xFF46D369), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                listOfNotNull(
                    item.year?.toString(),
                    item.type.name.takeIf { it != "UNKNOWN" }
                        ?.lowercase(Locale.ROOT)
                        ?.replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
                ).joinToString("   "),
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StableHeroButton(
                text = "▶   Play",
                primary = true,
                modifier = Modifier.width(152.dp).focusRequester(playFocus).focusProperties { down = firstRowFocus },
                onFocused = onFocused,
                onClick = onPlay
            )
            StableHeroButton(
                text = if (inList) "✓   My List" else "+   My List",
                primary = false,
                modifier = Modifier.width(174.dp).focusProperties { down = firstRowFocus },
                onFocused = onFocused,
                onClick = onList
            )
        }
    }
}

@Composable
private fun StableHeroButton(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "stableHeroButton")
    Box(
        modifier = modifier
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    primary -> Color.White
                    focused -> Color.White
                    else -> Color(0xFF4A4A4A).copy(alpha = 0.86f)
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .onFocusChanged { if (it.hasFocus) onFocused() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (primary || focused) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StableHomeRow(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = StableSafeX)
        )
        content()
    }
}

@Composable
private fun StableLandscapeCard(
    item: AnimeItem,
    progress: Float? = null,
    supportingText: String? = null,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(150), label = "stableCardScale")
    LaunchedEffect(focused) { if (focused) onFocused() }

    val focusModifier = when {
        focusRequester != null && upFocusRequester != null -> Modifier.focusRequester(focusRequester).focusProperties { up = upFocusRequester }
        focusRequester != null -> Modifier.focusRequester(focusRequester)
        upFocusRequester != null -> Modifier.focusProperties { up = upFocusRequester }
        else -> Modifier
    }

    Box(
        modifier = Modifier
            .width(StableCardWidth)
            .height(StableCardHeight)
            .then(focusModifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 5f else 0f)
            .clip(RoundedCornerShape(StableRadius))
            .background(MiruroColors.Card)
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(StableRadius))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (focused) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.90f)))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = if (progress != null) 10.dp else 8.dp)) {
                Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                supportingText?.let {
                    Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        progress?.let {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0xFF333333)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(it.coerceIn(0f, 1f)).height(4.dp).background(MiruroColors.Accent))
        }
    }
}

@Composable
private fun StableRankedCard(
    rank: Int,
    item: AnimeItem,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            rank.toString(),
            color = Color.White.copy(alpha = 0.16f),
            fontSize = 80.sp,
            lineHeight = 78.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(54.dp)
        )
        StableLandscapeCard(
            item = item,
            focusRequester = focusRequester,
            upFocusRequester = upFocusRequester,
            onFocused = onFocused,
            onClick = onClick
        )
    }
}

@Composable
private fun StableHomeNav(
    solid: Boolean,
    onHome: () -> Unit,
    onAnime: () -> Unit,
    onMovies: () -> Unit,
    onNewPopular: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(if (solid) 0.98f else 0.48f, tween(160), label = "stableNavAlpha")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(StableNavHeight)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = alpha), Color.Black.copy(alpha = alpha * 0.82f), Color.Transparent)))
            .padding(horizontal = StableSafeX),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo()
        Spacer(Modifier.width(28.dp))
        StableNavText("Home", true, onHome)
        StableNavText("Anime", false, onAnime)
        StableNavText("Movies", false, onMovies)
        StableNavText("New & Popular", false, onNewPopular, 132.dp)
        StableNavText("My List", false, onMyList)
        Spacer(Modifier.weight(1f))
        StableNavIcon(Icons.Filled.Search, "Search", false, onSearch)
        Spacer(Modifier.width(10.dp))
        StableNavIcon(Icons.Filled.Settings, "Settings", false, onSettings)
    }
}

@Composable
fun StableSearchScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<String?>(null) }
    var filtersVisible by remember { mutableStateOf(false) }
    val state by viewModel.searchResults.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()

    LaunchedEffect(query, format) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            viewModel.clearSearch()
        } else {
            delay(350)
            viewModel.search(AnimeSearchFilters(trimmed, format, null, emptyList(), null, AnimeSort.SEARCH_MATCH))
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(410.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StableSearchBox(query) { query = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StablePill("All", format == null) { format = null }
                StablePill("Anime", format == "TV") { format = "TV" }
                StablePill("Movies", format == "MOVIE") { format = "MOVIE" }
            }
            SecondaryButton("Filters", Modifier.fillMaxWidth()) { filtersVisible = !filtersVisible }
            if (filtersVisible) {
                Text("More filters are available under New & Popular.", color = MiruroColors.Subtle, fontSize = 13.sp)
            }
            StableKeyboard(
                onCharacter = { query += it },
                onBackspace = { if (query.isNotEmpty()) query = query.dropLast(1) },
                onSpace = { if (query.isNotEmpty() && !query.endsWith(' ')) query += " " },
                onClear = { query = "" },
                onSearch = { viewModel.search(AnimeSearchFilters(query.trim(), format, null, emptyList(), null, AnimeSort.SEARCH_MATCH)) }
            )
            if (recent.isNotEmpty()) {
                Text("Recent searches", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent.take(5), key = { it }) { saved -> StablePill(saved, false) { query = saved } }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("Top Results", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            when (val current = state) {
                null -> StateMessage("Type a title or use the TV keyboard to search.")
                is UiState.Loading -> LoadingState("Searching anime…")
                is UiState.Error -> ErrorState(current.message) {
                    viewModel.search(AnimeSearchFilters(query.trim(), format, null, emptyList(), null, AnimeSort.SEARCH_MATCH))
                }
                is UiState.Success -> {
                    if (current.data.isEmpty()) {
                        StateMessage("No matching anime found.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 30.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItems(current.data, key = { it.id }) { anime ->
                                StableResultCard(anime) { onOpenDetails(anime.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StableSearchBox(query: String, onQueryChange: (String) -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else MiruroColors.Card)
            .border(if (focused) 2.dp else 1.dp, if (focused) MiruroColors.Accent else MiruroColors.Border, RoundedCornerShape(8.dp))
            .clickable { requester.requestFocus() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = if (focused) Color.Black else Color.White.copy(alpha = 0.60f), modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) Text("Search anime", color = if (focused) Color.DarkGray else Color.White.copy(alpha = 0.55f), fontSize = 18.sp)
                        inner()
                    }
                }
            }
        )
    }
}

@Composable
private fun StableKeyboard(
    onCharacter: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { char ->
                    FocusableSurface(
                        onClick = { onCharacter(char.toString()) },
                        modifier = Modifier.size(width = 35.dp, height = 38.dp),
                        shape = RoundedCornerShape(5.dp),
                        unfocusedBackground = MiruroColors.Card,
                        focusedBackground = Color.White
                    ) { focused ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(char.toString(), color = if (focused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("⌫", Modifier.weight(0.75f), onBackspace)
            SecondaryButton("Space", Modifier.weight(1.25f), onSpace)
            SecondaryButton("Clear", Modifier.weight(1f), onClear)
            PrimaryButton("Search", Modifier.weight(1.15f), onSearch)
        }
    }
}

@Composable
private fun StablePill(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.height(42.dp).width((text.length * 9 + 38).coerceIn(74, 165).dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent else MiruroColors.Card,
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (selected) "✓ $text" else text, color = if (focused) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StableResultCard(item: AnimeItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(140), label = "resultScale")
    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 3f else 0f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(5.dp))
                .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(5.dp))
        ) {
            AsyncImage(item.bannerUrl ?: item.posterUrl, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StableBrowseScreen(
    title: String,
    format: String,
    viewModel: MiruroViewModel,
    onOpenDetails: (Int) -> Unit
) {
    var page by remember(format) { mutableStateOf(1) }
    val state by viewModel.genreResults.collectAsState()
    LaunchedEffect(format, page) {
        viewModel.loadGenre(emptyList(), format, page, AnimeSort.POPULARITY, null, null)
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            if (page > 1) SecondaryButton("Previous", Modifier.width(130.dp)) { page -= 1 }
            Spacer(Modifier.width(10.dp))
            SecondaryButton("Next page", Modifier.width(150.dp)) { page += 1 }
        }
        Spacer(Modifier.height(18.dp))
        when (val current = state) {
            null, is UiState.Loading -> LoadingState("Loading $title…")
            is UiState.Error -> ErrorState(current.message) {
                viewModel.loadGenre(emptyList(), format, page, AnimeSort.POPULARITY, null, null)
            }
            is UiState.Success -> {
                if (current.data.isEmpty()) {
                    StateMessage("No titles found on this page.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(bottom = 42.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(current.data, key = { it.id }) { anime ->
                            PosterCard(anime, width = 176.dp) { onOpenDetails(anime.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StableDetailsScreen(
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
            val unique = remember(details, settings.preferredAudio) { stableUniqueEpisodes(details, settings.preferredAudio) }
            val seasonIds = details.seasons.map { it.id }.toSet()
            val relevant = progress.filter { it.animeId in seasonIds }
            val watched = relevant.filter { it.watched }.map { Triple(it.animeId, it.seasonNumber, it.episodeNumber) }.toSet()
            val partial = relevant.filter { !it.watched && it.positionMs > 0 }.maxByOrNull { it.updatedAtMs }
            val smartTarget = partial?.let { saved ->
                unique.firstOrNull { it.episode.anilistId == saved.animeId && it.season == saved.seasonNumber && it.episode.episodeNumber == saved.episodeNumber }
            } ?: unique.firstOrNull { Triple(it.episode.anilistId, it.season, it.episode.episodeNumber) !in watched }
                ?: unique.firstOrNull()
            val watchedCount = unique.count { Triple(it.episode.anilistId, it.season, it.episode.episodeNumber) in watched }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 42.dp)) {
                item {
                    StableDetailsHero(
                        details = details,
                        inList = details.id in favorites,
                        target = smartTarget,
                        isResume = partial != null && smartTarget != null,
                        onBack = onBack,
                        onPlay = { target -> onPlayEpisode(target.season, target.episode.episodeNumber, target.episode.audioType) },
                        onList = { viewModel.toggleFavorite(details.id) }
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 52.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Episodes", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(16.dp))
                            Text("$watchedCount/${unique.size} watched", color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { StablePill("All", audioFilter == null) { audioFilter = null } }
                            item { StablePill("Sub", audioFilter == AudioType.SUB) { audioFilter = AudioType.SUB } }
                            item { StablePill("Dub", audioFilter == AudioType.DUB) { audioFilter = AudioType.DUB } }
                        }
                    }
                }
                details.seasons.forEach { season ->
                    val displayed = season.episodes
                        .filter { audioFilter == null || it.audioType == audioFilter }
                        .filterNot { ep -> settings.hideWatchedEpisodes && Triple(ep.anilistId, season.seasonNumber, ep.episodeNumber) in watched }
                    if (details.seasons.size > 1) {
                        item {
                            Text(
                                "Season ${season.seasonNumber}: ${season.title}",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 52.dp, vertical = 12.dp)
                            )
                        }
                    }
                    items(displayed.chunked(3)) { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 52.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            row.forEach { episode ->
                                val saved = relevant.filter {
                                    it.animeId == episode.anilistId && it.seasonNumber == season.seasonNumber && it.episodeNumber == episode.episodeNumber
                                }.maxByOrNull { it.updatedAtMs }
                                StableEpisodeCard(
                                    episode = episode,
                                    progress = saved,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenEpisode(season.seasonNumber, episode.episodeNumber, episode.audioType) }
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private fun stableUniqueEpisodes(details: AnimeDetails, preferred: AudioType): List<StableEpisodeTarget> =
    details.seasons.flatMap { season ->
        season.episodes.groupBy { it.episodeNumber }.mapNotNull { (_, versions) ->
            val playable = versions.filter { it.sourceCandidates.isNotEmpty() }
            val selected = playable.firstOrNull { it.audioType == preferred }
                ?: playable.firstOrNull()
                ?: versions.firstOrNull { it.audioType == preferred }
                ?: versions.firstOrNull()
            selected?.let { StableEpisodeTarget(season.seasonNumber, it) }
        }
    }.sortedWith(compareBy<StableEpisodeTarget> { it.season }.thenBy { it.episode.episodeNumber })

@Composable
private fun StableDetailsHero(
    details: AnimeDetails,
    inList: Boolean,
    target: StableEpisodeTarget?,
    isResume: Boolean,
    onBack: () -> Unit,
    onPlay: (StableEpisodeTarget) -> Unit,
    onList: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(410.dp).background(Color.Black)) {
        AsyncImage(details.bannerUrl ?: details.posterUrl, details.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black))))
        SecondaryButton("Back", Modifier.align(Alignment.TopStart).padding(28.dp).width(112.dp), onBack)
        Column(Modifier.align(Alignment.BottomStart).padding(start = 52.dp, bottom = 34.dp).width(640.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                details.rating?.let { Text("★ $it AniList", color = Color(0xFFFFE75A), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(14.dp))
                Text(
                    listOfNotNull(details.year?.toString(), "${details.seasons.size} season${if (details.seasons.size == 1) "" else "s"}").joinToString(" • "),
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(details.title, color = Color.White, fontSize = 38.sp, lineHeight = 41.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(details.genres.take(4), key = { it }) { GenreChip(it) } }
            Spacer(Modifier.height(10.dp))
            BodyText(details.description ?: "No synopsis available.", maxLines = 2)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                target?.let {
                    PrimaryButton(
                        if (isResume) "Resume S${it.season} E${it.episode.episodeNumber}" else "Play S${it.season} E${it.episode.episodeNumber}",
                        Modifier.width(250.dp)
                    ) { onPlay(it) }
                }
                SecondaryButton(if (inList) "✓ My List" else "+ Add to List", Modifier.width(190.dp), onList)
            }
        }
    }
}

@Composable
private fun StableEpisodeCard(
    episode: AnimeEpisode,
    progress: WatchProgress?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FocusableSurface(onClick = onClick, modifier = modifier, unfocusedBackground = MiruroColors.Card) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MiruroColors.CardHigh)) {
                episode.thumbnailUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Box(Modifier.align(Alignment.TopStart).padding(9.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                    Text(episode.audioType.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                progress?.let {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0xFF333333)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(it.percent.coerceIn(0f, 1f)).height(4.dp).background(MiruroColors.Accent))
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text("E${episode.episodeNumber} • ${episode.audioType.name}", color = if (focused) Color.Black else MiruroColors.AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(episode.title ?: "Episode ${episode.episodeNumber}", color = if (focused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun StableEpisodeDetailsScreen(
    episode: AnimeEpisode?,
    viewModel: MiruroViewModel,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    if (episode == null) {
        StateMessage("Episode not found.")
        return
    }
    val settings by viewModel.settings.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val saved = progress.firstOrNull {
        it.animeId == episode.anilistId && it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber && it.audioType == episode.audioType
    }
    val providers = remember(episode) { listOf("Auto") + episode.sourceCandidates.map { it.provider }.distinct() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 28.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { SecondaryButton("Back", Modifier.width(112.dp), onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f).height(250.dp).clip(RoundedCornerShape(6.dp)).background(MiruroColors.CardHigh)) {
                    episode.thumbnailUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Column(Modifier.weight(1.15f)) {
                    Text("Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(episode.title ?: "Episode ${episode.episodeNumber}", color = Color.White.copy(alpha = 0.76f), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(18.dp))
                    StableMetadataLine("Audio", episode.audioType.name)
                    StableMetadataLine("Runtime", episode.runtimeMinutes?.let { "${it}m" } ?: "Unknown")
                    StableMetadataLine("Release", episode.releaseDate ?: "Unknown")
                    StableMetadataLine("Sources", if (episode.sourceCandidates.isEmpty()) "Unavailable" else "${episode.sourceCandidates.size} available")
                }
            }
        }
        if (episode.sourceCandidates.isNotEmpty()) {
            item {
                Text("Provider", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(providers, key = { it }) { provider ->
                        SecondaryButton(
                            if (provider.equals(settings.preferredProvider, true)) "✓ $provider" else provider,
                            Modifier.width(160.dp)
                        ) { viewModel.updatePreferredProvider(provider) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Play episode", Modifier.width(210.dp), onPlay)
                    SecondaryButton(
                        if (saved?.watched == true) "Mark unwatched" else "Mark watched",
                        Modifier.width(210.dp)
                    ) { viewModel.setEpisodeWatched(episode, saved?.watched != true) }
                }
            }
        } else {
            item { StateMessage("No playable source is currently available for this episode.") }
        }
    }
}

@Composable
private fun StableMetadataLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.50f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
