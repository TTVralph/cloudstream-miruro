package com.ttvralph.miruroapp

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ContinueWatchingItem(
    val anime: AnimeItem,
    val progress: WatchProgress
)

@Composable
fun CinematicHomeScreen(
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
    val scope = rememberCoroutineScope()
    val heroFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }

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
            val initialHero = rows.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { !it.bannerUrl.isNullOrBlank() }
                ?: rows.firstOrNull()?.items?.firstOrNull()

            if (initialHero == null) {
                ErrorState("The home catalogue did not return any titles.") { viewModel.loadHome() }
                return
            }

            val continueItems = remember(unfinished, metadataVersion) {
                unfinished.map { entry ->
                    val anime = viewModel.cachedItem(entry.animeId) ?: AnimeItem(
                        id = entry.animeId,
                        title = "Anime #${entry.animeId}",
                        posterUrl = null,
                        bannerUrl = null,
                        type = AnimeType.UNKNOWN
                    )
                    ContinueWatchingItem(anime, entry)
                }
            }
            val allItems = remember(rows, continueItems) {
                (listOf(initialHero) + continueItems.map { it.anime } + rows.flatMap { it.items })
                    .distinctBy { it.id }
            }
            var activeId by remember(initialHero.id) { mutableStateOf(initialHero.id) }
            val activeItem = allItems.firstOrNull { it.id == activeId } ?: initialHero

            val rowsScrolled by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 96
                }
            }
            val heroContentAlpha by animateFloatAsState(
                targetValue = if (rowsScrolled) 0f else 1f,
                animationSpec = tween(220),
                label = "heroContentAlpha"
            )

            LaunchedEffect(Unit) {
                delay(250)
                runCatching { heroFocusRequester.requestFocus() }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                CinematicBackdrop(item = activeItem, rowsScrolled = rowsScrolled)

                CinematicHeroMetadata(
                    item = activeItem,
                    inList = activeItem.id in favorites,
                    alpha = heroContentAlpha,
                    playFocusRequester = heroFocusRequester,
                    firstRowFocusRequester = firstRowFocusRequester,
                    onFocused = {
                        activeId = activeItem.id
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onPlay = { onOpenDetails(activeItem.id) },
                    onToggleList = { viewModel.toggleFavorite(activeItem.id) }
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 82.dp),
                    contentPadding = PaddingValues(top = 360.dp, bottom = 58.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    var rowIndex = 0

                    if (continueItems.isNotEmpty()) {
                        val currentRowIndex = rowIndex++
                        item(key = "cinematic-continue") {
                            CinematicContinueWatchingRow(
                                items = continueItems,
                                firstFocusRequester = firstRowFocusRequester,
                                heroFocusRequester = heroFocusRequester,
                                onRowFocused = {
                                    scope.launch { listState.animateScrollToItem(currentRowIndex) }
                                },
                                onItemFocused = { activeId = it.anime.id },
                                onPlay = { onPlayProgress(it.progress) }
                            )
                        }
                    }

                    rows.forEach { row ->
                        val currentRowIndex = rowIndex++
                        val useFirstRequester = continueItems.isEmpty() && currentRowIndex == 0
                        item(key = "cinematic-${row.title}") {
                            CinematicTitleRow(
                                title = row.title,
                                items = row.items,
                                ranked = row.title.equals("Trending Now", ignoreCase = true),
                                firstFocusRequester = if (useFirstRequester) firstRowFocusRequester else null,
                                heroFocusRequester = if (currentRowIndex == 0) heroFocusRequester else null,
                                onRowFocused = {
                                    scope.launch { listState.animateScrollToItem(currentRowIndex) }
                                },
                                onItemFocused = { activeId = it.id },
                                onOpen = { onOpenDetails(it.id) }
                            )
                        }
                    }
                }

                CinematicTopNavigation(
                    solid = rowsScrolled,
                    onHome = onHome,
                    onAnime = onAnime,
                    onMovies = onMovies,
                    onNewPopular = onNewPopular,
                    onMyList = onMyList,
                    onSearch = onSearch,
                    onSettings = onSettings,
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(5f)
                )
            }
        }
    }
}

@Composable
private fun CinematicBackdrop(item: AnimeItem, rowsScrolled: Boolean) {
    val extraDim by animateFloatAsState(
        targetValue = if (rowsScrolled) 0.72f else 0.28f,
        animationSpec = tween(280),
        label = "backdropDim"
    )

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
                    colorStops = arrayOf(
                        0f to Color.Black,
                        0.28f to Color.Black.copy(alpha = 0.94f),
                        0.62f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.18f)
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.38f),
                        0.48f to Color.Transparent,
                        0.74f to Color.Black.copy(alpha = 0.74f),
                        1f to Color.Black
                    )
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = extraDim)))
    }
}

@Composable
private fun CinematicHeroMetadata(
    item: AnimeItem,
    inList: Boolean,
    alpha: Float,
    playFocusRequester: FocusRequester,
    firstRowFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onToggleList: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(start = 58.dp, top = 146.dp)
            .width(650.dp)
            .graphicsLayer { this.alpha = alpha }
            .zIndex(3f)
    ) {
        Text(
            "FEATURED",
            color = MiruroColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            item.title,
            color = Color.White,
            fontSize = 48.sp,
            lineHeight = 51.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.score?.let {
                Text(
                    "${it}% Match",
                    color = Color(0xFF46D369),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(14.dp))
            }
            Text(
                listOfNotNull(
                    item.year?.toString(),
                    item.type.name.takeIf { it != "UNKNOWN" }
                        ?.lowercase(Locale.ROOT)
                        ?.replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
                ).joinToString("   "),
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                "▶  Play",
                modifier = Modifier
                    .width(178.dp)
                    .focusRequester(playFocusRequester)
                    .focusProperties { down = firstRowFocusRequester }
                    .onFocusChanged { if (it.hasFocus) onFocused() },
                onClick = onPlay
            )
            SecondaryButton(
                if (inList) "✓  My List" else "+  My List",
                modifier = Modifier
                    .width(178.dp)
                    .focusProperties { down = firstRowFocusRequester }
                    .onFocusChanged { if (it.hasFocus) onFocused() },
                onClick = onToggleList
            )
        }
    }
}

@Composable
private fun CinematicContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    firstFocusRequester: FocusRequester,
    heroFocusRequester: FocusRequester,
    onRowFocused: () -> Unit,
    onItemFocused: (ContinueWatchingItem) -> Unit,
    onPlay: (ContinueWatchingItem) -> Unit
) {
    CinematicRowContainer(title = "Continue Watching for You") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 13.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.progress.key }) { index, item ->
                CinematicCard(
                    item = item.anime,
                    progress = item.progress.percent,
                    supportingText = "S${item.progress.seasonNumber}:E${item.progress.episodeNumber}",
                    focusRequester = if (index == 0) firstFocusRequester else null,
                    upFocusRequester = if (index == 0) heroFocusRequester else null,
                    onFocused = {
                        onRowFocused()
                        onItemFocused(item)
                    },
                    onClick = { onPlay(item) }
                )
            }
        }
    }
}

@Composable
private fun CinematicTitleRow(
    title: String,
    items: List<AnimeItem>,
    ranked: Boolean,
    firstFocusRequester: FocusRequester?,
    heroFocusRequester: FocusRequester?,
    onRowFocused: () -> Unit,
    onItemFocused: (AnimeItem) -> Unit,
    onOpen: (AnimeItem) -> Unit
) {
    CinematicRowContainer(title = title) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 13.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                if (ranked && index < 10) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${index + 1}",
                            color = Color.White.copy(alpha = 0.16f),
                            fontSize = 104.sp,
                            lineHeight = 104.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                        )
                        CinematicCard(
                            item = item,
                            focusRequester = if (index == 0) firstFocusRequester else null,
                            upFocusRequester = if (index == 0) heroFocusRequester else null,
                            onFocused = {
                                onRowFocused()
                                onItemFocused(item)
                            },
                            onClick = { onOpen(item) }
                        )
                    }
                } else {
                    CinematicCard(
                        item = item,
                        focusRequester = if (index == 0) firstFocusRequester else null,
                        upFocusRequester = if (index == 0) heroFocusRequester else null,
                        onFocused = {
                            onRowFocused()
                            onItemFocused(item)
                        },
                        onClick = { onOpen(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CinematicRowContainer(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 58.dp)
        )
        content()
    }
}

@Composable
private fun CinematicCard(
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
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.09f else 1f,
        animationSpec = tween(170),
        label = "cardScale"
    )

    LaunchedEffect(focused) {
        if (focused) onFocused()
    }

    val focusModifier = when {
        focusRequester != null && upFocusRequester != null -> Modifier
            .focusRequester(focusRequester)
            .focusProperties { up = upFocusRequester }
        focusRequester != null -> Modifier.focusRequester(focusRequester)
        upFocusRequester != null -> Modifier.focusProperties { up = upFocusRequester }
        else -> Modifier
    }

    Box(
        modifier = Modifier
            .width(304.dp)
            .height(171.dp)
            .then(focusModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (focused) 4f else 0f)
            .clip(RoundedCornerShape(4.dp))
            .background(MiruroColors.Card)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.58f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = if (progress != null) 12.dp else 9.dp)
        ) {
            Text(
                item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (focused && supportingText != null) {
                Text(
                    supportingText,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        progress?.let {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(it.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(MiruroColors.Accent)
            )
        }
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.04f))
            )
        }
    }
}

@Composable
private fun CinematicTopNavigation(
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
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (solid) 0.98f else 0.34f,
        animationSpec = tween(220),
        label = "navBackground"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = backgroundAlpha),
                        Color.Black.copy(alpha = backgroundAlpha * 0.82f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo()
        Spacer(Modifier.width(34.dp))
        CinematicNavText("Home", selected = true, onClick = onHome)
        CinematicNavText("Anime", onClick = onAnime)
        CinematicNavText("Movies", onClick = onMovies)
        CinematicNavText("New & Popular", width = 142.dp, onClick = onNewPopular)
        CinematicNavText("My List", onClick = onMyList)
        Spacer(Modifier.weight(1f))
        CinematicNavIcon(Icons.Filled.Search, "Search", onSearch)
        Spacer(Modifier.width(12.dp))
        CinematicNavIcon(Icons.Filled.Settings, "Settings", onSettings)
    }
}

@Composable
private fun CinematicNavText(
    text: String,
    selected: Boolean = false,
    width: androidx.compose.ui.unit.Dp = 84.dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .scale(if (focused) 1.05f else 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            color = when {
                focused -> Color.White
                selected -> Color.White
                else -> Color.White.copy(alpha = 0.72f)
            },
            fontSize = 15.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(if (selected) 4.dp else 0.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun CinematicNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = Color.Transparent,
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (focused) Color.Black else Color.White)
        }
    }
}
