package com.ttvralph.miruroapp

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.TitleExtras
import com.ttvralph.miruroapp.data.TitleReaction
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.SectionTitle
import com.ttvralph.miruroapp.ui.StateMessage

@Composable
fun EnhancedDetailsScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    animeId: Int,
    onBack: () -> Unit,
    onOpenEpisode: (Int, Int, AudioType) -> Unit,
    onPlayEpisode: (Int, Int, AudioType) -> Unit,
    onMoreLikeThis: () -> Unit
) {
    val reactions by features.reactions.collectAsState()
    val reminders by features.reminders.collectAsState()
    val trackingStatuses by features.trackingStatuses.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val extras by features.extras.collectAsState()
    var showStatusPicker by remember(animeId) { mutableStateOf(false) }
    LaunchedEffect(animeId) { features.loadExtras(animeId) }

    Box(Modifier.fillMaxSize()) {
        DailyDetailsScreen(
            viewModel = viewModel,
            features = features,
            animeId = animeId,
            onBack = onBack,
            onOpenEpisode = onOpenEpisode,
            onPlayEpisode = onPlayEpisode
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 26.dp)
                .width(650.dp)
                .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReactionButton("Like", reactions[animeId] == TitleReaction.LIKE) {
                    features.setReaction(animeId, TitleReaction.LIKE)
                }
                ReactionButton("Love", reactions[animeId] == TitleReaction.LOVE) {
                    features.setReaction(animeId, TitleReaction.LOVE)
                }
                ReactionButton("Not for me", reactions[animeId] == TitleReaction.DISLIKE) {
                    features.setReaction(animeId, TitleReaction.DISLIKE)
                }
                ReactionButton(
                    if (animeId in reminders) "✓ Reminder" else "Remind me",
                    animeId in reminders
                ) { features.toggleReminder(animeId) }
                PrimaryButton("Anime guide", Modifier.width(160.dp), onMoreLikeThis)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(
                    "Status: ${trackingStatuses[animeId]?.label ?: "None"}",
                    Modifier.width(210.dp)
                ) { showStatusPicker = true }
                if (settings.noSpoilerMode) {
                    Text(
                        "No-spoiler mode is on",
                        color = MiruroColors.AccentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                val next = (extras as? UiState.Success<TitleExtras>)?.data?.nextAiring
                next?.let {
                    Text(
                        "Next: E${it.episodeNumber} ${formatAiringTime(it.airingAtEpochSeconds)}",
                        color = MiruroColors.AccentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showStatusPicker) {
            TrackingStatusPicker(
                title = "Track this anime",
                selected = trackingStatuses[animeId],
                onDismiss = { showStatusPicker = false },
                onSelected = { status ->
                    features.setTrackingStatus(animeId, status)
                    showStatusPicker = false
                }
            )
        }
    }
}

@Composable
private fun ReactionButton(text: String, selected: Boolean, onClick: () -> Unit) {
    SecondaryButton(if (selected) "✓ $text" else text, Modifier.width(112.dp), onClick)
}

@Composable
fun TitleExtrasScreen(
    features: NetflixFeatureViewModel,
    animeId: Int,
    onBack: () -> Unit,
    onOpenDetails: (Int) -> Unit
) {
    val state by features.extras.collectAsState()
    LaunchedEffect(animeId) { features.loadExtras(animeId) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(horizontal = 54.dp, vertical = 30.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Back", Modifier.width(120.dp), onBack)
                Spacer(Modifier.width(18.dp))
                Text("More Like This", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
        }

        when (val current = state) {
            null, is UiState.Loading -> item { LoadingState("Loading related anime…") }
            is UiState.Error -> item { ErrorState(current.message) { features.loadExtras(animeId) } }
            is UiState.Success -> {
                val value = current.data
                value.nextAiring?.let { upcoming ->
                    item {
                        Text(
                            "Episode ${upcoming.episodeNumber} ${formatAiringTime(upcoming.airingAtEpochSeconds)}",
                            color = MiruroColors.AccentSoft,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item { ExtrasRow("Recommended for you", value.recommendations, onOpenDetails) }
                item { ExtrasRow("Related anime", value.related, onOpenDetails) }
                if (value.recommendations.isEmpty() && value.related.isEmpty()) {
                    item { StateMessage("AniList did not return related titles for this anime.") }
                }
            }
        }
    }
}

@Composable
private fun ExtrasRow(
    title: String,
    items: List<com.ttvralph.miruroapp.data.AnimeItem>,
    onOpenDetails: (Int) -> Unit
) {
    if (items.isEmpty()) return
    SectionTitle(title, "${items.size} TITLES")
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(items, key = { it.id }) { anime ->
            PosterCard(anime, width = 205.dp) { onOpenDetails(anime.id) }
        }
    }
}

private fun formatAiringTime(epochSeconds: Long): String {
    val remaining = epochSeconds - System.currentTimeMillis() / 1_000L
    if (remaining <= 0L) return "is airing now"
    val days = remaining / 86_400L
    val hours = (remaining % 86_400L) / 3_600L
    return when {
        days > 0L -> "airs in ${days}d ${hours}h"
        hours > 0L -> "airs in ${hours}h"
        else -> "airs in ${remaining / 60L}m"
    }
}
