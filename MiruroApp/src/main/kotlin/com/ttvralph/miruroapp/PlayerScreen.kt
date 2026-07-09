package com.ttvralph.miruroapp

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.PlaybackType
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.LoadingState

@Composable
fun PlayerScreen(viewModel: MiruroViewModel, episode: AnimeEpisode?, onBack: () -> Unit) {
    if (episode == null) {
        ErrorState("Episode not found.", onBack)
        return
    }

    LaunchedEffect(episode) { viewModel.resolvePlayback(episode) }
    DisposableEffect(Unit) { onDispose { viewModel.clearPlayback() } }

    val state by viewModel.playback.collectAsState()
    when (val s = state) {
        null, is UiState.Loading -> LoadingState("Resolving stream…")
        is UiState.Error -> ErrorState(s.message, onBack)
        is UiState.Success -> VideoPlayer(s.data)
    }
}

@Composable
private fun VideoPlayer(source: PlaybackSource) {
    val context = LocalContext.current
    val player = remember(source) {
        val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
        val mediaSource = when (source.type) {
            PlaybackType.HLS -> HlsMediaSource.Factory(factory).createMediaSource(buildMediaItem(source, MimeTypes.APPLICATION_M3U8))
            PlaybackType.DASH -> DashMediaSource.Factory(factory).createMediaSource(buildMediaItem(source, MimeTypes.APPLICATION_MPD))
            else -> ProgressiveMediaSource.Factory(factory).createMediaSource(buildMediaItem(source, null))
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> PlayerView(ctx).apply { useController = true; isFocusable = true } },
        update = { it.player = player }
    )
}

private fun buildMediaItem(source: PlaybackSource, mime: String?): MediaItem =
    MediaItem.Builder()
        .setUri(source.url)
        .setMimeType(mime)
        .setSubtitleConfigurations(
            source.subtitleTracks.map {
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(it.language)
                    .setLabel(it.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
        )
        .build()
