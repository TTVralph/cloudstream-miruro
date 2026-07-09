package com.ttvralph.miruroapp

import androidx.compose.runtime.Composable
import com.ttvralph.miruroapp.data.AudioType

@Composable
fun DetailsScreen(
    viewModel: MiruroViewModel,
    animeId: Int,
    onBack: () -> Unit,
    onOpenEpisode: (Int, Int, AudioType) -> Unit,
    onPlayEpisode: (Int, Int, AudioType) -> Unit
) {
    DetailsScreen(
        viewModel = viewModel,
        animeId = animeId,
        onOpenEpisode = onOpenEpisode,
        onPlayEpisode = onPlayEpisode
    )
}
