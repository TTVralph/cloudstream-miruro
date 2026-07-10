package com.ttvralph.miruroapp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.*
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Sx = 45.dp
private val NavH = 70.dp
private val CardW = 228.dp
private val CardH = 128.dp
private val Gap = 12.dp
private val Radius = 4.dp

private data class ResumeCard(val anime: AnimeItem, val progress: WatchProgress)

@Composable
fun StitchHomeScreen(
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
    val playFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }

    val unfinished = remember(progress, settings.preferredAudio, metadataVersion) {
        progress.filterNot { it.watched }
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
            val initial = rows.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { !it.bannerUrl.isNullOrBlank() }
                ?: rows.firstOrNull()?.items?.firstOrNull()

            if (initial == null) {
                ErrorState("The home catalogue did not return any titles.") { viewModel.loadHome() }
                return
            }

            val resume = remember(unfinished, metadataVersion) {
                unfinished.map { p ->
                    ResumeCard(
                        viewModel.cachedItem(p.animeId) ?: AnimeItem(
                            p.animeId,
                            "Anime #${p.animeId}",
                            null,
                            null,
                            AnimeType.UNKNOWN
                        ),
                        p
                    )
                }
            }
            val all = remember(rows, resume) {
                (listOf(initial) + resume.map { it.anime } + rows.flatMap { it.items })
                    .distinctBy { it.id }
            }
            var pendingId by remember(initial.id) { mutableIntStateOf(initial.id) }
            var activeId by remember(initial.id) { mutableIntStateOf(initial.id) }
            val active = all.firstOrNull { it.id == activeId } ?: initial

            LaunchedEffect(pendingId) {
                delay(110)
                activeId = pendingId
            }

            val scrolled by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 72
                }
            }
            val heroAlpha by animateFloatAsState(
                if (scrolled) 0f else 1f,
                tween(210),
                label = "heroAlpha"
            )

            LaunchedEffect(Unit) {
                delay(250)
                runCatching { playFocus.requestFocus() }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                StitchBackdrop(active, scrolled)
                StitchHero(
                    active,
                    active.id in favorites,
                    heroAlpha,
                    playFocus,
                    firstRowFocus,
                    onFocused = { scope.launch { listState.animateScrollToItem(0) } },
                    onPlay = { onOpenDetails(active.id) },
                    onList = { viewModel.toggleFavorite(active.id) }
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(top = NavH),
                    contentPadding = PaddingValues(top = 414.dp, bottom = 52.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    var rowIndex = 0
                    if (resume.isNotEmpty()) {
                        val index = rowIndex++
                        item("stitch-resume") {
                            StitchRow("Continue Watching") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(Gap),
                                    contentPadding = PaddingValues(horizontal = Sx, vertical = 13.dp)
                                ) {
                                    itemsIndexed(resume, key = { _, it -> it.progress.key }) { i, item ->
                                        StitchCard(
                                            item.anime,
                                            item.progress.percent,
                                            "S${item.progress.seasonNumber}:E${item.progress.episodeNumber}",
                                            if (i == 0) firstRowFocus else null,
                                            if (i == 0) playFocus else null,
                                            onFocused = {
                                                scope.launch { listState.animateScrollToItem(index) }
                                                pendingId = item.anime.id
                                            },
                                            onClick = { onPlayProgress(item.progress) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    rows.forEach { row ->
                        val index = rowIndex++
                        val first = index == 0
                        item("stitch-${row.title}") {
                            StitchRow(row.title) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(
                                        if (row.title.equals("Trending Now", true)) 18.dp else Gap
                                    ),
                                    contentPadding = PaddingValues(horizontal = Sx, vertical = 13.dp)
                                ) {
                                    itemsIndexed(row.items, key = { _, it -> it.id }) { i, item ->
                                        val firstFocus = if (first && i == 0) firstRowFocus else null
                                        val upFocus = if (first && i == 0) playFocus else null
                                        if (row.title.equals("Trending Now", true) && i < 10) {
                                            StitchRanked(
                                                i + 1,
                                                item,
                                                firstFocus,
                                                upFocus,
                                                onFocused = {
                                                    scope.launch { listState.animateScrollToItem(index) }
                                                    pendingId = item.id
                                                },
                                                onClick = { onOpenDetails(item.id) }
                                            )
                                        } else {
                                            StitchCard(
                                                item,
                                                null,
                                                null,
                                                firstFocus,
                                                upFocus,
                                                onFocused = {
                                                    scope.launch { listState.animateScrollToItem(index) }
                                                    pendingId = item.id
                                                },
                                                onClick = { onOpenDetails(item.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                StitchNav(
                    scrolled,
                    onHome,
                    onAnime,
                    onMovies,
                    onNewPopular,
                    onMyList,
                    onSearch,
                    onSettings,
                    Modifier.align(Alignment.TopCenter).zIndex(10f)
                )
            }
        }
    }
}

@Composable
private fun StitchBackdrop(item: AnimeItem, scrolled: Boolean) {
    val dim by animateFloatAsState(
        if (scrolled) 0.86f else 0.30f,
        tween(300),
        label = "backdropDim"
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(item, tween(360), label = "backdrop") { active ->
            AsyncImage(
                model = active.bannerUrl ?: active.posterUrl,
                contentDescription = active.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    arrayOf(
                        0f to Color.Black,
                        .26f to Color.Black.copy(.96f),
                        .48f to Color.Black.copy(.72f),
                        .72f to Color.Black.copy(.20f),
                        1f to Color.Black.copy(.12f)
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    arrayOf(
                        0f to Color.Black.copy(.40f),
                        .46f to Color.Transparent,
                        .70f to Color.Black.copy(.58f),
                        .86f to Color.Black.copy(.94f),
                        1f to Color.Black
                    )
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(dim)))
    }
}

@Composable
private fun StitchHero(
    item: AnimeItem,
    inList: Boolean,
    alpha: Float,
    play: FocusRequester,
    first: FocusRequester,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onList: () -> Unit
) {
    Column(
        Modifier.padding(start = Sx, top = 168.dp)
            .width(500.dp)
            .graphicsLayer { this.alpha = alpha }
            .zIndex(4f)
    ) {
        TvText(
            "ANISTREAM  •  FEATURED",
            Color.White.copy(.76f),
            12.sp,
            FontWeight.Bold,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(10.dp))
        TvText(
            item.title,
            Color.White,
            42.sp,
            FontWeight.Black,
            lineHeight = 45.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.score?.let {
                TvText("$it% Match", Color(0xFF46D369), 14.sp, FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
            }
            TvText(
                listOfNotNull(
                    item.year?.toString(),
                    item.type.name.takeIf { it != "UNKNOWN" }
                        ?.lowercase(Locale.ROOT)
                        ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
                ).joinToString("   "),
                Color.White.copy(.86f),
                14.sp,
                FontWeight.Medium
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroButton(
                "▶   Play",
                true,
                Modifier.width(152.dp).focusRequester(play).focusProperties { down = first },
                onFocused,
                onPlay
            )
            HeroButton(
                if (inList) "✓   My List" else "+   My List",
                false,
                Modifier.width(174.dp).focusProperties { down = first },
                onFocused,
                onList
            )
        }
    }
}

@Composable
private fun HeroButton(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.05f else 1f,
        tween(180),
        label = "buttonScale"
    )
    LaunchedEffect(focused) { if (focused) onFocused() }
    Box(
        modifier.height(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(Radius))
            .background(
                if (primary) Color.White
                else if (focused) Color(0xFF5A5A5A)
                else Color(0xB33A3A3A)
            )
            .border(
                if (!primary && focused) 2.dp else 0.dp,
                if (!primary && focused) Color.White else Color.Transparent,
                RoundedCornerShape(Radius)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        TvText(text, if (primary) Color.Black else Color.White, 16.sp, FontWeight.Bold)
    }
}

@Composable
private fun StitchRow(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        TvText(
            title,
            Color.White,
            20.sp,
            FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Sx)
        )
        content()
    }
}

@Composable
private fun StitchCard(
    item: AnimeItem,
    progress: Float?,
    support: String?,
    focus: FocusRequester?,
    up: FocusRequester?,
    width: Dp = CardW,
    height: Dp = CardH,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.10f else 1f,
        tween(180),
        label = "cardScale"
    )
    LaunchedEffect(focused) { if (focused) onFocused() }
    val focusModifier = when {
        focus != null && up != null -> Modifier.focusRequester(focus).focusProperties { this.up = up }
        focus != null -> Modifier.focusRequester(focus)
        up != null -> Modifier.focusProperties { this.up = up }
        else -> Modifier
    }
    Box(
        Modifier.width(width)
            .height(height)
            .then(focusModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 18f else 0f
            }
            .zIndex(if (focused) 6f else 0f)
            .clip(RoundedCornerShape(Radius))
            .background(MiruroColors.Card)
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(Radius)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (focused) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        arrayOf(
                            0f to Color.Transparent,
                            .48f to Color.Transparent,
                            1f to Color.Black.copy(.88f)
                        )
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(
                    start = 11.dp,
                    end = 11.dp,
                    bottom = if (progress != null) 11.dp else 8.dp
                )
            ) {
                TvText(
                    item.title,
                    Color.White,
                    13.sp,
                    FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                support?.let {
                    TvText(it, Color.White.copy(.74f), 11.sp, FontWeight.Medium)
                }
            }
        }
        progress?.let {
            Box(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF333333))
            )
            Box(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth(it.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(Color(0xFFE50914))
            )
        }
    }
}

@Composable
private fun StitchRanked(
    rank: Int,
    item: AnimeItem,
    focus: FocusRequester?,
    up: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    Box(Modifier.width(260.dp).height(142.dp)) {
        TvText(
            rank.toString(),
            Color.White.copy(.18f),
            104.sp,
            FontWeight.Black,
            lineHeight = 104.sp,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-2).dp, y = 3.dp)
        )
        Box(Modifier.align(Alignment.CenterEnd)) {
            StitchCard(item, null, null, focus, up, 196.dp, 110.dp, onFocused, onClick)
        }
    }
}

@Composable
private fun StitchNav(
    solid: Boolean,
    onHome: () -> Unit,
    onAnime: () -> Unit,
    onMovies: () -> Unit,
    onNew: () -> Unit,
    onList: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier
) {
    val alpha by animateFloatAsState(
        if (solid) .96f else .54f,
        tween(220),
        label = "navAlpha"
    )
    Row(
        modifier.fillMaxWidth()
            .height(NavH)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha),
                        Color.Black.copy(alpha * .72f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = Sx),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo()
        Spacer(Modifier.width(28.dp))
        NavText("Home", true, 76.dp, onHome)
        NavText("Anime", false, 76.dp, onAnime)
        NavText("Movies", false, 76.dp, onMovies)
        NavText("New & Popular", false, 132.dp, onNew)
        NavText("My List", false, 76.dp, onList)
        Spacer(Modifier.weight(1f))
        NavIcon(Icons.Filled.Search, "Search", onSearch)
        Spacer(Modifier.width(10.dp))
        NavIcon(Icons.Filled.Settings, "Settings", onSettings)
    }
}

@Composable
private fun NavText(
    text: String,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) 1.04f else 1f,
        tween(160),
        label = "navScale"
    )
    Column(
        Modifier.width(width)
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TvText(
            text,
            if (focused || selected) Color.White else Color.White.copy(.70f),
            14.sp,
            if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.size(if (selected) 3.dp else 0.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun NavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = Color.Transparent,
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (focused) Color.Black else Color.White)
        }
    }
}

@Composable
private fun TvText(
    text: String,
    color: Color,
    size: TextUnit,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    letterSpacing: TextUnit = TextUnit.Unspecified
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontWeight = weight,
        modifier = modifier,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        letterSpacing = letterSpacing
    )
}
