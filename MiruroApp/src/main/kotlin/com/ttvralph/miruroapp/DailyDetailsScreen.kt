package com.ttvralph.miruroapp

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeSeason
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

@Composable
fun DailyDetailsScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
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
    val seasonLoading by viewModel.seasonLoading.collectAsState()
    val seasonErrors by viewModel.seasonErrors.collectAsState()
    var audioFilter by remember(animeId, settings.preferredAudio) {
        mutableStateOf<AudioType?>(settings.preferredAudio)
    }
    var modalContent by remember(animeId) { mutableStateOf<SynopsisModalContent?>(null) }

    when (val current = state) {
        is UiState.Loading -> LoadingState("Loading title…")
        is UiState.Error -> ErrorState(current.message) { viewModel.loadDetails(animeId) }
        is UiState.Success -> {
            val details = current.data
            val firstSeason = details.seasons.firstOrNull()
            LaunchedEffect(details.id, firstSeason?.id) {
                firstSeason?.takeUnless { it.episodesLoaded }?.let { viewModel.loadSeason(it.id) }
            }

            val targets = remember(details, settings.preferredAudio) {
                dailyUniqueEpisodes(details, settings.preferredAudio)
            }
            val watchedKeys = remember(progress) { dailyWatchedKeys(progress) }
            val hiddenKeys = remember(details, progress, settings.preferredAudio, settings.noSpoilerMode) {
                dailySpoilerHiddenKeys(
                    details,
                    progress,
                    settings.preferredAudio,
                    settings.noSpoilerMode
                )
            }
            val titleSummary = remember(details, progress, settings.preferredAudio) {
                dailyTitleProgress(details, progress, settings.preferredAudio)
            }
            val knownEpisodeTotal = remember(details) {
                details.seasons.sumOf { season ->
                    season.episodeCount ?: season.episodes.distinctBy { it.episodeNumber }.size
                }
            }
            val latestPartial = remember(progress, targets) {
                targets.mapNotNull { target -> dailyLatestProgress(progress, target) }
                    .filterNot { it.watched }
                    .maxByOrNull { it.updatedAtMs }
            }
            val smartTarget = remember(targets, watchedKeys, latestPartial) {
                latestPartial?.let { saved ->
                    targets.firstOrNull {
                        it.episode.anilistId == saved.animeId &&
                            it.seasonNumber == saved.seasonNumber &&
                            it.episode.episodeNumber == saved.episodeNumber
                    }
                } ?: targets.firstOrNull { target ->
                    dailyProgressKey(
                        target.episode.anilistId,
                        target.seasonNumber,
                        target.episode.episodeNumber
                    ) !in watchedKeys
                } ?: targets.firstOrNull()
            }

            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        DailyDetailsHero(
                            details = details,
                            inList = details.id in favorites,
                            target = smartTarget,
                            isResume = latestPartial != null && smartTarget != null,
                            watched = titleSummary.watched,
                            knownTotal = knownEpisodeTotal,
                            onBack = onBack,
                            onPlay = { target ->
                                onPlayEpisode(
                                    target.seasonNumber,
                                    target.episode.episodeNumber,
                                    target.episode.audioType
                                )
                            },
                            onList = { viewModel.toggleFavorite(details.id) }
                        )
                    }

                    item {
                        Column(Modifier.padding(horizontal = ReliableSafeX, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Episodes", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    if (knownEpisodeTotal > 0) {
                                        "${titleSummary.watched}/$knownEpisodeTotal watched"
                                    } else {
                                        "Episodes load by season"
                                    },
                                    color = Color.White.copy(alpha = 0.64f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (settings.noSpoilerMode) {
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "NO-SPOILER MODE",
                                        color = MiruroColors.AccentSoft,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (knownEpisodeTotal > 0) {
                                        titleSummary.watched.toFloat() / knownEpisodeTotal.toFloat()
                                    } else 0f
                                },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MiruroColors.Accent,
                                trackColor = Color.White.copy(alpha = 0.16f)
                            )
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { DailyFilterPill("All", audioFilter == null) { audioFilter = null } }
                                item { DailyFilterPill("Sub", audioFilter == AudioType.SUB) { audioFilter = AudioType.SUB } }
                                item { DailyFilterPill("Dub", audioFilter == AudioType.DUB) { audioFilter = AudioType.DUB } }
                            }
                        }
                    }

                    details.seasons.forEach { season ->
                        val seasonTargets = targets.filter { it.seasonNumber == season.seasonNumber }
                        val seasonWatched = seasonTargets.count { target ->
                            dailyProgressKey(
                                target.episode.anilistId,
                                target.seasonNumber,
                                target.episode.episodeNumber
                            ) in watchedKeys
                        }
                        val seasonTotal = season.episodeCount ?: seasonTargets.size
                        val seasonPercent = if (seasonTotal > 0) {
                            seasonWatched.toFloat() / seasonTotal.toFloat()
                        } else 0f
                        val nextTarget = seasonTargets.firstOrNull { target ->
                            dailyProgressKey(
                                target.episode.anilistId,
                                target.seasonNumber,
                                target.episode.episodeNumber
                            ) !in watchedKeys
                        }
                        val isLoading = season.id in seasonLoading
                        val loadError = seasonErrors[season.id]

                        item {
                            DailySeasonHeader(
                                season = season,
                                watched = seasonWatched,
                                total = seasonTotal,
                                percent = seasonPercent,
                                nextTarget = nextTarget,
                                loading = isLoading,
                                loadError = loadError,
                                onLoad = { viewModel.loadSeason(season.id, force = loadError != null) },
                                onReadMore = season.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
                                    {
                                        modalContent = SynopsisModalContent(
                                            eyebrow = "Season synopsis",
                                            title = season.title,
                                            metadata = listOfNotNull(
                                                season.year?.toString(),
                                                season.episodeCount?.let { "$it episodes" }
                                            ).joinToString(" • ").takeIf { it.isNotBlank() },
                                            synopsis = synopsis
                                        )
                                    }
                                },
                                onPlayNext = { target ->
                                    onPlayEpisode(
                                        target.seasonNumber,
                                        target.episode.episodeNumber,
                                        target.episode.audioType
                                    )
                                },
                                onMarkWatched = {
                                    features.setSeasonWatched(season.episodes, seasonWatched < seasonTotal)
                                },
                                onRestart = { features.setSeasonWatched(season.episodes, false) }
                            )
                        }

                        if (!season.episodesLoaded) {
                            item {
                                Text(
                                    when {
                                        isLoading -> "Loading this season's episodes…"
                                        loadError != null -> loadError
                                        else -> "Episodes load only when this season is opened, keeping Details fast."
                                    },
                                    color = if (loadError != null) Color(0xFFFFA3A3) else Color.White.copy(alpha = 0.52f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = ReliableSafeX + 16.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            val displayed = season.episodes
                                .filter { audioFilter == null || it.audioType == audioFilter }
                                .filterNot { episode ->
                                    settings.hideWatchedEpisodes && dailyProgressKey(
                                        episode.anilistId,
                                        season.seasonNumber,
                                        episode.episodeNumber
                                    ) in watchedKeys
                                }

                            items(displayed.chunked(3)) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = ReliableSafeX, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    row.forEach { episode ->
                                        val key = dailyProgressKey(
                                            episode.anilistId,
                                            season.seasonNumber,
                                            episode.episodeNumber
                                        )
                                        val saved = progress
                                            .filter {
                                                it.animeId == episode.anilistId &&
                                                    it.seasonNumber == season.seasonNumber &&
                                                    it.episodeNumber == episode.episodeNumber &&
                                                    it.audioType == episode.audioType
                                            }
                                            .maxByOrNull { it.updatedAtMs }
                                        val hideSpoilers = key in hiddenKeys
                                        DailyEpisodeCard(
                                            episode = episode,
                                            progress = saved,
                                            watched = key in watchedKeys,
                                            isNew = key !in watchedKeys && dailyIsRecentEpisode(episode),
                                            hideSpoilers = hideSpoilers,
                                            modifier = Modifier.weight(1f),
                                            onReadMore = episode.synopsis
                                                ?.takeIf { it.isNotBlank() && !hideSpoilers }
                                                ?.let { synopsis ->
                                                    {
                                                        modalContent = SynopsisModalContent(
                                                            eyebrow = "Episode synopsis",
                                                            title = episode.title ?: "Episode ${episode.episodeNumber}",
                                                            metadata = "Season ${season.seasonNumber} • Episode ${episode.episodeNumber} • ${episode.audioType.name}",
                                                            synopsis = synopsis
                                                        )
                                                    }
                                                }
                                        ) {
                                            onOpenEpisode(
                                                season.seasonNumber,
                                                episode.episodeNumber,
                                                episode.audioType
                                            )
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }

                modalContent?.let { content ->
                    SynopsisReadMoreModal(content = content) { modalContent = null }
                }
            }
        }
    }
}

@Composable
private fun DailyDetailsHero(
    details: AnimeDetails,
    inList: Boolean,
    target: DailyEpisodeTarget?,
    isResume: Boolean,
    watched: Int,
    knownTotal: Int,
    onBack: () -> Unit,
    onPlay: (DailyEpisodeTarget) -> Unit,
    onList: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(410.dp).background(Color.Black)) {
        AsyncImage(
            model = details.bannerUrl ?: details.posterUrl,
            contentDescription = details.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black, Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.45f), Color.Transparent)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black))
            )
        )
        SecondaryButton("Back", Modifier.align(Alignment.TopStart).padding(28.dp).width(112.dp), onBack)
        Column(Modifier.align(Alignment.BottomStart).padding(start = ReliableSafeX, bottom = 32.dp).width(660.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                details.rating?.let { rating ->
                    Text("★ $rating", color = Color(0xFFFFE75A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                }
                Text(
                    listOfNotNull(
                        details.year?.toString(),
                        "${details.seasons.size} season${if (details.seasons.size == 1) "" else "s"}",
                        knownTotal.takeIf { it > 0 }?.let { "$watched/$it watched" }
                    ).joinToString(" • "),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                details.title,
                color = Color.White,
                fontSize = 38.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(9.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(details.genres.take(4), key = { it }) { GenreChip(it) }
            }
            Spacer(Modifier.height(9.dp))
            BodyText(details.description ?: "No synopsis available.", maxLines = 2)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                target?.let {
                    PrimaryButton(
                        if (isResume) "Resume S${it.seasonNumber} E${it.episode.episodeNumber}"
                        else "Play S${it.seasonNumber} E${it.episode.episodeNumber}",
                        Modifier.width(260.dp)
                    ) { onPlay(it) }
                }
                SecondaryButton(
                    if (inList) "✓ My List" else "+ Add to List",
                    Modifier.width(190.dp),
                    onList
                )
            }
        }
    }
}

@Composable
private fun DailySeasonHeader(
    season: AnimeSeason,
    watched: Int,
    total: Int,
    percent: Float,
    nextTarget: DailyEpisodeTarget?,
    loading: Boolean,
    loadError: String?,
    onLoad: () -> Unit,
    onReadMore: (() -> Unit)?,
    onPlayNext: (DailyEpisodeTarget) -> Unit,
    onMarkWatched: () -> Unit,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReliableSafeX, vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Season ${season.seasonNumber}: ${season.title}",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (season.episodesLoaded) {
                        "$watched of $total episodes watched • ${(percent * 100).toInt()}%"
                    } else {
                        listOfNotNull(
                            season.episodeCount?.let { "$it episodes" },
                            season.year?.toString(),
                            "loads on demand"
                        ).joinToString(" • ")
                    },
                    color = MiruroColors.Subtle,
                    fontSize = 13.sp
                )
            }

            if (!season.episodesLoaded) {
                PrimaryButton(
                    when {
                        loading -> "Loading…"
                        loadError != null -> "Retry episodes"
                        else -> "Load episodes"
                    },
                    Modifier.width(190.dp)
                ) {
                    if (!loading) onLoad()
                }
            } else {
                nextTarget?.let { target ->
                    PrimaryButton(
                        "Play next E${target.episode.episodeNumber}",
                        Modifier.width(190.dp)
                    ) { onPlayNext(target) }
                    Spacer(Modifier.width(10.dp))
                }
                SecondaryButton(
                    if (total > 0 && watched >= total) "Mark unwatched" else "Mark season watched",
                    Modifier.width(210.dp),
                    onMarkWatched
                )
                Spacer(Modifier.width(10.dp))
                SecondaryButton("Restart season", Modifier.width(170.dp), onRestart)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            season.synopsis ?: "Season synopsis unavailable.",
            color = Color.White.copy(alpha = if (season.synopsis != null) 0.74f else 0.48f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (onReadMore != null) {
            Spacer(Modifier.height(9.dp))
            SecondaryButton("Read more", Modifier.width(145.dp), onReadMore)
        }

        if (season.episodesLoaded) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { percent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = MiruroColors.Accent,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun DailyEpisodeCard(
    episode: AnimeEpisode,
    progress: WatchProgress?,
    watched: Boolean,
    isNew: Boolean,
    hideSpoilers: Boolean,
    modifier: Modifier,
    onReadMore: (() -> Unit)?,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiruroColors.Card)
            .padding(bottom = 10.dp)
    ) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            unfocusedBackground = MiruroColors.Card
        ) { focused ->
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MiruroColors.CardHigh)) {
                    if (!hideSpoilers) {
                        episode.thumbnailUrl?.let {
                            AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(Color(0xFF161616), Color(0xFF292929)))
                            )
                        )
                        Text(
                            "Spoiler hidden",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DailyEpisodeBadge(episode.audioType.name, Color.Black.copy(alpha = 0.78f))
                        if (isNew) DailyEpisodeBadge("NEW", MiruroColors.Accent)
                        if (watched) DailyEpisodeBadge("✓ WATCHED", Color(0xFF1B6E35))
                    }

                    progress?.let {
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                                .background(Color.White.copy(alpha = 0.18f))
                        )
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth(it.percent.coerceIn(0f, 1f)).height(4.dp)
                                .background(MiruroColors.Accent)
                        )
                    }
                }
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "E${episode.episodeNumber} • ${episode.audioType.name}" +
                            progress?.takeUnless { it.watched }?.let { " • ${(it.percent * 100).toInt()}%" }.orEmpty(),
                        color = if (focused) Color.Black else MiruroColors.AccentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (hideSpoilers) "Episode ${episode.episodeNumber}" else episode.title ?: "Episode ${episode.episodeNumber}",
                        color = if (focused) Color.Black else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            when {
                hideSpoilers -> "Synopsis hidden by No-Spoiler Mode."
                !episode.synopsis.isNullOrBlank() -> episode.synopsis.orEmpty()
                else -> "Synopsis unavailable."
            },
            color = Color.White.copy(
                alpha = if (!episode.synopsis.isNullOrBlank() && !hideSpoilers) 0.68f else 0.45f
            ),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (onReadMore != null) {
            SecondaryButton(
                "Read more",
                Modifier.padding(horizontal = 12.dp).width(135.dp),
                onReadMore
            )
        }
    }
}

@Composable
private fun DailyEpisodeBadge(text: String, background: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(5.dp)).background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DailyFilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    SecondaryButton(if (selected) "✓ $text" else text, Modifier.width(105.dp), onClick)
}
