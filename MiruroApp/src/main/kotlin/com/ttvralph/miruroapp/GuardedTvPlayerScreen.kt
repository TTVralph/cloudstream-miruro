package com.ttvralph.miruroapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val rootView = LocalView.current.rootView
    DisposableEffect(context, rootView) {
        val wasKeepingScreenOn = rootView.keepScreenOn
        val window = context.findActivity()?.window
        val keepScreenOnFlag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val windowAlreadyKeptOn =
            window?.attributes?.flags?.and(keepScreenOnFlag)?.let { it != 0 } == true
        rootView.keepScreenOn = true
        window?.addFlags(keepScreenOnFlag)
        onDispose {
            rootView.keepScreenOn = wasKeepingScreenOn
            if (!windowAlreadyKeptOn) window?.clearFlags(keepScreenOnFlag)
        }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
