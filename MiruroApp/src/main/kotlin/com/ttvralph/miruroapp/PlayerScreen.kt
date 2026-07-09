package com.ttvralph.miruroapp

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import com.ttvralph.miruroapp.ui.SecondaryButton
import java.util.Locale

private const val TAG = "PlayerScreen"

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
        is UiState.Success -> VideoPlayer(s.data, onBack)
    }
}

@Composable
private fun VideoPlayer(source: PlaybackSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val sources = remember(source) { listOf(source.copy(fallbackSources = emptyList())) + source.fallbackSources }
    var sourceIndex by remember(source) { mutableIntStateOf(0) }
    var playerError by remember(source) { mutableStateOf<String?>(null) }
    val activeSource = sources[sourceIndex]

    val player = remember(activeSource) {
        Log.d(TAG, "preparing player label=${activeSource.label} type=${activeSource.type}")
        val userAgent = activeSource.headers["User-Agent"]
        val requestHeaders = activeSource.headers - "User-Agent"
        val factory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(requestHeaders)
            .apply { if (userAgent != null) setUserAgent(userAgent) }
        val mediaSource = when (activeSource.type) {
            PlaybackType.HLS -> HlsMediaSource.Factory(factory).createMediaSource(buildMediaItem(activeSource, MimeTypes.APPLICATION_M3U8))
            PlaybackType.DASH -> DashMediaSource.Factory(factory).createMediaSource(buildMediaItem(activeSource, MimeTypes.APPLICATION_MPD))
            else -> ProgressiveMediaSource.Factory(factory).createMediaSource(buildMediaItem(activeSource, null))
        }
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "playback error for ${activeSource.label}", error)
                    if (sourceIndex < sources.lastIndex) {
                        sourceIndex += 1
                    } else {
                        playerError = "${error.errorCodeName}: ${error.message ?: "Playback failed"}"
                    }
                }
            })
            setMediaSource(mediaSource)
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val error = playerError
    if (error != null) {
        ErrorState(error, onBack)
    } else {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        controllerShowTimeoutMs = 0
                        controllerHideOnTouch = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        isFocusable = true
                        requestFocus()
                    }
                },
                update = { it.player = player }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0x99000000))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton("Back", modifier = Modifier.width(120.dp), onClick = onBack)
                Spacer(Modifier.width(12.dp))
                SecondaryButton("Prev source", modifier = Modifier.width(170.dp), onClick = {
                    sourceIndex = if (sourceIndex == 0) sources.lastIndex else sourceIndex - 1
                })
                Spacer(Modifier.width(12.dp))
                SecondaryButton("${activeSource.label} (${sourceIndex + 1}/${sources.size})", modifier = Modifier.width(280.dp), onClick = {
                    sourceIndex = if (sourceIndex == sources.lastIndex) 0 else sourceIndex + 1
                })
                Spacer(Modifier.width(12.dp))
                SecondaryButton("Next source", modifier = Modifier.width(170.dp), onClick = {
                    sourceIndex = if (sourceIndex == sources.lastIndex) 0 else sourceIndex + 1
                })
            }
        }
    }
}

private fun buildMediaItem(source: PlaybackSource, mime: String?): MediaItem =
    MediaItem.Builder()
        .setUri(source.url)
        .setMimeType(mime)
        .setSubtitleConfigurations(
            source.subtitleTracks.mapNotNull {
                if (it.url.isBlank()) return@mapNotNull null
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                    .setMimeType(subtitleMimeType(it.url))
                    .setLanguage(it.language)
                    .setLabel(it.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
        )
        .build()

private fun subtitleMimeType(url: String): String {
    val path = url.substringBefore('?').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}
