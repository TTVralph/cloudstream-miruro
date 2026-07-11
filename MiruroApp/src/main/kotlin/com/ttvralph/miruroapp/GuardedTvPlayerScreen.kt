package com.ttvralph.miruroapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ttvralph.miruroapp.data.AnimeEpisode

@Composable
fun GuardedTvPlayerScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    episode: AnimeEpisode?,
    nextEpisode: AnimeEpisode?,
    onBack: () -> Unit,
    onPlayNext: (AnimeEpisode) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        ImprovedTvPlayerScreen(
            viewModel = viewModel,
            episode = episode,
            nextEpisode = nextEpisode,
            onBack = onBack,
            onPlayNext = onPlayNext
        )
        episode?.let { current ->
            SkipPlayerOverlay(features, current)
            EnhancedPostPlayOverlay(
                viewModel = viewModel,
                episode = current,
                nextEpisode = nextEpisode,
                onBack = onBack,
                onPlayNext = onPlayNext
            )
        }
    }

    // Register after the player so the TV remote Back key always has a safe route out,
    // including Media3 source-error screens where the internal controls are no longer useful.
    BackHandler(onBack = onBack)
}
