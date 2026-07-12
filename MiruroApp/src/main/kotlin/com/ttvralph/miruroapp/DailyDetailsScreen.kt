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
    var audioFilter by remember(animeId, settings.preferredAudio) {
        mutableStateOf<AudioType?>(settings.preferredAudio)
    }

    when (val current = state) {
        is UiState.Loading -> LoadingState("Loading details…")
        is UiState.Error -> ErrorState(current.message) { viewModel.loadDetails(animeId) }
        is UiState.Success -> {
            val details = current.data
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
                        summary = titleSummary,
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
                                "${titleSummary.watched}/${titleSummary.total} watched",
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
                            progress = { titleSummary.percent },
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
                    val seasonTotal = seasonTargets.size
                    val seasonPercent = if (seasonTotal > 0) seasonWatched.toFloat() / seasonTotal else 0f
                    val nextTarget = seasonTargets.firstOrNull { target ->
                        dailyProgressKey(
                            target.episode.anilistId,
                            target.seasonNumber,
                            target.episode.episodeNumber
                        ) !in watchedKeys
                    }

                    item {
                        DailySeasonHeader(
                            season = season,
                            watched = seasonWatched,
                            total = seasonTotal,
                            percent = seasonPercent,
                            nextTarget = nextTarget,
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
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
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
                                DailyEpisodeCard(
                                    episode = episode,
                                    progress = saved,
                                    watched = key in watchedKeys,
                                    isNew = key !in watchedKeys && dailyIsRecentEpisode(episode),
                                    hideSpoilers = key in hiddenKeys,
                                    modifier = Modifier.weight(1f)
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
    }
}

@Composable
private fun DailyDetailsHero(
    details: AnimeDetails,
    inList: Boolean,
    target: DailyEpisodeTarget?,
    isResume: Boolean,
    summary: DailyTitleProgress,
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
                        "${summary.watched}/${summary.total} watched"
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
                    "$watched of $total episodes watched • ${(percent * 100).toInt()}%",
                    color = MiruroColors.Subtle,
                    fontSize = 13.sp
                )
            }
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
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { percent.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = MiruroColors.Accent,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
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
    onClick: () -> Unit
) {
    FocusableSurface(onClick = onClick, modifier = modifier, unfocusedBackground = MiruroColors.Card) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MiruroColors.CardHigh)) {
                if (!hideSpoilers) {
                    episode.thumbnailUrl?.let {
                        AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF161616), Color(0xFF292929)))))
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
