package com.ttvralph.miruroapp

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.SecondaryButton
import java.util.Locale
import kotlinx.coroutines.delay

private const val TAG = "PlayerScreen"
private const val SEEK_INCREMENT_MS = 10_000L

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
        is UiState.Success -> VideoPlayer(s.data, episode, onBack)
    }
}

@Composable
private fun VideoPlayer(source: PlaybackSource, episode: AnimeEpisode, onBack: () -> Unit) {
    val context = LocalContext.current
    val sources = remember(source) { listOf(source.copy(fallbackSources = emptyList())) + source.fallbackSources }
    var sourceIndex by remember(source) { mutableIntStateOf(0) }
    var subtitleIndex by remember(source) { mutableIntStateOf(-1) }
    var playerError by remember(source) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(source) { mutableStateOf(true) }
    var sourceMenuVisible by remember(source) { mutableStateOf(false) }
    var subtitleMenuVisible by remember(source) { mutableStateOf(false) }
    val activeSource = sources[sourceIndex]

    val player = remember(activeSource, subtitleIndex) {
        Log.d(TAG, "preparing player label=${activeSource.label} type=${activeSource.type}")
        val userAgent = activeSource.headers["User-Agent"]
        val requestHeaders = activeSource.headers - "User-Agent"
        val factory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(requestHeaders)
            .apply { if (userAgent != null) setUserAgent(userAgent) }
        val mediaSource = when (activeSource.type) {
            PlaybackType.HLS -> HlsMediaSource.Factory(factory).createMediaSource(buildMediaItem(activeSource, MimeTypes.APPLICATION_M3U8, subtitleIndex))
            PlaybackType.DASH -> DashMediaSource.Factory(factory).createMediaSource(buildMediaItem(activeSource, MimeTypes.APPLICATION_MPD, subtitleIndex))
            else -> ProgressiveMediaSource.Factory(factory).createMediaSource(buildMediaItem(activeSource, null, subtitleIndex))
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
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(player, controlsVisible) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val seekBy = if (offset.x < size.width / 2f) -SEEK_INCREMENT_MS else SEEK_INCREMENT_MS
                            player.seekTo((player.currentPosition + seekBy).coerceAtLeast(0L))
                            controlsVisible = true
                        }
                    )
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        isFocusable = true
                        requestFocus()
                    }
                },
                update = { it.player = player }
            )
            if (controlsVisible) {
                CloudstreamPlayerOverlay(
                    player = player,
                    episode = episode,
                    sources = sources,
                    activeSource = activeSource,
                    sourceIndex = sourceIndex,
                    subtitleIndex = subtitleIndex,
                    sourceMenuVisible = sourceMenuVisible,
                    subtitleMenuVisible = subtitleMenuVisible,
                    onBack = onBack,
                    onSeekBack = { player.seekTo((player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)) },
                    onPlayPause = { player.playWhenReady = !player.playWhenReady },
                    onSeekForward = { player.seekTo(player.currentPosition + SEEK_INCREMENT_MS) },
                    onSourceMenu = { sourceMenuVisible = !sourceMenuVisible; subtitleMenuVisible = false },
                    onSourceSelected = { sourceIndex = it; sourceMenuVisible = false },
                    onSubtitleMenu = { subtitleMenuVisible = !subtitleMenuVisible; sourceMenuVisible = false },
                    onSubtitleSelected = { subtitleIndex = it; subtitleMenuVisible = false },
                    onHideControls = { controlsVisible = false }
                )
            }
        }
    }
}

@Composable
private fun CloudstreamPlayerOverlay(
    player: Player,
    episode: AnimeEpisode,
    sources: List<PlaybackSource>,
    activeSource: PlaybackSource,
    sourceIndex: Int,
    subtitleIndex: Int,
    sourceMenuVisible: Boolean,
    subtitleMenuVisible: Boolean,
    onBack: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSourceMenu: () -> Unit,
    onSourceSelected: (Int) -> Unit,
    onSubtitleMenu: () -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onHideControls: () -> Unit
) {
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var progress by remember(player) { mutableFloatStateOf(0f) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            progress = if (duration > 0L) position.toFloat() / duration.toFloat() else 0f
            isPlaying = player.isPlaying
            delay(500L)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0x33000000))) {
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xAA000000)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton("Back", modifier = Modifier.width(110.dp), onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("S${episode.seasonNumber} • E${episode.episodeNumber}", color = MiruroColors.Subtle, fontSize = 14.sp)
                Text(episode.title ?: "Episode ${episode.episodeNumber}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SecondaryButton("Lock", modifier = Modifier.width(110.dp), onClick = onHideControls)
            Spacer(Modifier.width(10.dp))
            SecondaryButton("Settings", modifier = Modifier.width(140.dp), onClick = onSubtitleMenu)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton("-10s", modifier = Modifier.width(110.dp), onClick = onSeekBack)
            SecondaryButton(if (isPlaying) "Pause" else "Play", modifier = Modifier.width(150.dp), onClick = onPlayPause)
            SecondaryButton("+10s", modifier = Modifier.width(110.dp), onClick = onSeekForward)
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC000000)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), color = Color.White, fontSize = 14.sp)
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        progress = newProgress
                        if (duration > 0L) player.seekTo((duration * newProgress).toLong())
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                Text(formatTime(duration), color = Color.White, fontSize = 14.sp)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Sources", modifier = Modifier.width(145.dp), onClick = onSourceMenu)
                SecondaryButton("${activeSource.label} (${sourceIndex + 1}/${sources.size})", modifier = Modifier.width(260.dp), onClick = onSourceMenu)
                SecondaryButton("Subtitles", modifier = Modifier.width(155.dp), onClick = onSubtitleMenu)
                SecondaryButton("Audio", modifier = Modifier.width(120.dp), onClick = onSubtitleMenu)
                SecondaryButton("Next episode", modifier = Modifier.width(170.dp), onClick = onHideControls)
            }
        }

        if (sourceMenuVisible) {
            PlayerMenu("Select source", Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                sources.forEachIndexed { index, item ->
                    MenuRow(
                        text = "${index + 1}. ${item.label} • ${item.type}",
                        selected = index == sourceIndex,
                        onClick = { onSourceSelected(index) }
                    )
                }
            }
        }
        if (subtitleMenuVisible) {
            PlayerMenu("Subtitles / settings", Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                MenuRow("Off", selected = subtitleIndex == -1, onClick = { onSubtitleSelected(-1) })
                activeSource.subtitleTracks.forEachIndexed { index, subtitle ->
                    MenuRow(
                        text = subtitle.label.ifBlank { subtitle.language ?: "Subtitle ${index + 1}" },
                        selected = subtitleIndex == index,
                        onClick = { onSubtitleSelected(index) }
                    )
                }
                if (activeSource.subtitleTracks.isEmpty()) Text("No external subtitle tracks", color = MiruroColors.Subtle, modifier = Modifier.padding(12.dp))
                Text("Audio track and subtitle styling controls will appear here when Media3 exposes selectable tracks.", color = MiruroColors.Subtle, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun PlayerMenu(title: String, modifier: Modifier = Modifier, content: @Composable Column.() -> Unit) {
    Column(modifier.width(360.dp).background(Color(0xEE111111)).padding(16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun MenuRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = if (selected) "✓ $text" else text,
        color = if (selected) MiruroColors.Accent else Color.White,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        fontSize = 16.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
}

private fun buildMediaItem(source: PlaybackSource, mime: String?, subtitleIndex: Int): MediaItem =
    MediaItem.Builder()
        .setUri(source.url)
        .setMimeType(mime)
        .setSubtitleConfigurations(
            source.subtitleTracks.mapIndexedNotNull { index, track ->
                if (subtitleIndex != -1 && index != subtitleIndex) return@mapIndexedNotNull null
                if (track.url.isBlank()) return@mapIndexedNotNull null
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                    .setMimeType(subtitleMimeType(track.url))
                    .setLanguage(track.language)
                    .setLabel(track.label)
                    .setSelectionFlags(if (index == subtitleIndex) C.SELECTION_FLAG_DEFAULT else 0)
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

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
