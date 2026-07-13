package com.ttvralph.miruroapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
    val rootView = LocalView.current.rootView
    DisposableEffect(rootView) {
        val wasKeepingScreenOn = rootView.keepScreenOn
        rootView.keepScreenOn = true
        onDispose { rootView.keepScreenOn = wasKeepingScreenOn }
    }

    Box(Modifier.fillMaxSize()) {
        HotfixTvPlayerScreen(
            viewModel = viewModel,
            features = features,
            episode = episode,
            nextEpisode = nextEpisode,
            onBack = onBack,
            onPlayNext = onPlayNext
        )
    }
}
