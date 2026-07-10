package com.ttvralph.miruroapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.data.WatchlistEntry
import com.ttvralph.miruroapp.data.WatchlistSort
import com.ttvralph.miruroapp.ui.LandscapeCard
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.SectionTitle
import com.ttvralph.miruroapp.ui.StateMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchManagementScreen(
    viewModel: MiruroViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit
) {
    val entries by viewModel.watchlistEntries.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }

    val favoriteIds = remember(entries) { entries.map { it.id }.toSet() }
    val progressIds = remember(progress) { progress.map { it.animeId }.toSet() }
    val unfinished = remember(progress) { progress.filterNot { it.watched }.take(20) }
    val completed = remember(progress) { progress.filter { it.watched } }

    LaunchedEffect(favoriteIds) { viewModel.resolveFavoriteMetadata(favoriteIds) }
    LaunchedEffect(progressIds) { viewModel.resolveProgressMetadata(progress) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear all watch history?") },
            text = { Text("This removes every saved episode position, Continue Watching entry, and completed episode. Your My List items stay saved.") },
            confirmButton = {
                SecondaryButton(
                    text = "Clear all",
                    modifier = Modifier.width(160.dp),
                    onClick = {
                        viewModel.clearWatchProgress()
                        showClearHistoryDialog = false
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    modifier = Modifier.width(130.dp),
                    onClick = { showClearHistoryDialog = false }
                )
            }
        )
    }

    if (showClearCompletedDialog) {
        AlertDialog(
            onDismissRequest = { showClearCompletedDialog = false },
            title = { Text("Clear completed history?") },
            text = { Text("This removes completed episodes from Recently Watched. Unfinished Continue Watching progress and My List stay unchanged.") },
            confirmButton = {
                SecondaryButton(
                    text = "Clear completed",
                    modifier = Modifier.width(190.dp),
                    onClick = {
                        completed.forEach { item ->
                            viewModel.setEpisodeWatched(item.toEpisodeStub(), false)
                        }
                        showClearCompletedDialog = false
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    modifier = Modifier.width(130.dp),
                    onClick = { showClearCompletedDialog = false }
                )
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            SectionTitle("My List & Watch History")
            Text(
                text = "Resume unfinished episodes, replay completed ones, and manage saved anime in one place.",
                color = MiruroColors.Muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            SectionTitle(
                text = "Continue Watching",
                badge = "RESUME",
                trailing = if (progress.isNotEmpty()) {
                    {
                        SecondaryButton(
                            text = "Clear all history",
                            modifier = Modifier.width(190.dp),
                            onClick = { showClearHistoryDialog = true }
                        )
                    }
                } else {
                    null
                }
            )
        }

        if (unfinished.isEmpty()) {
            item { StateMessage("Nothing is waiting in Continue Watching.") }
        } else {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    items(unfinished, key = { it.key }) { item ->
                        val anime = viewModel.cachedItem(item.animeId) ?: item.toFallbackAnimeItem()
                        Column(modifier = Modifier.width(330.dp)) {
                            LandscapeCard(
                                item = anime,
                                width = 330.dp,
                                height = 180.dp,
                                progressPercent = item.percent,
                                onClick = { onPlayProgress(item) }
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = item.progressLabel(),
                                color = MiruroColors.AccentSoft,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatTimeShort(item.positionMs)} of ${formatTimeShort(item.durationMs)}",
                                color = MiruroColors.Muted,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton(
                                    text = "Details",
                                    modifier = Modifier.width(150.dp),
                                    onClick = { onOpenDetails(item.animeId) }
                                )
                                SecondaryButton(
                                    text = "Remove",
                                    modifier = Modifier.width(150.dp),
                                    onClick = { viewModel.setEpisodeWatched(item.toEpisodeStub(), false) }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            SecondaryButton(
                                text = "Mark episode watched",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.setEpisodeWatched(item.toEpisodeStub(), true) }
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionTitle(
                text = "Recently Watched",
                badge = "HISTORY",
                trailing = if (completed.isNotEmpty()) {
                    {
                        SecondaryButton(
                            text = "Clear completed",
                            modifier = Modifier.width(185.dp),
                            onClick = { showClearCompletedDialog = true }
                        )
                    }
                } else {
                    null
                }
            )
        }

        if (completed.isEmpty()) {
            item { StateMessage("Completed episodes will appear here, newest first.") }
        } else {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    items(completed.take(20), key = { it.key }) { item ->
                        val anime = viewModel.cachedItem(item.animeId) ?: item.toFallbackAnimeItem()
                        Column(modifier = Modifier.width(330.dp)) {
                            LandscapeCard(
                                item = anime,
                                width = 330.dp,
                                height = 180.dp,
                                progressPercent = 1f,
                                onClick = { onPlayProgress(item) }
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = item.progressLabel(),
                                color = MiruroColors.AccentSoft,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Watched ${formatHistoryDate(item.updatedAtMs)}",
                                color = MiruroColors.Muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton(
                                    text = "Replay",
                                    modifier = Modifier.width(150.dp),
                                    onClick = { onPlayProgress(item) }
                                )
                                SecondaryButton(
                                    text = "Details",
                                    modifier = Modifier.width(150.dp),
                                    onClick = { onOpenDetails(item.animeId) }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton(
                                    text = "Mark unwatched",
                                    modifier = Modifier.width(190.dp),
                                    onClick = { markProgressUnwatched(viewModel, item) }
                                )
                                SecondaryButton(
                                    text = "Remove",
                                    modifier = Modifier.width(110.dp),
                                    onClick = { viewModel.setEpisodeWatched(item.toEpisodeStub(), false) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("My List", "SAVED")
        }

        if (entries.isEmpty()) {
            item { StateMessage("Your My List is empty. Add anime from Home or Details.") }
        } else {
            val sortedEntries = sortWatchlist(entries, progress, settings.watchlistSort, viewModel)
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    items(sortedEntries, key = { it.id }) { entry ->
                        val anime = viewModel.cachedItem(entry.id)
                            ?: entry.toFallbackAnimeItem()
                        val latestProgress = progress.firstOrNull { it.animeId == entry.id }

                        Column(modifier = Modifier.width(posterWidth(settings.posterGridDensity))) {
                            PosterCard(
                                item = anime,
                                width = posterWidth(settings.posterGridDensity),
                                onClick = { onOpenDetails(entry.id) }
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = latestProgress?.progressLabel() ?: "Not started",
                                color = if (latestProgress == null) MiruroColors.Muted else MiruroColors.AccentSoft,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Saved ${formatSavedDate(entry.addedAtMs)}",
                                color = MiruroColors.Muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            SecondaryButton(
                                text = "Remove from My List",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.toggleFavorite(entry.id) }
                            )
                            if (latestProgress != null) {
                                Spacer(Modifier.height(8.dp))
                                SecondaryButton(
                                    text = if (latestProgress.watched) "Mark episode unwatched" else "Mark episode watched",
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        if (latestProgress.watched) {
                                            markProgressUnwatched(viewModel, latestProgress)
                                        } else {
                                            viewModel.setEpisodeWatched(latestProgress.toEpisodeStub(), true)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sortWatchlist(
    entries: List<WatchlistEntry>,
    progress: List<WatchProgress>,
    sort: WatchlistSort,
    viewModel: MiruroViewModel
): List<WatchlistEntry> = when (sort) {
    WatchlistSort.TITLE -> entries.sortedBy { entry ->
        viewModel.cachedItem(entry.id)?.title ?: entry.title.orEmpty()
    }

    WatchlistSort.PROGRESS -> entries.sortedByDescending { entry ->
        progress.firstOrNull { it.animeId == entry.id }?.percent ?: 0f
    }

    WatchlistSort.RECENTLY_ADDED -> entries.sortedByDescending { it.addedAtMs }
}

private fun markProgressUnwatched(viewModel: MiruroViewModel, item: WatchProgress) {
    val duration = item.durationMs.coerceAtLeast(10_000L)
    val position = minOf(item.positionMs, duration / 2L).coerceAtLeast(1_000L)
    viewModel.saveProgress(item.toEpisodeStub(), position, duration)
}

private fun WatchProgress.toEpisodeStub(): AnimeEpisode = AnimeEpisode(
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = null,
    thumbnailUrl = null,
    runtimeMinutes = null,
    releaseDate = null,
    audioType = audioType,
    anilistId = animeId
)

private fun WatchProgress.toFallbackAnimeItem(): AnimeItem = AnimeItem(
    id = animeId,
    title = "Anime #$animeId",
    posterUrl = null,
    bannerUrl = null,
    type = AnimeType.UNKNOWN
)

private fun WatchlistEntry.toFallbackAnimeItem(): AnimeItem = AnimeItem(
    id = id,
    title = title ?: "Anime #$id",
    posterUrl = posterUrl,
    bannerUrl = null,
    type = AnimeType.UNKNOWN
)

private fun WatchProgress.progressLabel(): String {
    val percentage = (percent * 100).toInt().coerceIn(0, 100)
    return if (watched) {
        "S$seasonNumber E$episodeNumber • Watched"
    } else {
        "S$seasonNumber E$episodeNumber • $percentage% watched"
    }
}

private fun formatTimeShort(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatSavedDate(milliseconds: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(milliseconds))

private fun formatHistoryDate(milliseconds: Long): String =
    SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date(milliseconds))

private fun posterWidth(density: PosterGridDensity) = when (density) {
    PosterGridDensity.COMPACT -> 150.dp
    PosterGridDensity.COMFORTABLE -> 180.dp
    PosterGridDensity.LARGE -> 220.dp
}
