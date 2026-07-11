package com.ttvralph.miruroapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.TitleReaction
import com.ttvralph.miruroapp.data.TrackingStatus
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import kotlinx.coroutines.delay

internal data class HomeCardActionTarget(
    val item: AnimeItem,
    val resumeProgress: WatchProgress?,
    val titleProgress: DailyTitleProgress?
)

@Composable
internal fun HomeCardActionMenu(
    target: HomeCardActionTarget,
    inList: Boolean,
    reaction: TitleReaction?,
    trackingStatus: TrackingStatus?,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit,
    onPlay: () -> Unit,
    onToggleList: () -> Unit,
    onSetReaction: (TitleReaction) -> Unit,
    onSetTrackingStatus: (TrackingStatus?) -> Unit,
    onSetTitleWatched: (Boolean) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val firstFocus = FocusRequester()
    LaunchedEffect(target.item.id) {
        delay(90L)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.80f)),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier
                .width(760.dp)
                .background(Color(0xFF101010), RoundedCornerShape(18.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    target.item.title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        trackingStatus?.label,
                        target.titleProgress?.let { "${it.watched}/${it.total} watched" }
                    ).joinToString(" • ").ifBlank { "Quick actions" },
                    color = MiruroColors.Subtle,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        if (target.resumeProgress != null) "Resume" else "Open & play",
                        Modifier.width(220.dp).focusRequester(firstFocus),
                        onPlay
                    )
                    SecondaryButton("View details", Modifier.width(190.dp), onOpenDetails)
                    SecondaryButton(
                        if (inList) "Remove from My List" else "Add to My List",
                        Modifier.width(260.dp),
                        onToggleList
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val allWatched = target.titleProgress?.allWatched == true
                    SecondaryButton(
                        if (allWatched) "Mark title unwatched" else "Mark title watched",
                        Modifier.width(250.dp)
                    ) { onSetTitleWatched(!allWatched) }
                    SecondaryButton(
                        if (reaction == TitleReaction.LIKE) "✓ Liked" else "Like",
                        Modifier.width(150.dp)
                    ) { onSetReaction(TitleReaction.LIKE) }
                    SecondaryButton(
                        if (reaction == TitleReaction.DISLIKE) "✓ Not for me" else "Not for me",
                        Modifier.width(180.dp)
                    ) { onSetReaction(TitleReaction.DISLIKE) }
                }
            }

            item {
                Text("Tracking status", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                StatusButtonRow(
                    statuses = listOf(TrackingStatus.WATCHING, TrackingStatus.PLANNING, TrackingStatus.COMPLETED),
                    selected = trackingStatus,
                    onSelected = onSetTrackingStatus
                )
                Spacer(Modifier.height(8.dp))
                StatusButtonRow(
                    statuses = listOf(TrackingStatus.ON_HOLD, TrackingStatus.DROPPED, TrackingStatus.REWATCHING),
                    selected = trackingStatus,
                    onSelected = onSetTrackingStatus
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Clear tracking status", Modifier.fillMaxWidth()) { onSetTrackingStatus(null) }
            }

            item {
                SecondaryButton("Close", Modifier.fillMaxWidth(), onDismiss)
            }
        }
    }
}

@Composable
internal fun TrackingStatusPicker(
    title: String,
    selected: TrackingStatus?,
    onDismiss: () -> Unit,
    onSelected: (TrackingStatus?) -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(660.dp)
                .background(Color(0xFF101010), RoundedCornerShape(18.dp))
                .padding(24.dp)
        ) {
            Text(title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            StatusButtonRow(
                statuses = listOf(TrackingStatus.WATCHING, TrackingStatus.PLANNING, TrackingStatus.COMPLETED),
                selected = selected,
                onSelected = onSelected
            )
            Spacer(Modifier.height(10.dp))
            StatusButtonRow(
                statuses = listOf(TrackingStatus.ON_HOLD, TrackingStatus.DROPPED, TrackingStatus.REWATCHING),
                selected = selected,
                onSelected = onSelected
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Clear status", Modifier.weight(1f)) { onSelected(null) }
                SecondaryButton("Cancel", Modifier.weight(1f), onDismiss)
            }
        }
    }
}

@Composable
private fun StatusButtonRow(
    statuses: List<TrackingStatus>,
    selected: TrackingStatus?,
    onSelected: (TrackingStatus?) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        statuses.forEach { status ->
            SecondaryButton(
                if (selected == status) "✓ ${status.label}" else status.label,
                Modifier.weight(1f)
            ) { onSelected(status) }
        }
    }
}
