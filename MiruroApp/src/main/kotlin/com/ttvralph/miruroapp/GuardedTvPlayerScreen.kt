package com.ttvralph.miruroapp

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.ttvralph.miruroapp.data.AnimeEpisode

@Composable
fun GuardedTvPlayerScreen(
    viewModel: MiruroViewModel,
    episode: AnimeEpisode?,
    nextEpisode: AnimeEpisode?,
    onBack: () -> Unit,
    onPlayNext: (AnimeEpisode) -> Unit
) {
    TvPlayerScreen(
        viewModel = viewModel,
        episode = episode,
        nextEpisode = nextEpisode,
        onBack = onBack,
        onPlayNext = onPlayNext
    )

    // Register after the player so the TV remote Back key always has a safe route out,
    // including Media3 source-error screens where the internal controls are no longer useful.
    BackHandler(onBack = onBack)
}
