package com.ttvralph.miruroapp

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.SkipInterval
import com.ttvralph.miruroapp.ui.PrimaryButton
import kotlinx.coroutines.delay

@Composable
fun SkipPlayerOverlay(
    features: NetflixFeatureViewModel,
    episode: AnimeEpisode
) {
    val rootView = LocalView.current.rootView
    val intervalVersion by features.skipIntervals.collectAsState()
    var player by remember(episode) { mutableStateOf<Player?>(null) }
    var positionMs by remember(episode) { mutableLongStateOf(0L) }
    var durationMs by remember(episode) { mutableLongStateOf(0L) }
    var skippedInterval by remember(episode) { mutableStateOf<SkipInterval?>(null) }

    LaunchedEffect(episode, rootView) {
        while (true) {
            val active = findPlayerView(rootView)?.player
            if (active != null) {
                player = active
                positionMs = active.currentPosition.coerceAtLeast(0L)
                durationMs = active.duration.takeIf { it > 0L } ?: 0L
                if (durationMs > 0L) features.loadSkipTimes(episode, durationMs)
            }
            delay(400L)
        }
    }

    val interval = remember(intervalVersion, episode, durationMs, positionMs, skippedInterval) {
        features.intervalsFor(episode, durationMs)
            .activeAt(positionMs)
            ?.takeUnless { it == skippedInterval }
    }

    if (interval != null && player?.playbackState != Player.STATE_ENDED) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 54.dp, bottom = 118.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            PrimaryButton(
                text = interval.kind.label,
                modifier = Modifier.width(210.dp)
            ) {
                skippedInterval = interval
                player?.let { active ->
                    active.seekTo((interval.endMs + 250L).coerceAtMost(durationMs))
                    active.play()
                }
            }
        }
    }
}

private fun List<SkipInterval>.activeAt(positionMs: Long): SkipInterval? = firstOrNull { interval ->
    positionMs >= (interval.startMs - 350L).coerceAtLeast(0L) && positionMs < interval.endMs
}

private fun findPlayerView(view: View): PlayerView? {
    if (view is PlayerView && view.player != null) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findPlayerView(view.getChildAt(index))?.let { return it }
        }
    }
    return null
}
