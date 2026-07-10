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
import androidx.compose.material3.OutlinedTextField
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
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.data.WatchlistEntry
import com.ttvralph.miruroapp.data.WatchlistSort
import com.ttvralph.miruroapp.ui.LandscapeCard
import com.ttvralph.miruroapp.ui.GenreChip
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.SectionTitle
import com.ttvralph.miruroapp.ui.StateMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AudioFilter(val label: String) { ALL("All audio"), SUB("Sub"), DUB("Dub") }
private enum class ContinueSort(val label: String) {
    RECENT("Recent"), TITLE("Title"), MOST_PROGRESS("Most progress"), LEAST_PROGRESS("Least progress")
}
private enum class HistorySort(val label: String) {
    NEWEST("Newest"), OLDEST("Oldest"), TITLE("Title"), EPISODE("Episode")
}
private enum class ListStateFilter(val label: String) {
    ALL("All titles"), NOT_STARTED("Not started"), IN_PROGRESS("In progress"), COMPLETED("Completed")
}
private enum class ListSort(val label: String) {
    RECENTLY_ADDED("Recently added"), TITLE("Title"), PROGRESS("Progress"), RECENT_ACTIVITY("Recent activity")
}

@Composable
fun WatchManagementScreen(
    viewModel: MiruroViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit
) {
    val entries by viewModel.watchlistEntries.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val itemMetadataVersion by viewModel.itemMetadataVersion.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var continueAudio by remember { mutableStateOf(AudioFilter.ALL) }
    var continueSort by remember { mutableStateOf(ContinueSort.RECENT) }
    var historyAudio by remember { mutableStateOf(AudioFilter.ALL) }
    var historySort by remember { mutableStateOf(HistorySort.NEWEST) }
    var listState by remember { mutableStateOf(ListStateFilter.ALL) }
    var listSort by remember(settings.watchlistSort) {
        mutableStateOf(
            when (settings.watchlistSort) {
                WatchlistSort.RECENTLY_ADDED -> ListSort.RECENTLY_ADDED
                WatchlistSort.TITLE -> ListSort.TITLE
                WatchlistSort.PROGRESS -> ListSort.PROGRESS
            }
        )
    }
    var showAllContinue by remember { mutableStateOf(false) }
    var showAllHistory by remember { mutableStateOf(false) }

    val favoriteIds = remember(entries, itemMetadataVersion) { entries.map { it.id }.toSet() }
    val progressIds = remember(progress) { progress.map { it.animeId }.toSet() }
    val unfinished = remember(progress) { progress.filterNot { it.watched } }
    val completed = remember(progress) { progress.filter { it.watched } }
    val normalizedQuery = query.trim()
    fun titleFor(id: Int, fallback: String? = null): String =
        viewModel.cachedItem(id)?.title ?: fallback ?: "Anime #$id"
    fun matchesQuery(id: Int, fallback: String? = null): Boolean = normalizedQuery.isEmpty() ||
        titleFor(id, fallback).contains(normalizedQuery, ignoreCase = true) || id.toString() == normalizedQuery
    fun matchesAudio(item: WatchProgress, filter: AudioFilter): Boolean = when (filter) {
        AudioFilter.ALL -> true
        AudioFilter.SUB -> item.audioType == AudioType.SUB
        AudioFilter.DUB -> item.audioType == AudioType.DUB
    }
    val matchingContinue = unfinished
        .filter { matchesQuery(it.animeId) && matchesAudio(it, continueAudio) }
        .let { items ->
            when (continueSort) {
                ContinueSort.RECENT -> items.sortedByDescending { it.updatedAtMs }
                ContinueSort.TITLE -> items.sortedBy { titleFor(it.animeId).lowercase(Locale.ROOT) }
                ContinueSort.MOST_PROGRESS -> items.sortedByDescending { it.percent }
                ContinueSort.LEAST_PROGRESS -> items.sortedBy { it.percent }
            }
        }
    val visibleContinue = if (showAllContinue) matchingContinue else matchingContinue.take(20)
    val matchingHistory = completed
        .filter { matchesQuery(it.animeId) && matchesAudio(it, historyAudio) }
        .let { items ->
            when (historySort) {
                HistorySort.NEWEST -> items.sortedByDescending { it.updatedAtMs }
                HistorySort.OLDEST -> items.sortedBy { it.updatedAtMs }
                HistorySort.TITLE -> items.sortedBy { titleFor(it.animeId).lowercase(Locale.ROOT) }
                HistorySort.EPISODE -> items.sortedWith(compareBy({ titleFor(it.animeId).lowercase(Locale.ROOT) }, { it.seasonNumber }, { it.episodeNumber }))
            }
        }
    val visibleHistory = if (showAllHistory) matchingHistory else matchingHistory.take(20)
    val visibleEntries = sortWatchlist(entries.filter { entry ->
        val itemProgress = progress.filter { it.animeId == entry.id }
        matchesQuery(entry.id, entry.title) && when (listState) {
            ListStateFilter.ALL -> true
            ListStateFilter.NOT_STARTED -> itemProgress.isEmpty()
            ListStateFilter.IN_PROGRESS -> itemProgress.any { !it.watched }
            ListStateFilter.COMPLETED -> itemProgress.isNotEmpty() && itemProgress.all { it.watched }
        }
    }, progress, listSort, viewModel)
    val totalMatches = matchingContinue.size + matchingHistory.size + visibleEntries.size
    val controlsActive = normalizedQuery.isNotEmpty() || continueAudio != AudioFilter.ALL ||
        continueSort != ContinueSort.RECENT || historyAudio != AudioFilter.ALL ||
        historySort != HistorySort.NEWEST || listState != ListStateFilter.ALL ||
        listSort != ListSort.RECENTLY_ADDED || showAllContinue || showAllHistory

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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search saved titles or AniList ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$totalMatches matching • ${unfinished.size + completed.size + entries.size} total entries",
                    color = MiruroColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 19.dp)
                )
                if (query.isNotEmpty()) {
                    SecondaryButton("Clear search", Modifier.width(150.dp)) { query = "" }
                }
                if (controlsActive) {
                    SecondaryButton("Reset controls", Modifier.width(165.dp)) {
                        query = ""
                        continueAudio = AudioFilter.ALL
                        continueSort = ContinueSort.RECENT
                        historyAudio = AudioFilter.ALL
                        historySort = HistorySort.NEWEST
                        listState = ListStateFilter.ALL
                        listSort = ListSort.RECENTLY_ADDED
                        showAllContinue = false
                        showAllHistory = false
                    }
                }
            }
            if (normalizedQuery.isNotEmpty() && totalMatches == 0) {
                StateMessage("No saved titles match “$normalizedQuery”. Clear search or change a filter.")
            }
        }

        item {
            SectionTitle(
                text = "Continue Watching",
                badge = "${unfinished.size} RESUME",
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

        item {
            FilterRow(AudioFilter.entries, continueAudio, { it.label }) { continueAudio = it }
            FilterRow(ContinueSort.entries, continueSort, { it.label }) { continueSort = it }
            if (unfinished.size > 20) {
                SecondaryButton(
                    text = if (showAllContinue) "Show first 20" else "Show all ${unfinished.size}",
                    modifier = Modifier.width(180.dp),
                    onClick = { showAllContinue = !showAllContinue }
                )
            }
        }

        if (visibleContinue.isEmpty()) {
            item { StateMessage(if (unfinished.isEmpty()) "Nothing is waiting in Continue Watching." else "No Continue Watching entries match these controls.") }
        } else {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    items(visibleContinue, key = { it.key }) { item ->
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
                                text = "${formatTimeShort(item.positionMs)} of ${formatTimeShort(item.durationMs)} • ${formatRemaining(item)} left",
                                color = MiruroColors.Muted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${item.audioType.name} • ${formatRelativeActivity(item.updatedAtMs)}",
                                color = MiruroColors.Muted,
                                fontSize = 11.sp
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
                badge = "${completed.size} HISTORY",
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

        item {
            FilterRow(AudioFilter.entries, historyAudio, { it.label }) { historyAudio = it }
            FilterRow(HistorySort.entries, historySort, { it.label }) { historySort = it }
            if (completed.size > 20) {
                SecondaryButton(
                    text = if (showAllHistory) "Show first 20" else "Show all ${completed.size}",
                    modifier = Modifier.width(180.dp),
                    onClick = { showAllHistory = !showAllHistory }
                )
            }
        }

        if (visibleHistory.isEmpty()) {
            item { StateMessage(if (completed.isEmpty()) "Completed episodes will appear here, newest first." else "No Recently Watched entries match these controls.") }
        } else {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    items(visibleHistory, key = { it.key }) { item ->
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
                                text = "${item.audioType.name} • Watched ${formatHistoryDate(item.updatedAtMs)}",
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
            SectionTitle("My List", "${entries.size} SAVED")
            FilterRow(ListStateFilter.entries, listState, { it.label }) { listState = it }
            FilterRow(ListSort.entries, listSort, { it.label }) { listSort = it }
        }

        if (visibleEntries.isEmpty()) {
            item { StateMessage(if (entries.isEmpty()) "Your My List is empty. Add anime from Home or Details." else "No My List titles match these controls.") }
        } else {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    items(visibleEntries, key = { it.id }) { entry ->
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
                            latestProgress?.let {
                                Text(
                                    text = "Last activity ${formatRelativeActivity(it.updatedAtMs)}",
                                    color = MiruroColors.Muted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
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
    sort: ListSort,
    viewModel: MiruroViewModel
): List<WatchlistEntry> = when (sort) {
    ListSort.TITLE -> entries.sortedBy { entry ->
        viewModel.cachedItem(entry.id)?.title ?: entry.title.orEmpty()
    }

    ListSort.PROGRESS -> entries.sortedByDescending { entry ->
        progress.firstOrNull { it.animeId == entry.id }?.percent ?: 0f
    }

    ListSort.RECENTLY_ADDED -> entries.sortedByDescending { it.addedAtMs }
    ListSort.RECENT_ACTIVITY -> entries.sortedByDescending { entry ->
        progress.firstOrNull { it.animeId == entry.id }?.updatedAtMs ?: 0L
    }
}

@Composable
private fun <T> FilterRow(
    options: Iterable<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 5.dp)
    ) {
        items(options.toList()) { option ->
            GenreChip(
                text = label(option),
                selected = option == selected,
                onClick = { onSelected(option) }
            )
        }
    }
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

private fun formatRemaining(item: WatchProgress): String =
    formatTimeShort((item.durationMs - item.positionMs).coerceAtLeast(0L))

private fun formatRelativeActivity(milliseconds: Long): String {
    val elapsedMinutes = ((System.currentTimeMillis() - milliseconds).coerceAtLeast(0L) / 60_000L)
    return when {
        elapsedMinutes < 1L -> "just now"
        elapsedMinutes < 60L -> "${elapsedMinutes}m ago"
        elapsedMinutes < 1_440L -> "${elapsedMinutes / 60L}h ago"
        elapsedMinutes < 10_080L -> "${elapsedMinutes / 1_440L}d ago"
        else -> formatSavedDate(milliseconds)
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
