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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AuditSafeX = 52.dp
private val AuditNavHeight = 76.dp
private val AuditCardWidth = 228.dp
private val AuditCardHeight = 128.dp
private val AuditRadius = 5.dp

private data class AuditResumeItem(val anime: AnimeItem, val progress: WatchProgress)
private data class AuditEpisodeTarget(val season: Int, val episode: AnimeEpisode)

@Composable
fun AuditHomeScreen(
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
                    AuditResumeItem(
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
            var heroItem by remember(initial.id) { mutableStateOf(initial) }
            var activeRow by remember(initial.id) { mutableStateOf(-1) }

            LaunchedEffect(Unit) {
                delay(220)
                runCatching { playFocus.requestFocus() }
            }
            LaunchedEffect(activeRow) {
                if (activeRow < 0) listState.scrollToItem(0) else listState.scrollToItem(activeRow + 1)
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(top = AuditNavHeight),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item("audit-home-hero") {
                        AuditHomeHero(
                            item = heroItem,
                            inList = heroItem.id in favorites,
                            playFocus = playFocus,
                            firstRowFocus = firstRowFocus,
                            onFocused = { activeRow = -1 },
                            onPlay = { onOpenDetails(heroItem.id) },
                            onList = { viewModel.toggleFavorite(heroItem.id) }
                        )
                    }

                    var rowNumber = 0
                    if (resumeItems.isNotEmpty()) {
                        val row = rowNumber++
                        item("audit-resume") {
                            AuditHomeRow("Continue Watching") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = AuditSafeX, vertical = 12.dp)
                                ) {
                                    itemsIndexed(resumeItems, key = { _, it -> it.progress.key }) { index, item ->
                                        AuditHomeCard(
                                            item = item.anime,
                                            progress = item.progress.percent,
                                            supportingText = "S${item.progress.seasonNumber} E${item.progress.episodeNumber}",
                                            focusRequester = if (index == 0) firstRowFocus else null,
                                            upFocusRequester = playFocus,
                                            onFocused = {
                                                activeRow = row
                                                heroItem = item.anime
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
                        item("audit-row-${homeRow.title}") {
                            AuditHomeRow(homeRow.title) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = AuditSafeX, vertical = 12.dp)
                                ) {
                                    itemsIndexed(homeRow.items, key = { _, it -> it.id }) { index, anime ->
                                        AuditHomeCard(
                                            item = anime,
                                            focusRequester = if (first && index == 0) firstRowFocus else null,
                                            upFocusRequester = if (first) playFocus else null,
                                            onFocused = {
                                                activeRow = row
                                                heroItem = anime
                                            },
                                            onClick = { onOpenDetails(anime.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                StableTopBar(
                    current = "Home",
                    onHome = onHome,
                    onAnime = onAnime,
                    onMovies = onMovies,
                    onNewPopular = onNewPopular,
                    onMyList = onMyList,
                    onSearch = onSearch,
                    onSettings = onSettings
                )
            }
        }
    }
}

@Composable
private fun AuditHomeHero(
    item: AnimeItem,
    inList: Boolean,
    playFocus: FocusRequester,
    firstRowFocus: FocusRequester,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onList: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(405.dp).background(Color.Black)) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(.94f), Color.Black.copy(.55f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.20f), Color.Transparent, Color.Black))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = AuditSafeX, bottom = 38.dp).width(560.dp)) {
            Text("ANISTREAM  •  FEATURED", color = Color.White.copy(.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Text(item.title, color = Color.White, fontSize = 38.sp, lineHeight = 41.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.score?.let {
                    Text("$it% Match", color = Color(0xFF46D369), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    listOfNotNull(item.year?.toString(), item.type.name.takeIf { it != "UNKNOWN" }?.lowercase(Locale.ROOT)?.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }).joinToString("   "),
                    color = Color.White.copy(.80f),
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AuditHeroButton(
                    "▶  Play",
                    true,
                    Modifier.width(150.dp).focusRequester(playFocus).focusProperties { down = firstRowFocus },
                    onFocused,
                    onPlay
                )
                AuditHeroButton(
                    if (inList) "✓  My List" else "+  My List",
                    false,
                    Modifier.width(170.dp).focusProperties { down = firstRowFocus },
                    onFocused,
                    onList
                )
            }
        }
    }
}

@Composable
private fun AuditHeroButton(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (primary || focused) Color.White else Color(0xFF4A4A4A).copy(.88f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .onFocusChanged { if (it.hasFocus) onFocused() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (primary || focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AuditHomeRow(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = AuditSafeX))
        content()
    }
}

@Composable
private fun AuditHomeCard(
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
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, tween(140), label = "auditCard")
    LaunchedEffect(focused) { if (focused) onFocused() }
    val focusModifier = when {
        focusRequester != null && upFocusRequester != null -> Modifier.focusRequester(focusRequester).focusProperties { up = upFocusRequester }
        focusRequester != null -> Modifier.focusRequester(focusRequester)
        upFocusRequester != null -> Modifier.focusProperties { up = upFocusRequester }
        else -> Modifier
    }
    Box(
        modifier = Modifier
            .width(AuditCardWidth)
            .height(AuditCardHeight)
            .then(focusModifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 4f else 0f)
            .clip(RoundedCornerShape(AuditRadius))
            .background(MiruroColors.Card)
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(AuditRadius))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        AsyncImage(item.bannerUrl ?: item.posterUrl, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (focused) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(.90f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                supportingText?.let { Text(it, color = Color.White.copy(.72f), fontSize = 11.sp) }
            }
        }
        progress?.let {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0xFF333333)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(it.coerceIn(0f, 1f)).height(4.dp).background(MiruroColors.Accent))
        }
    }
}

@Composable
fun AuditSearchScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<String?>(null) }
    val state by viewModel.searchResults.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()

    LaunchedEffect(query, format) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) viewModel.clearSearch()
        else {
            delay(350)
            viewModel.search(AnimeSearchFilters(trimmed, format, null, emptyList(), null, AnimeSort.SEARCH_MATCH))
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LazyColumn(
            modifier = Modifier.width(430.dp).fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { AuditSearchBox(query) { query = it } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditPill("All", format == null) { format = null }
                    AuditPill("Anime", format == "TV") { format = "TV" }
                    AuditPill("Movies", format == "MOVIE") { format = "MOVIE" }
                }
            }
            item {
                AuditKeyboard(
                    onCharacter = { query += it },
                    onBackspace = { if (query.isNotEmpty()) query = query.dropLast(1) },
                    onSpace = { if (query.isNotEmpty() && !query.endsWith(' ')) query += " " },
                    onClear = { query = "" },
                    onSearch = {
                        viewModel.search(AnimeSearchFilters(query.trim(), format, null, emptyList(), null, AnimeSort.SEARCH_MATCH))
                    }
                )
            }
            if (recent.isNotEmpty()) {
                item {
                    Text("Recent searches", color = Color.White.copy(.72f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recent.take(10), key = { it }) { saved -> AuditPill(saved, false) { query = saved } }
                    }
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
                is UiState.Success -> if (current.data.isEmpty()) {
                    StateMessage("No matching anime found.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 30.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(current.data, key = { it.id }) { anime -> AuditResultCard(anime) { onOpenDetails(anime.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditSearchBox(query: String, onQueryChange: (String) -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else MiruroColors.Card)
            .border(if (focused) 2.dp else 1.dp, if (focused) MiruroColors.Accent else MiruroColors.Border, RoundedCornerShape(8.dp))
            .clickable { requester.requestFocus() }.padding(horizontal = 16.dp),
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
                    Icon(Icons.Filled.Search, null, tint = if (focused) Color.Black else Color.White.copy(.60f), modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) Text("Search anime", color = if (focused) Color.DarkGray else Color.White.copy(.55f), fontSize = 18.sp)
                        inner()
                    }
                }
            }
        )
    }
}

@Composable
private fun AuditKeyboard(
    onCharacter: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    val rows = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM", ".,!?'-_@")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { char -> AuditKeyboardKey(char.toString(), 35.dp) { onCharacter(char.toString()) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            AuditActionKey("⌫", Modifier.weight(.75f), onBackspace)
            AuditActionKey("Space", Modifier.weight(1.25f), onSpace)
            AuditActionKey("Clear", Modifier.weight(1f), onClear)
            AuditActionKey("Search", Modifier.weight(1.35f), onSearch, primary = true)
        }
    }
}

@Composable
private fun AuditKeyboardKey(text: String, width: Dp, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(width).height(38.dp),
        shape = RoundedCornerShape(5.dp),
        unfocusedBackground = MiruroColors.Card,
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = if (focused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AuditActionKey(text: String, modifier: Modifier, onClick: () -> Unit, primary: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(7.dp))
            .background(if (focused) Color.White else if (primary) MiruroColors.Accent else MiruroColors.Card)
            .border(if (focused) 2.dp else 0.dp, if (focused) MiruroColors.Accent else Color.Transparent, RoundedCornerShape(7.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (focused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun AuditPill(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.height(42.dp).width((text.length * 9 + 38).coerceIn(74, 180).dp),
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
private fun AuditResultCard(item: AnimeItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(Modifier.zIndex(if (focused) 3f else 0f).clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(5.dp))
                .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(5.dp))
        ) { AsyncImage(item.bannerUrl ?: item.posterUrl, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Spacer(Modifier.height(6.dp))
        Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AuditSettingsScreen(
    viewModel: MiruroViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear watch history?", color = Color.White, fontWeight = FontWeight.Black) },
            text = { Text("This removes Continue Watching and all locally saved episode progress. This cannot be undone.", color = MiruroColors.Muted) },
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
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black) }
        item { AuditSettingsHeader("Playback") }
        item {
            AuditSettingChoiceRow("Preferred audio", "Used when both Sub and Dub are available.", listOf("SUB" to "Sub", "DUB" to "Dub"), settings.preferredAudio.name) {
                viewModel.updatePreferredAudio(AudioType.valueOf(it))
            }
        }
        item {
            AuditSettingChoiceRow(
                "Autoplay next episode",
                "When an episode ends, a five-second countdown starts before the next playable episode.",
                listOf("true" to "On", "false" to "Off"),
                settings.autoPlayNext.toString()
            ) { viewModel.updateAutoPlayNext(it == "true") }
        }
        item {
            AuditSettingChoiceRow(
                "Resume unfinished episodes",
                "When enabled, playback seeks to your last saved position.",
                listOf("true" to "On", "false" to "Off"),
                settings.resumePlayback.toString()
            ) { viewModel.updateResumePlayback(it == "true") }
        }
        item {
            AuditInfoRow(
                "Providers and video quality",
                "Providers differ by title and episode, so there is no global provider list. Choose an available provider on the episode page, then use Quality & Source inside the player to select an available resolution.",
                if (!settings.preferredProvider.equals("Auto", true)) "Reset to Auto" else null
            ) { viewModel.updatePreferredProvider("Auto") }
        }
        item { AuditSettingsHeader("Subtitles") }
        item {
            AuditSettingChoiceRow("Preferred language", "Used by automatic subtitle selection.", listOf("English" to "English", "Spanish" to "Spanish", "Japanese" to "Japanese"), settings.subtitleLanguage) {
                viewModel.updateSubtitleLanguage(it)
            }
        }
        item {
            AuditSettingChoiceRow("Subtitle style", "Applied inside the video player.", listOf("Default" to "Default", "Large" to "Large", "High Contrast" to "High Contrast"), settings.subtitleStyle) {
                viewModel.updateSubtitleStyle(it)
            }
        }
        item { AuditSettingsHeader("History") }
        item {
            AuditInfoRow("Watch history", "Removes Continue Watching and all locally saved progress.", "Clear watch history") { confirmClear = true }
        }
        item { AuditSettingsHeader("App") }
        item { AuditInfoRow("AniStream TV", "Version ${BuildConfig.VERSION_NAME}", null) {} }
    }
}

@Composable
private fun AuditSettingsHeader(text: String) {
    Text(text.uppercase(Locale.ROOT), color = MiruroColors.AccentSoft, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun AuditSettingChoiceRow(
    label: String,
    description: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MiruroColors.Card).padding(16.dp),
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(description, color = MiruroColors.Subtle, fontSize = 12.sp, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            items(options, key = { it.first }) { option ->
                val value = option.first
                FocusableSurface(
                    onClick = { onSelected(value) },
                    modifier = Modifier.width(150.dp).height(48.dp),
                    shape = RoundedCornerShape(7.dp),
                    unfocusedBackground = if (selected == value) MiruroColors.Accent else MiruroColors.Panel,
                    focusedBackground = Color.White
                ) { focused ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (selected == value) "✓ ${option.second}" else option.second, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditInfoRow(label: String, description: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MiruroColors.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(description, color = MiruroColors.Subtle, fontSize = 12.sp, maxLines = 3)
        }
        action?.let { SecondaryButton(it, Modifier.width(210.dp), onAction) }
    }
}

@Composable
fun AuditDetailsScreen(
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
            val unique = remember(details, settings.preferredAudio) { auditUniqueEpisodes(details, settings.preferredAudio) }
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
                    AuditDetailsHero(
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
                    Column(Modifier.padding(horizontal = AuditSafeX, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Episodes", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(16.dp))
                            Text("$watchedCount/${unique.size} watched", color = Color.White.copy(.62f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { AuditPill("All", audioFilter == null) { audioFilter = null } }
                            item { AuditPill("Sub", audioFilter == AudioType.SUB) { audioFilter = AudioType.SUB } }
                            item { AuditPill("Dub", audioFilter == AudioType.DUB) { audioFilter = AudioType.DUB } }
                        }
                    }
                }
                details.seasons.forEach { season ->
                    val displayed = season.episodes.filter { audioFilter == null || it.audioType == audioFilter }
                        .filterNot { ep -> settings.hideWatchedEpisodes && Triple(ep.anilistId, season.seasonNumber, ep.episodeNumber) in watched }
                    if (details.seasons.size > 1) {
                        item {
                            Text("Season ${season.seasonNumber}: ${season.title}", color = Color.White.copy(.78f), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = AuditSafeX, vertical = 12.dp))
                        }
                    }
                    items(displayed.chunked(3)) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = AuditSafeX, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            row.forEach { episode ->
                                val saved = relevant.filter { it.animeId == episode.anilistId && it.seasonNumber == season.seasonNumber && it.episodeNumber == episode.episodeNumber }.maxByOrNull { it.updatedAtMs }
                                AuditEpisodeCard(episode, saved, Modifier.weight(1f)) { onOpenEpisode(season.seasonNumber, episode.episodeNumber, episode.audioType) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private fun auditUniqueEpisodes(details: AnimeDetails, preferred: AudioType): List<AuditEpisodeTarget> =
    details.seasons.flatMap { season ->
        season.episodes.groupBy { it.episodeNumber }.mapNotNull { (_, versions) ->
            val playable = versions.filter { it.sourceCandidates.isNotEmpty() }
            val selected = playable.firstOrNull { it.audioType == preferred } ?: playable.firstOrNull() ?: versions.firstOrNull { it.audioType == preferred } ?: versions.firstOrNull()
            selected?.let { AuditEpisodeTarget(season.seasonNumber, it) }
        }
    }.sortedWith(compareBy<AuditEpisodeTarget> { it.season }.thenBy { it.episode.episodeNumber })

@Composable
private fun AuditDetailsHero(
    details: AnimeDetails,
    inList: Boolean,
    target: AuditEpisodeTarget?,
    isResume: Boolean,
    onBack: () -> Unit,
    onPlay: (AuditEpisodeTarget) -> Unit,
    onList: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(410.dp).background(Color.Black)) {
        AsyncImage(details.bannerUrl ?: details.posterUrl, details.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(.92f), Color.Black.copy(.45f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.25f), Color.Transparent, Color.Black))))
        SecondaryButton("Back", Modifier.align(Alignment.TopStart).padding(28.dp).width(112.dp), onBack)
        Column(Modifier.align(Alignment.BottomStart).padding(start = AuditSafeX, bottom = 34.dp).width(640.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                details.rating?.let { rating ->
                    Text("★ $rating", color = Color(0xFFFFE75A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                }
                Text(listOfNotNull(details.year?.toString(), "${details.seasons.size} season${if (details.seasons.size == 1) "" else "s"}").joinToString(" • "), color = Color.White.copy(.76f), fontSize = 14.sp)
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
                    PrimaryButton(if (isResume) "Resume S${it.season} E${it.episode.episodeNumber}" else "Play S${it.season} E${it.episode.episodeNumber}", Modifier.width(250.dp)) { onPlay(it) }
                }
                SecondaryButton(if (inList) "✓ My List" else "+ Add to List", Modifier.width(190.dp), onList)
            }
        }
    }
}

@Composable
private fun AuditEpisodeCard(episode: AnimeEpisode, progress: WatchProgress?, modifier: Modifier, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, modifier = modifier, unfocusedBackground = MiruroColors.Card) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MiruroColors.CardHigh)) {
                episode.thumbnailUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Box(Modifier.align(Alignment.TopStart).padding(9.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(.75f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
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
fun AuditEpisodeDetailsScreen(
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
    val saved = progress.firstOrNull { it.animeId == episode.anilistId && it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber && it.audioType == episode.audioType }
    val providers = remember(episode) { listOf("Auto") + episode.sourceCandidates.map { it.provider }.distinct() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(start = AuditSafeX, end = AuditSafeX, top = 28.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { SecondaryButton("Back", Modifier.width(112.dp), onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f).height(240.dp).clip(RoundedCornerShape(6.dp)).background(MiruroColors.CardHigh)) {
                    episode.thumbnailUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Column(Modifier.weight(1.15f)) {
                    Text("Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(episode.title ?: "Episode ${episode.episodeNumber}", color = Color.White.copy(.76f), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Text("Choose from providers available for this episode. Video resolutions appear under Quality & Source after playback resolves.", color = MiruroColors.Subtle, fontSize = 13.sp)
                }
            }
        }
        if (episode.sourceCandidates.isNotEmpty()) {
            item {
                Text("Available providers", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(providers, key = { it }) { provider ->
                        SecondaryButton(if (provider.equals(settings.preferredProvider, true)) "✓ $provider" else provider, Modifier.width(160.dp)) {
                            viewModel.updatePreferredProvider(provider)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Play episode", Modifier.width(210.dp), onPlay)
                    SecondaryButton(if (saved?.watched == true) "Mark unwatched" else "Mark watched", Modifier.width(210.dp)) {
                        viewModel.setEpisodeWatched(episode, saved?.watched != true)
                    }
                }
            }
        } else item { StateMessage("No playable source is currently available for this episode.") }
    }
}
