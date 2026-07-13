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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import kotlinx.coroutines.delay

@Composable
fun EnhancedPostPlayOverlay(
    episode: AnimeEpisode,
    nextEpisode: AnimeEpisode?,
    autoplayEnabled: Boolean,
    autoplayCountdown: Int,
    onCancelAutoplay: () -> Unit,
    onPlayNext: () -> Unit,
    onReplay: () -> Unit,
    onMarkUnwatched: () -> Unit,
    onBack: () -> Unit
) {
    val firstFocus = remember(nextEpisode) { FocusRequester() }

    // Cancelling autoplay removes the focused Cancel button from composition.
    // Re-home focus on the primary action after that state change so TV remotes
    // can continue into the lower action row instead of becoming focusless.
    LaunchedEffect(nextEpisode, autoplayCountdown <= 0) {
        delay(120L)
        runCatching { firstFocus.requestFocus() }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
    ) {
        nextEpisode?.thumbnailUrl?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = nextEpisode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black,
                        Color.Black.copy(alpha = 0.97f),
                        Color.Black.copy(alpha = 0.76f),
                        Color.Black.copy(alpha = 0.20f)
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.18f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.86f)
                    )
                )
            )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 72.dp, end = 48.dp)
                .width(650.dp)
        ) {
            Text(
                "EPISODE COMPLETE",
                color = MiruroColors.AccentSoft,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "S${episode.seasonNumber} E${episode.episodeNumber} watched",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                episode.title ?: "Episode ${episode.episodeNumber}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(28.dp))

            if (nextEpisode != null) {
                Text(
                    "UP NEXT",
                    color = MiruroColors.AccentSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    nextEpisode.title ?: "Episode ${nextEpisode.episodeNumber}",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Season ${nextEpisode.seasonNumber}  |  Episode ${nextEpisode.episodeNumber}  |  ${nextEpisode.audioType.name}",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                if (autoplayEnabled) {
                    Spacer(Modifier.height(15.dp))
                    Text(
                        if (autoplayCountdown > 0) {
                            "Playing automatically in ${autoplayCountdown}s"
                        } else {
                            "Autoplay cancelled for this episode"
                        },
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (autoplayCountdown > 0) {
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(
                            progress = { autoplayCountdown.coerceIn(0, POST_PLAY_COUNTDOWN_SECONDS) / POST_PLAY_COUNTDOWN_SECONDS.toFloat() },
                            modifier = Modifier.width(300.dp).height(4.dp),
                            color = MiruroColors.Accent,
                            trackColor = Color.White.copy(alpha = 0.18f)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        if (autoplayCountdown > 0) "Play now" else "Play next episode",
                        Modifier.width(220.dp).focusRequester(firstFocus),
                        onPlayNext
                    )
                    if (autoplayCountdown > 0) {
                        SecondaryButton(
                            "Cancel autoplay",
                            Modifier.width(180.dp),
                            onCancelAutoplay
                        )
                    }
                }
            } else {
                Text(
                    "YOU'RE ALL CAUGHT UP",
                    color = MiruroColors.AccentSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No later playable episode is available yet.",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(18.dp))
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    "Replay",
                    Modifier.width(120.dp).then(
                        if (nextEpisode == null) Modifier.focusRequester(firstFocus) else Modifier
                    ),
                    onReplay
                )
                SecondaryButton("Mark unwatched", Modifier.width(170.dp), onMarkUnwatched)
                SecondaryButton("Back to episodes", Modifier.width(180.dp), onBack)
            }
        }
    }
}

private const val POST_PLAY_COUNTDOWN_SECONDS = 10
