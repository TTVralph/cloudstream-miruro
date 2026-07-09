package com.ttvralph.miruroapp

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.PlaybackType
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import java.util.Locale
import kotlinx.coroutines.delay

private const val TAG = "PlayerScreen"
private const val SEEK_INCREMENT_MS = 10_000L

@Composable
fun PlayerScreen(viewModel: MiruroViewModel, episode: AnimeEpisode?, onBack: () -> Unit, onNextEpisode: (() -> Unit)? = null) {
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
        is UiState.Success -> VideoPlayer(s.data, episode, viewModel, onBack, onNextEpisode)
    }
}

@Composable
private fun VideoPlayer(source: PlaybackSource, episode: AnimeEpisode, viewModel: MiruroViewModel, onBack: () -> Unit, onNextEpisode: (() -> Unit)?) {
    val context = LocalContext.current
    val sources = remember(source) { listOf(source.copy(fallbackSources = emptyList())) + source.fallbackSources }
    var sourceIndex by remember(source) { mutableIntStateOf(0) }
    var playerError by remember(source) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(source) { mutableStateOf(true) }
    var sourceMenuVisible by remember(source) { mutableStateOf(false) }
    var subtitleMenuVisible by remember(source) { mutableStateOf(false) }
    var speedMenuVisible by remember(source) { mutableStateOf(false) }
    val activeSource = sources[sourceIndex]
    val settings = viewModel.settings.collectAsState().value
    val autoSubtitleIndex = remember(activeSource, settings.subtitleLanguage) {
        preferredSubtitleIndex(activeSource.subtitleTracks, settings.subtitleLanguage)
    }
    val preferredSubtitleIndex = remember(activeSource, settings.subtitleChoice, autoSubtitleIndex) {
        subtitleIndexForChoice(activeSource.subtitleTracks, settings.subtitleChoice, autoSubtitleIndex)
    }
    var subtitleIndex by remember(activeSource, preferredSubtitleIndex) { mutableIntStateOf(preferredSubtitleIndex) }
    val savedProgress = viewModel.watchProgress.collectAsState().value.firstOrNull {
        it.animeId == episode.anilistId && it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber && it.audioType == episode.audioType
    }
    var locked by remember(source) { mutableStateOf(false) }
    var gestureMessage by remember(source) { mutableStateOf<String?>(null) }
    var nextCountdown by remember(source) { mutableIntStateOf(0) }
    var lastBackPressMs by remember(source) { mutableLongStateOf(0L) }
    val playerFocusRequester = remember { FocusRequester() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    BackHandler {
        if (!controlsVisible) {
            controlsVisible = true
            gestureMessage = "Controls shown"
        } else if (sourceMenuVisible || subtitleMenuVisible || speedMenuVisible) {
            sourceMenuVisible = false
            subtitleMenuVisible = false
            speedMenuVisible = false
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressMs < 2_000L) {
                onBack()
            } else {
                lastBackPressMs = now
                gestureMessage = "Press Back again to exit video"
            }
        }
    }

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
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && settings.autoPlayNext && onNextEpisode != null) nextCountdown = 5
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "playback error for ${activeSource.label}", error)
                    if (sourceIndex < sources.lastIndex) {
                        gestureMessage = "Source failed, trying next source…"
                        controlsVisible = true
                        sourceIndex += 1
                    } else {
                        playerError = "${error.errorCodeName}: ${error.message ?: "Playback failed"}"
                    }
                }
            })
            setMediaSource(mediaSource)
            playWhenReady = true
            prepare()
            if (settings.resumePlayback) savedProgress?.takeIf { !it.watched && it.positionMs > 10_000L }?.let { seekTo(it.positionMs) }
        }
    }
    DisposableEffect(player) {
        onDispose {
            val duration = player.duration.takeIf { it > 0 } ?: 0L
            viewModel.saveProgress(episode, player.currentPosition, duration)
            player.release()
        }
    }

    LaunchedEffect(source) { playerFocusRequester.requestFocus() }
    LaunchedEffect(gestureMessage) { if (gestureMessage != null) { delay(1_200L); gestureMessage = null } }
    LaunchedEffect(nextCountdown) {
        if (nextCountdown > 0) {
            delay(1_000L)
            if (nextCountdown == 1) onNextEpisode?.invoke() else nextCountdown -= 1
        }
    }

    val error = playerError
    if (error != null) {
        Column(Modifier.fillMaxSize().background(Color.Black), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            ErrorState(error, onBack)
            PrimaryButton("Try alternate source", modifier = Modifier.width(240.dp), onClick = { sourceIndex = ((sourceIndex + 1).coerceAtMost(sources.lastIndex)); playerError = null })
        }
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(playerFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.MediaPlayPause -> {
                            controlsVisible = true
                            if (!locked) player.playWhenReady = !player.playWhenReady
                            true
                        }
                        Key.MediaPlay -> {
                            controlsVisible = true
                            if (!locked) player.play()
                            true
                        }
                        Key.MediaPause -> {
                            controlsVisible = true
                            if (!locked) player.pause()
                            true
                        }
                        Key.MediaRewind -> {
                            controlsVisible = true
                            if (!locked) {
                                player.seekTo((player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L))
                                gestureMessage = "Rewind 10s"
                            }
                            true
                        }
                        Key.MediaFastForward -> {
                            controlsVisible = true
                            if (!locked) {
                                player.seekTo(player.currentPosition + SEEK_INCREMENT_MS)
                                gestureMessage = "Forward 10s"
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (controlsVisible) false else {
                                controlsVisible = true
                                if (!locked) player.playWhenReady = !player.playWhenReady
                                true
                            }
                        }
                        Key.DirectionLeft -> {
                            if (controlsVisible) false else {
                                controlsVisible = true
                                if (!locked) {
                                    player.seekTo((player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L))
                                    gestureMessage = "Rewind 10s"
                                }
                                true
                            }
                        }
                        Key.DirectionRight -> {
                            if (controlsVisible) false else {
                                controlsVisible = true
                                if (!locked) {
                                    player.seekTo(player.currentPosition + SEEK_INCREMENT_MS)
                                    gestureMessage = "Forward 10s"
                                }
                                true
                            }
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (controlsVisible) false else {
                                controlsVisible = true
                                true
                            }
                        }
                        else -> false
                    }
                }
                .pointerInput(player, controlsVisible, locked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (!locked) {
                                val seekBy = if (offset.x < size.width / 2f) -SEEK_INCREMENT_MS else SEEK_INCREMENT_MS
                                player.seekTo((player.currentPosition + seekBy).coerceAtLeast(0L))
                                gestureMessage = if (seekBy < 0) "Rewind 10s" else "Forward 10s"
                            }
                            controlsVisible = true
                        }
                    )
                }
                .pointerInput(player, locked) {
                    detectDragGestures { change, dragAmount ->
                        if (!locked && kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x)) {
                            val delta = -dragAmount.y / size.height
                            if (change.position.x < size.width / 2f) {
                                val activity = context as? Activity
                                val current = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                                val next = (current + delta).coerceIn(0.05f, 1f)
                                activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = next }
                                gestureMessage = "Brightness ${(next * 100).toInt()}%"
                            } else {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val next = (current + (delta * max).toInt()).coerceIn(0, max)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                gestureMessage = "Volume ${next * 100 / max}%"
                            }
                            controlsVisible = true
                        }
                    }
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
                update = { view ->
                    view.player = player
                    view.subtitleView?.setStyle(captionStyle(settings.subtitleStyle))
                    view.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (settings.subtitleStyle == "Large") 24f else 18f)
                }
            )
            LaunchedEffect(controlsVisible, player.isPlaying) { if (controlsVisible && player.isPlaying) { delay(4_000L); controlsVisible = false } }
            if (controlsVisible) {
                CloudstreamPlayerOverlay(
                    player = player,
                    episode = episode,
                    sources = sources,
                    activeSource = activeSource,
                    sourceIndex = sourceIndex,
                    subtitleIndex = subtitleIndex,
                    autoSubtitleIndex = autoSubtitleIndex,
                    sourceMenuVisible = sourceMenuVisible,
                    subtitleMenuVisible = subtitleMenuVisible,
                    speedMenuVisible = speedMenuVisible,
                    locked = locked,
                    gestureMessage = gestureMessage,
                    subtitleStyle = settings.subtitleStyle,
                    nextCountdown = nextCountdown,
                    onBack = onBack,
                    onSeekBack = { player.seekTo((player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)) },
                    onPlayPause = { player.playWhenReady = !player.playWhenReady },
                    onSeekForward = { player.seekTo(player.currentPosition + SEEK_INCREMENT_MS) },
                    onSourceMenu = { sourceMenuVisible = !sourceMenuVisible; subtitleMenuVisible = false },
                    onSourceSelected = { sourceIndex = it; sourceMenuVisible = false },
                    onSubtitleMenu = { subtitleMenuVisible = !subtitleMenuVisible; sourceMenuVisible = false; speedMenuVisible = false },
                    onSubtitleSelected = { index, choice -> subtitleIndex = index; viewModel.updateSubtitleChoice(choice); subtitleMenuVisible = false },
                    onSpeedMenu = { speedMenuVisible = !speedMenuVisible; sourceMenuVisible = false; subtitleMenuVisible = false },
                    onSpeedSelected = { player.setPlaybackSpeed(it); speedMenuVisible = false },
                    onLockToggle = { locked = !locked; controlsVisible = true },
                    onNextEpisode = onNextEpisode?.let { next -> { nextCountdown = 0; next() } },
                    onHideControls = { controlsVisible = false },
                    onProgressTick = { position, duration -> viewModel.saveProgress(episode, position, duration) }
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
    autoSubtitleIndex: Int,
    sourceMenuVisible: Boolean,
    subtitleMenuVisible: Boolean,
    speedMenuVisible: Boolean,
    locked: Boolean,
    gestureMessage: String?,
    subtitleStyle: String,
    nextCountdown: Int,
    onBack: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSourceMenu: () -> Unit,
    onSourceSelected: (Int) -> Unit,
    onSubtitleMenu: () -> Unit,
    onSubtitleSelected: (Int, String) -> Unit,
    onSpeedMenu: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onLockToggle: () -> Unit,
    onNextEpisode: (() -> Unit)?,
    onHideControls: () -> Unit,
    onProgressTick: (Long, Long) -> Unit
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
            onProgressTick(position, duration)
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
            SecondaryButton(if (locked) "Unlock" else "Lock", modifier = Modifier.width(110.dp), onClick = onLockToggle)
            Spacer(Modifier.width(10.dp))
            SecondaryButton("Settings", modifier = Modifier.width(140.dp), onClick = onSubtitleMenu)
        }

        if (nextCountdown > 0) Text("Next episode in ${nextCountdown}s", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).background(Color(0x99000000)).padding(16.dp))
        gestureMessage?.let { Text(it, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).background(Color(0x99000000)).padding(16.dp)) }

        if (!locked) Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton("-10s", modifier = Modifier.width(110.dp), onClick = onSeekBack)
            SecondaryButton(if (isPlaying) "Pause" else "Play", modifier = Modifier.width(150.dp), onClick = onPlayPause)
            SecondaryButton("+10s", modifier = Modifier.width(110.dp), onClick = onSeekForward)
        }

        if (!locked) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC000000)).padding(16.dp)) {
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
                SecondaryButton("Speed", modifier = Modifier.width(120.dp), onClick = onSpeedMenu)
                SecondaryButton(if (onNextEpisode != null) "Next episode" else "No next episode", modifier = Modifier.width(190.dp), onClick = { onNextEpisode?.invoke() ?: onHideControls() })
            }
        }

        if (sourceMenuVisible) {
            PlayerMenu("Select source", Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                sources.forEachIndexed { index, item ->
                    MenuRow(
                        text = "${index + 1}. ${sourceDisplayLabel(item)}",
                        selected = index == sourceIndex,
                        onClick = { onSourceSelected(index) }
                    )
                }
            }
        }
        if (speedMenuVisible) {
            PlayerMenu("Playback speed", Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed -> MenuRow("${speed}x", selected = false, onClick = { onSpeedSelected(speed) }) }
            }
        }
        if (subtitleMenuVisible) {
            PlayerMenu("Subtitles / settings", Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                MenuRow("Off", selected = subtitleIndex == -1, onClick = { onSubtitleSelected(-1, "Off") })
                MenuRow("Auto (${activeSource.subtitleTracks.getOrNull(autoSubtitleIndex)?.label ?: "preferred language"})", selected = false, onClick = { onSubtitleSelected(autoSubtitleIndex, "Auto") })
                activeSource.subtitleTracks.forEachIndexed { index, subtitle ->
                    MenuRow(
                        text = subtitle.label.ifBlank { subtitle.language ?: "Subtitle ${index + 1}" },
                        selected = subtitleIndex == index,
                        onClick = { onSubtitleSelected(index, subtitle.language ?: subtitle.label) }
                    )
                }
                if (activeSource.subtitleTracks.isEmpty()) Text("No external subtitle tracks", color = MiruroColors.Subtle, modifier = Modifier.padding(12.dp))
                Text("Subtitle style: $subtitleStyle. External subtitles auto-select from your preferred language when available.", color = MiruroColors.Subtle, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun PlayerMenu(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.width(360.dp).background(Color(0xEE111111)).padding(16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun MenuRow(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
        focusedBackground = Color.White
    ) { focused ->
        Text(
            text = if (selected) "✓ $text" else text,
            color = when {
                focused -> Color.Black
                selected -> MiruroColors.Accent
                else -> Color.White
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            fontSize = 16.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun sourceDisplayLabel(source: PlaybackSource): String {
    val quality = Regex("""(?i)(2160p|1440p|1080p|720p|480p|360p|4k)""").find(source.label)?.value?.uppercase(Locale.ROOT) ?: source.type.name
    val subtitles = if (source.subtitleTracks.isNotEmpty()) " • ${source.subtitleTracks.size} subtitles" else ""
    return "${source.label} • $quality$subtitles"
}

private fun subtitleIndexForChoice(tracks: List<com.ttvralph.miruroapp.data.SubtitleTrack>, choice: String, autoIndex: Int): Int = when {
    choice.equals("Off", ignoreCase = true) -> -1
    choice.equals("Auto", ignoreCase = true) -> autoIndex
    else -> preferredSubtitleIndex(tracks, choice).takeIf { it >= 0 } ?: autoIndex
}

private fun preferredSubtitleIndex(tracks: List<com.ttvralph.miruroapp.data.SubtitleTrack>, preferredLanguage: String): Int {
    val preferred = preferredLanguage.lowercase(Locale.ROOT)
    return tracks.indexOfFirst { track ->
        track.language?.lowercase(Locale.ROOT)?.contains(preferred.take(2)) == true ||
            track.label.lowercase(Locale.ROOT).contains(preferred)
    }.takeIf { it >= 0 } ?: -1
}

private fun captionStyle(style: String): CaptionStyleCompat = when (style) {
    "High Contrast" -> CaptionStyleCompat(AndroidColor.WHITE, AndroidColor.BLACK, AndroidColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, null)
    else -> CaptionStyleCompat.DEFAULT
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
