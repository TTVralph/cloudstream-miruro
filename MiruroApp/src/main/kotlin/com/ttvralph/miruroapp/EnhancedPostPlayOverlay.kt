package com.ttvralph.miruroapp

import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import kotlinx.coroutines.delay

@Composable
fun EnhancedPostPlayOverlay(
    viewModel: MiruroViewModel,
    episode: AnimeEpisode,
    nextEpisode: AnimeEpisode?,
    onBack: () -> Unit,
    onPlayNext: (AnimeEpisode) -> Unit
) {
    val rootView = LocalView.current.rootView
    val settings by viewModel.settings.collectAsState()
    var player by remember(episode) { mutableStateOf<Player?>(null) }
    var ended by remember(episode) { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(episode, rootView) {
        while (true) {
            val active = findPostPlayPlayerView(rootView)?.player
            if (active != null) {
                player = active
                ended = active.playbackState == Player.STATE_ENDED
            }
            delay(350L)
        }
    }

    LaunchedEffect(ended) {
        if (ended) {
            delay(100L)
            runCatching { firstFocus.requestFocus() }
        }
    }

    if (!ended) return
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.97f))
    ) {
        nextEpisode?.thumbnailUrl?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = nextEpisode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.Black, Color.Black.copy(alpha = 0.96f), Color.Black.copy(alpha = 0.70f), Color.Black.copy(alpha = 0.28f))
                    )
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent, Color.Black))
                )
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 76.dp)
                .width(650.dp)
                .background(Color.Black.copy(alpha = 0.84f), RoundedCornerShape(18.dp))
                .padding(28.dp)
        ) {
            Text(
                "EPISODE COMPLETE",
                color = MiruroColors.AccentSoft,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                episode.title ?: "Episode ${episode.episodeNumber}",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(22.dp))

            if (nextEpisode != null) {
                Text("Up Next", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(
                    "S${nextEpisode.seasonNumber} E${nextEpisode.episodeNumber} • ${nextEpisode.audioType.name}",
                    color = MiruroColors.AccentSoft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    nextEpisode.title ?: "Episode ${nextEpisode.episodeNumber}",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (settings.autoPlayNext) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Autoplay is enabled. Press Back to stop and return to episodes.",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    "Play next episode",
                    Modifier.width(250.dp).focusRequester(firstFocus)
                ) { onPlayNext(nextEpisode) }
            } else {
                Text("Season caught up", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(
                    "There is no later playable episode available yet.",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(18.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    "Replay",
                    Modifier.width(150.dp).then(
                        if (nextEpisode == null) Modifier.focusRequester(firstFocus) else Modifier
                    )
                ) {
                    player?.seekTo(0L)
                    player?.play()
                    ended = false
                }
                SecondaryButton("Mark unwatched", Modifier.width(190.dp)) {
                    viewModel.setEpisodeWatched(episode, false)
                }
                SecondaryButton("Back to episodes", Modifier.width(190.dp), onBack)
            }
        }
    }
}

private fun findPostPlayPlayerView(view: View): PlayerView? {
    if (view is PlayerView && view.player != null) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findPostPlayPlayerView(view.getChildAt(index))?.let { return it }
        }
    }
    return null
}
