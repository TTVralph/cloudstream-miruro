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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.DEFAULT_PROFILE_ID
import com.ttvralph.miruroapp.data.LocalProfile
import com.ttvralph.miruroapp.data.TitleReaction
import com.ttvralph.miruroapp.data.TrackingStatus
import com.ttvralph.miruroapp.data.UpcomingEpisode
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.LandscapeCard
import com.ttvralph.miruroapp.ui.MinimalActionButton
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.SectionTitle
import com.ttvralph.miruroapp.ui.StateMessage
import com.ttvralph.miruroapp.ui.YumeBrand
import java.util.Locale

private enum class MyAniStreamTab(val label: String) {
    OVERVIEW("Overview"),
    TRACKING("Tracking"),
    LIBRARY("Library & history"),
    PROFILES("Profiles")
}

@Composable
fun MyAniStreamScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit,
    onOpenProfilePicker: () -> Unit,
    onExitApp: () -> Unit,
    openProfiles: Boolean = false
) {
    var tab by remember(openProfiles) {
        mutableStateOf(if (openProfiles) MyAniStreamTab.PROFILES else MyAniStreamTab.OVERVIEW)
    }
    var profileEditorOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (!profileEditorOpen) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    YumeBrand.LibraryLabel,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                MyAniStreamTab.entries.forEach { option ->
                    val selected = option == tab
                    val width = if (option == MyAniStreamTab.LIBRARY) 170.dp else 132.dp
                    MinimalActionButton(
                        text = option.label,
                        modifier = Modifier.width(width),
                        selected = selected,
                        onClick = { tab = option }
                    )
                }
            }
        }

        when (tab) {
            MyAniStreamTab.OVERVIEW -> MyAniStreamOverview(viewModel, features, onOpenDetails, onPlayProgress)
            MyAniStreamTab.TRACKING -> MyAniStreamTracking(viewModel, features, onOpenDetails)
            MyAniStreamTab.LIBRARY -> WatchManagementScreen(viewModel, onOpenDetails, onPlayProgress)
            MyAniStreamTab.PROFILES -> MyAniStreamProfiles(
                features = features,
                onEditorOpenChanged = { profileEditorOpen = it },
                onOpenProfilePicker = onOpenProfilePicker,
                onExitApp = onExitApp
            )
        }
    }
}

@Composable
private fun MyAniStreamOverview(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit
) {
    val progress by viewModel.watchProgress.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val reactions by features.reactions.collectAsState()
    val reminders by features.reminders.collectAsState()
    val trackingStatuses by features.trackingStatuses.collectAsState()
    val featureMetadata by features.metadata.collectAsState()
    val upcoming by features.upcoming.collectAsState()
    val unfinished = remember(progress) { progress.filterNot { it.watched }.sortedByDescending { it.updatedAtMs } }
    val completed = remember(progress) { progress.filter { it.watched }.sortedByDescending { it.updatedAtMs } }
    val metadataIds = remember(reactions, reminders, trackingStatuses, progress) {
        reactions.keys + reminders + trackingStatuses.keys + progress.map { it.animeId }
    }

    LaunchedEffect(metadataIds) { features.loadMetadata(metadataIds.toSet()) }
    LaunchedEffect(favorites, progress, reactions, reminders, trackingStatuses) {
        features.loadHomeFeatures(emptyList(), progress, favorites)
    }

    fun itemFor(id: Int): AnimeItem = featureMetadata[id]
        ?: viewModel.cachedItem(id)
        ?: AnimeItem(id, "Saved anime", null, null, AnimeType.UNKNOWN)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HubStat("${unfinished.size}", "Continue Watching")
                HubStat("${favorites.size}", "My List")
                HubStat("${trackingStatuses.size}", "Tracked titles")
                HubStat("${reactions.count { it.value != TitleReaction.DISLIKE }}", "Liked")
                HubStat("${reminders.size}", "Reminders")
                HubStat("${completed.size}", "Completed episodes")
            }
        }

        item { SectionTitle("Continue Watching", "${unfinished.size} RESUME") }
        if (unfinished.isEmpty()) {
            item { StateMessage("Start an episode and it will appear here for this profile.") }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(unfinished.take(12), key = { it.key }) { saved ->
                        val anime = itemFor(saved.animeId)
                        Column(Modifier.width(310.dp)) {
                            LandscapeCard(
                                item = anime,
                                width = 310.dp,
                                height = 170.dp,
                                progressPercent = saved.percent,
                                onClick = { onPlayProgress(saved) }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "S${saved.seasonNumber} E${saved.episodeNumber} • ${(saved.percent * 100).toInt()}%",
                                color = MiruroColors.AccentSoft,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton("Resume", Modifier.width(145.dp)) { onPlayProgress(saved) }
                                SecondaryButton("Restart", Modifier.width(145.dp)) {
                                    features.removeProgress(saved)
                                    onPlayProgress(saved.copy(positionMs = 0L, updatedAtMs = System.currentTimeMillis()))
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton("Remove", Modifier.width(145.dp)) { features.removeProgress(saved) }
                                SecondaryButton("Mark watched", Modifier.width(145.dp)) {
                                    viewModel.setEpisodeWatched(saved.asEpisodeStub(), true)
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            SecondaryButton("Restart title", Modifier.fillMaxWidth()) {
                                features.removeTitleProgress(setOf(saved.animeId))
                                onPlayProgress(saved.copy(positionMs = 0L, updatedAtMs = System.currentTimeMillis()))
                            }
                        }
                    }
                }
            }
        }

        item { ReactionRow("Loved", TitleReaction.LOVE, reactions, ::itemFor, onOpenDetails) }
        item { ReactionRow("Liked", TitleReaction.LIKE, reactions, ::itemFor, onOpenDetails) }
        item { ReactionRow("Not for me", TitleReaction.DISLIKE, reactions, ::itemFor, onOpenDetails) }

        item { SectionTitle("Upcoming & reminders", "${reminders.size} SAVED") }
        if (upcoming.isEmpty()) {
            item { StateMessage("Add reminders from a title page. Upcoming episodes will appear here when AniList has an air date.") }
        } else {
            item { UpcomingRow(upcoming, reminders, features, onOpenDetails) }
        }

        item { SectionTitle("Recently completed", "${completed.size} WATCHED") }
        if (completed.isEmpty()) {
            item { StateMessage("Completed episodes will stay in this profile's history.") }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(completed.take(15), key = { it.key }) { saved ->
                        val anime = itemFor(saved.animeId)
                        Column(Modifier.width(225.dp)) {
                            PosterCard(anime, width = 225.dp) { onOpenDetails(saved.animeId) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "S${saved.seasonNumber} E${saved.episodeNumber} • Watched",
                                color = MiruroColors.AccentSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                            SecondaryButton("Mark unwatched", Modifier.fillMaxWidth()) {
                                val duration = saved.durationMs.coerceAtLeast(10_000L)
                                viewModel.saveProgress(saved.asEpisodeStub(), 1_000L, duration)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyAniStreamTracking(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    onOpenDetails: (Int) -> Unit
) {
    val statuses by features.trackingStatuses.collectAsState()
    val featureMetadata by features.metadata.collectAsState()
    LaunchedEffect(statuses.keys) { features.loadMetadata(statuses.keys) }

    fun itemFor(id: Int): AnimeItem = featureMetadata[id]
        ?: viewModel.cachedItem(id)
        ?: AnimeItem(id, "Tracked anime", null, null, AnimeType.UNKNOWN)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Track what you are watching, planning, pausing, dropping, completing, or rewatching.",
                color = MiruroColors.Subtle,
                fontSize = 14.sp
            )
        }
        TrackingStatus.entries.forEach { status ->
            val ids = statuses.filterValues { it == status }.keys.toList()
            item {
                SectionTitle(status.label, "${ids.size} TITLES")
                if (ids.isEmpty()) {
                    StateMessage("No titles marked ${status.label.lowercase(Locale.ROOT)}.")
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(ids, key = { it }) { id ->
                            Column(Modifier.width(205.dp)) {
                                PosterCard(itemFor(id), width = 205.dp) { onOpenDetails(id) }
                                Spacer(Modifier.height(7.dp))
                                SecondaryButton("Clear status", Modifier.fillMaxWidth()) {
                                    features.setTrackingStatus(id, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubStat(value: String, label: String) {
    Column(Modifier.width(155.dp)) {
        Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(label, color = MiruroColors.Subtle, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ReactionRow(
    title: String,
    reaction: TitleReaction,
    reactions: Map<Int, TitleReaction>,
    itemFor: (Int) -> AnimeItem,
    onOpenDetails: (Int) -> Unit
) {
    val ids = reactions.filterValues { it == reaction }.keys.toList()
    SectionTitle(title, "${ids.size} TITLES")
    if (ids.isEmpty()) {
        StateMessage("Nothing marked ${title.lowercase(Locale.ROOT)} yet.")
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(ids, key = { it }) { id ->
                PosterCard(itemFor(id), width = 205.dp) { onOpenDetails(id) }
            }
        }
    }
}

@Composable
private fun UpcomingRow(
    upcoming: List<UpcomingEpisode>,
    reminders: Set<Int>,
    features: NetflixFeatureViewModel,
    onOpenDetails: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        items(upcoming, key = { it.anime.id }) { item ->
            Column(Modifier.width(285.dp)) {
                LandscapeCard(item.anime, 285.dp, 160.dp, onClick = { onOpenDetails(item.anime.id) })
                Spacer(Modifier.height(8.dp))
                Text(
                    "Episode ${item.episodeNumber} • ${formatAiring(item)}",
                    color = MiruroColors.AccentSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                SecondaryButton(
                    if (item.anime.id in reminders) "✓ Reminder set" else "Remind me",
                    Modifier.fillMaxWidth()
                ) { features.toggleReminder(item.anime.id) }
            }
        }
    }
}

@Composable
private fun MyAniStreamProfiles(
    features: NetflixFeatureViewModel,
    onEditorOpenChanged: (Boolean) -> Unit,
    onOpenProfilePicker: () -> Unit,
    onExitApp: () -> Unit
) {
    val state by features.profileState.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LocalProfile?>(null) }
    val editingProfile = editing
    val editorOpen = creating || editingProfile != null
    LaunchedEffect(editorOpen) { onEditorOpenChanged(editorOpen) }
    if (editorOpen) {
        ProfileEditorOverlay(
            profile = editingProfile,
            suggestedName = "Profile ${state.profiles.size + 1}",
            onCancel = {
                creating = false
                editing = null
            },
            onSave = { name, avatarId, themeColorId ->
                if (editingProfile == null) {
                    features.createProfile(name, avatarId, themeColorId)
                } else {
                    features.updateProfile(editingProfile, name, avatarId, themeColorId)
                }
                creating = false
                editing = null
            }
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            SectionTitle("Local profiles", "${state.profiles.size} PROFILES")
            Text(
                "Each profile has separate progress, My List, playback preferences, reactions, reminders, tracking, and recommendations on this TV.",
                color = MiruroColors.Subtle,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Choose profile", Modifier.width(190.dp), onOpenProfilePicker)
                SecondaryButton("Add profile", Modifier.width(170.dp)) { creating = true }
                SecondaryButton("Exit Yume", Modifier.width(150.dp), onExitApp)
            }
        }
        items(state.profiles, key = { it.id }) { profile ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileAvatarArtwork(
                    name = profile.name,
                    avatarId = profile.avatarId,
                    modifier = Modifier.size(58.dp)
                )
                Text(
                    if (profile.id == state.activeId) "${profile.name} • Active" else profile.name,
                    color = if (profile.id == state.activeId) MiruroColors.AccentSoft else Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(top = 14.dp)
                )
                SecondaryButton("Switch", Modifier.width(130.dp)) { features.switchProfile(profile) }
                SecondaryButton("Edit", Modifier.width(130.dp)) { editing = profile }
                if (profile.id != DEFAULT_PROFILE_ID) {
                    SecondaryButton("Delete", Modifier.width(130.dp)) { features.deleteProfile(profile) }
                }
            }
        }
    }
}

private fun WatchProgress.asEpisodeStub(): AnimeEpisode = AnimeEpisode(
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = null,
    thumbnailUrl = null,
    runtimeMinutes = null,
    releaseDate = null,
    audioType = audioType,
    anilistId = animeId
)

private fun formatAiring(item: UpcomingEpisode): String {
    val seconds = item.airingAtEpochSeconds - System.currentTimeMillis() / 1_000L
    if (seconds <= 0L) return "airing now"
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    return when {
        days > 0L -> "in ${days}d ${hours}h"
        hours > 0L -> "in ${hours}h"
        else -> "in ${seconds / 60L}m"
    }
}
