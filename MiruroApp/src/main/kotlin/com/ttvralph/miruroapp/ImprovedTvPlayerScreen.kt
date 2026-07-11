@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ttvralph.miruroapp

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.PlaybackType
import com.ttvralph.miruroapp.data.SettingsStore
import com.ttvralph.miruroapp.data.SubtitleTrack
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val IMPROVED_PLAYER_TAG = "ImprovedTvPlayer"
private const val IMPROVED_SEEK_MS = 10_000L

private enum class ImprovedPlayerPanel {
    NONE,
    QUALITY,
    SOURCES,
    SUBTITLES,
    SPEED,
    DIAGNOSTICS
}

private enum class PlayerQualityMode(
    val setting: String,
    val label: String,
    val maxWidth: Int?,
    val maxHeight: Int?,
    val maxBitrate: Int?
) {
    AUTO("Auto", "Auto", null, null, null),
    P1080("1080p", "1080p", 1920, 1080, null),
    P720("720p", "720p", 1280, 720, null),
    P480("480p", "480p", 854, 480, null),
    DATA_SAVER("Data Saver", "Data Saver", 854, 480, 1_200_000);

    companion object {
        fun fromSetting(value: String): PlayerQualityMode =
            entries.firstOrNull { it.setting.equals(value, ignoreCase = true) } ?: AUTO
    }
}

private data class PlayerDiagnostics(
    val provider: String = "Unknown",
    val sourceLabel: String = "Unknown",
    val sourceNumber: String = "1/1",
    val streamType: String = "Unknown",
    val qualityMode: String = "Auto",
    val actualResolution: String = "Detecting…",
    val bitrate: String = "Detecting…",
    val bufferHealth: String = "0.0s",
    val bufferedPercent: String = "0%",
    val droppedFrames: Int = 0,
    val playbackState: String = "Preparing",
    val subtitle: String = "Off",
    val speed: String = "1.0x",
    val lastError: String? = null
)

@Composable
fun ImprovedTvPlayerScreen(
    viewModel: MiruroViewModel,
    episode: AnimeEpisode?,
    nextEpisode: AnimeEpisode?,
    onBack: () -> Unit,
    onPlayNext: (AnimeEpisode) -> Unit
) {
    if (episode == null) {
        ErrorState("Episode not found.", onBack)
        return
    }

    LaunchedEffect(episode) { viewModel.resolvePlayback(episode) }
    DisposableEffect(Unit) { onDispose { viewModel.clearPlayback() } }
    val state by viewModel.playback.collectAsState()

    when (val current = state) {
        null, is UiState.Loading -> LoadingState("Resolving stream…")
        is UiState.Error -> ErrorState(current.message, onBack)
        is UiState.Success -> ImprovedTvVideoPlayer(
            initialSource = current.data,
            episode = episode,
            nextEpisode = nextEpisode,
            viewModel = viewModel,
            onBack = onBack,
            onPlayNext = onPlayNext
        )
    }
}

@Composable
private fun ImprovedTvVideoPlayer(
    initialSource: PlaybackSource,
    episode: AnimeEpisode,
    nextEpisode: AnimeEpisode?,
    viewModel: MiruroViewModel,
    onBack: () -> Unit,
    onPlayNext: (AnimeEpisode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember(context) { SettingsStore(context) }
    val settings by viewModel.settings.collectAsState()
    val watchProgress by viewModel.watchProgress.collectAsState()
    val savedProgress = watchProgress.firstOrNull {
        it.animeId == episode.anilistId &&
            it.seasonNumber == episode.seasonNumber &&
            it.episodeNumber == episode.episodeNumber &&
            it.audioType == episode.audioType
    }

    val sources = remember(initialSource) {
        (listOf(initialSource.copy(fallbackSources = emptyList())) + initialSource.fallbackSources)
            .distinctBy { it.url to it.type }
    }
    var sourceIndex by remember(initialSource) { mutableIntStateOf(0) }
    val activeSource = sources[sourceIndex.coerceIn(0, sources.lastIndex)]
    var qualityMode by remember(initialSource) {
        mutableStateOf(PlayerQualityMode.fromSetting(settings.preferredQuality))
    }

    val automaticSubtitle = remember(activeSource, settings.subtitleLanguage) {
        improvedPreferredSubtitle(activeSource.subtitleTracks, settings.subtitleLanguage)
    }
    val initialSubtitle = remember(activeSource, settings.subtitleChoice, automaticSubtitle) {
        improvedSubtitleChoiceIndex(activeSource.subtitleTracks, settings.subtitleChoice, automaticSubtitle)
    }
    var subtitleIndex by remember(activeSource, initialSubtitle) { mutableIntStateOf(initialSubtitle) }
    var speed by remember(initialSource) { mutableFloatStateOf(1f) }
    var controlsVisible by remember(initialSource) { mutableStateOf(true) }
    var panel by remember(initialSource) { mutableStateOf(ImprovedPlayerPanel.NONE) }
    var playerError by remember(initialSource) { mutableStateOf<String?>(null) }
    var lastPlaybackError by remember(initialSource) { mutableStateOf<String?>(null) }
    var message by remember(initialSource) { mutableStateOf<String?>(null) }
    var ended by remember(initialSource) { mutableStateOf(false) }
    var countdown by remember(initialSource) { mutableIntStateOf(0) }
    var pendingPosition by remember(initialSource) { mutableLongStateOf(0L) }
    var fallbackEvents by remember(initialSource) { mutableStateOf(emptyList<String>()) }
    var diagnostics by remember(initialSource) { mutableStateOf(PlayerDiagnostics()) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }

    val trackSelector = remember(activeSource, qualityMode) {
        DefaultTrackSelector(context).apply {
            if (qualityMode != PlayerQualityMode.AUTO) {
                val builder = buildUponParameters()
                qualityMode.maxWidth?.let { width ->
                    builder.setMaxVideoSize(width, qualityMode.maxHeight ?: Int.MAX_VALUE)
                }
                qualityMode.maxBitrate?.let(builder::setMaxVideoBitrate)
                setParameters(builder)
            }
        }
    }

    val player = remember(activeSource, subtitleIndex, trackSelector) {
        val userAgent = activeSource.headers["User-Agent"]
        val headers = activeSource.headers - "User-Agent"
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .apply { if (userAgent != null) setUserAgent(userAgent) }
        val mediaItem = improvedPlayerMediaItem(activeSource, subtitleIndex)
        val mediaSource = when (activeSource.type) {
            PlaybackType.HLS -> HlsMediaSource.Factory(dataSource).createMediaSource(mediaItem)
            PlaybackType.DASH -> DashMediaSource.Factory(dataSource).createMediaSource(mediaItem)
            else -> ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem)
        }

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            ended = true
                            controlsVisible = false
                            panel = ImprovedPlayerPanel.NONE
                            viewModel.setEpisodeWatched(episode, true)
                            countdown = if (settings.autoPlayNext && nextEpisode != null) 5 else 0
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val errorText = "${error.errorCodeName}: ${error.message ?: "Playback failed"}"
                        lastPlaybackError = errorText
                        Log.w(IMPROVED_PLAYER_TAG, "Playback failed for ${activeSource.label}", error)
                        if (sourceIndex < sources.lastIndex) {
                            pendingPosition = currentPosition.coerceAtLeast(0L)
                            val nextLabel = sources[sourceIndex + 1].label
                            fallbackEvents = (fallbackEvents + "${activeSource.label} failed → $nextLabel").takeLast(8)
                            message = "${activeSource.label} failed. Switching to $nextLabel…"
                            sourceIndex += 1
                        } else {
                            playerError = errorText
                        }
                    }
                })
                setMediaSource(mediaSource)
                setPlaybackSpeed(speed)
                playWhenReady = true
                prepare()
            }
    }

    var initialSeekApplied by remember(player) { mutableStateOf(false) }
    LaunchedEffect(player) {
        if (!initialSeekApplied) {
            val resume = pendingPosition.takeIf { it > 0L }
                ?: savedProgress
                    ?.takeIf { settings.resumePlayback && !it.watched && it.positionMs > 10_000L }
                    ?.positionMs
            resume?.let(player::seekTo)
            initialSeekApplied = true
            pendingPosition = 0L
        }
    }

    DisposableEffect(player) {
        onDispose {
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            if (duration > 0L) viewModel.saveProgress(episode, player.currentPosition, duration)
            player.release()
        }
    }

    LaunchedEffect(player, activeSource, qualityMode, subtitleIndex, speed, lastPlaybackError) {
        while (true) {
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            if (duration > 0L && player.currentPosition > 0L && !ended) {
                viewModel.saveProgress(episode, player.currentPosition, duration)
            }
            val format = player.videoFormat
            val width = format?.width?.takeIf { it > 0 }
            val height = format?.height?.takeIf { it > 0 }
            val bitrate = format?.bitrate?.takeIf { it > 0 }
            val bufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
            val selectedSubtitle = activeSource.subtitleTracks.getOrNull(subtitleIndex)
            diagnostics = PlayerDiagnostics(
                provider = improvedProviderName(activeSource),
                sourceLabel = activeSource.label,
                sourceNumber = "${sourceIndex + 1}/${sources.size}",
                streamType = activeSource.type.name,
                qualityMode = qualityMode.label,
                actualResolution = when {
                    width != null && height != null -> "${width}×${height} (${height}p)"
                    else -> improvedQualityLabel(activeSource)
                },
                bitrate = bitrate?.let { "${it / 1_000} kbps" } ?: "Adaptive/unknown",
                bufferHealth = String.format(Locale.US, "%.1fs", bufferMs / 1_000f),
                bufferedPercent = "${player.bufferedPercentage.coerceIn(0, 100)}%",
                droppedFrames = player.videoDecoderCounters?.droppedBufferCount ?: 0,
                playbackState = improvedPlaybackState(player.playbackState, player.isPlaying),
                subtitle = selectedSubtitle?.let(::improvedSubtitleLabel) ?: "Off",
                speed = "${speed}x",
                lastError = lastPlaybackError
            )
            delay(500L)
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2_200L)
            message = null
        }
    }
    LaunchedEffect(countdown) {
        if (countdown > 0 && nextEpisode != null) {
            delay(1_000L)
            if (countdown == 1) onPlayNext(nextEpisode) else countdown -= 1
        }
    }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    LaunchedEffect(controlsVisible, panel, ended) {
        delay(80L)
        when {
            ended -> Unit
            panel != ImprovedPlayerPanel.NONE -> runCatching { menuFocus.requestFocus() }
            controlsVisible -> runCatching { playFocus.requestFocus() }
            else -> runCatching { rootFocus.requestFocus() }
        }
    }

    fun selectQuality(selected: PlayerQualityMode) {
        pendingPosition = player.currentPosition.coerceAtLeast(0L)
        qualityMode = selected
        scope.launch { settingsStore.updatePreferredQuality(selected.setting) }
        val candidate = improvedSourceForQuality(
            sources = sources,
            mode = selected,
            currentProvider = improvedProviderName(activeSource)
        )
        if (candidate != null && candidate != sourceIndex) {
            val from = activeSource.label
            val to = sources[candidate].label
            sourceIndex = candidate
            fallbackEvents = (fallbackEvents + "Manual quality: $from → $to").takeLast(8)
        }
        message = if (selected == PlayerQualityMode.AUTO) {
            "Quality set to Auto"
        } else {
            "Quality limited to ${selected.label}"
        }
        panel = ImprovedPlayerPanel.NONE
    }

    BackHandler {
        when {
            panel != ImprovedPlayerPanel.NONE -> panel = ImprovedPlayerPanel.NONE
            ended -> onBack()
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }

    val error = playerError
    if (error != null) {
        Column(
            Modifier.fillMaxSize().background(Color.Black),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ErrorState(error, onBack)
            if (sourceIndex < sources.lastIndex) {
                PlayerBButton("Try another source", 260, settings.largePlayerControls, settings.highContrastPlayerControls) {
                    pendingPosition = player.currentPosition.coerceAtLeast(0L)
                    sourceIndex += 1
                    playerError = null
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controlsVisible = true
                        true
                    }
                    Key.MediaPlay -> { player.play(); controlsVisible = true; true }
                    Key.MediaPause -> { player.pause(); controlsVisible = true; true }
                    Key.MediaRewind -> {
                        player.seekTo((player.currentPosition - IMPROVED_SEEK_MS).coerceAtLeast(0L))
                        controlsVisible = true
                        true
                    }
                    Key.MediaFastForward -> {
                        player.seekTo(player.currentPosition + IMPROVED_SEEK_MS)
                        controlsVisible = true
                        true
                    }
                    Key.DirectionLeft -> if (!controlsVisible) {
                        player.seekTo((player.currentPosition - IMPROVED_SEEK_MS).coerceAtLeast(0L))
                        controlsVisible = true
                        message = "Rewind 10 seconds"
                        true
                    } else false
                    Key.DirectionRight -> if (!controlsVisible) {
                        player.seekTo(player.currentPosition + IMPROVED_SEEK_MS)
                        controlsVisible = true
                        message = "Forward 10 seconds"
                        true
                    } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> if (!controlsVisible) {
                        controlsVisible = true
                        true
                    } else false
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            },
            update = { view ->
                view.player = player
                view.subtitleView?.setStyle(
                    improvedCaptionStyle(settings.subtitleStyle, settings.subtitleBackground)
                )
                view.subtitleView?.setFixedTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    improvedSubtitleSize(settings.subtitleSize)
                )
            }
        )

        if (controlsVisible && !ended) {
            ImprovedPlayerControls(
                player = player,
                episode = episode,
                diagnostics = diagnostics,
                qualityMode = qualityMode,
                speed = speed,
                nextEpisode = nextEpisode,
                playFocus = playFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls,
                onBack = onBack,
                onSeekBack = { player.seekTo((player.currentPosition - IMPROVED_SEEK_MS).coerceAtLeast(0L)) },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onSeekForward = { player.seekTo(player.currentPosition + IMPROVED_SEEK_MS) },
                onSeekFraction = { fraction ->
                    val duration = player.duration.takeIf { it > 0L } ?: 0L
                    if (duration > 0L) player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
                },
                onQuality = { panel = ImprovedPlayerPanel.QUALITY },
                onSource = { panel = ImprovedPlayerPanel.SOURCES },
                onSubtitle = { panel = ImprovedPlayerPanel.SUBTITLES },
                onSpeed = { panel = ImprovedPlayerPanel.SPEED },
                onDiagnostics = { panel = ImprovedPlayerPanel.DIAGNOSTICS },
                onNext = { nextEpisode?.let(onPlayNext) },
                onHide = { controlsVisible = false }
            )
        }

        when (panel) {
            ImprovedPlayerPanel.QUALITY -> ImprovedQualityPanel(
                selected = qualityMode,
                sources = sources,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls,
                onSelected = ::selectQuality
            )
            ImprovedPlayerPanel.SOURCES -> ImprovedSourcePanel(
                sources = sources,
                selected = sourceIndex,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls
            ) { index ->
                pendingPosition = player.currentPosition.coerceAtLeast(0L)
                sourceIndex = index
                panel = ImprovedPlayerPanel.NONE
                message = "Switched to ${sources[index].label}"
            }
            ImprovedPlayerPanel.SUBTITLES -> ImprovedSubtitlePanel(
                source = activeSource,
                selected = subtitleIndex,
                automatic = automaticSubtitle,
                textSize = settings.subtitleSize,
                background = settings.subtitleBackground,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls,
                onTrackSelected = { index, choice ->
                    pendingPosition = player.currentPosition.coerceAtLeast(0L)
                    subtitleIndex = index
                    viewModel.updateSubtitleChoice(choice)
                    panel = ImprovedPlayerPanel.NONE
                },
                onSizeSelected = { value -> scope.launch { settingsStore.updateSubtitleSize(value) } },
                onBackgroundSelected = { value -> scope.launch { settingsStore.updateSubtitleBackground(value) } }
            )
            ImprovedPlayerPanel.SPEED -> ImprovedSpeedPanel(
                selected = speed,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls
            ) { selected ->
                speed = selected
                player.setPlaybackSpeed(selected)
                panel = ImprovedPlayerPanel.NONE
            }
            ImprovedPlayerPanel.DIAGNOSTICS -> ImprovedDiagnosticsPanel(
                diagnostics = diagnostics,
                fallbackEvents = fallbackEvents,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls,
                onClose = { panel = ImprovedPlayerPanel.NONE }
            )
            ImprovedPlayerPanel.NONE -> Unit
        }

        message?.let {
            Text(
                it,
                color = Color.White,
                fontSize = if (settings.largePlayerControls) 22.sp else 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(10.dp))
                    .border(
                        if (settings.highContrastPlayerControls) 2.dp else 0.dp,
                        if (settings.highContrastPlayerControls) Color.White else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ImprovedPlayerControls(
    player: Player,
    episode: AnimeEpisode,
    diagnostics: PlayerDiagnostics,
    qualityMode: PlayerQualityMode,
    speed: Float,
    nextEpisode: AnimeEpisode?,
    playFocus: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onBack: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onQuality: () -> Unit,
    onSource: () -> Unit,
    onSubtitle: () -> Unit,
    onSpeed: () -> Unit,
    onDiagnostics: () -> Unit,
    onNext: () -> Unit,
    onHide: () -> Unit
) {
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            isPlaying = player.isPlaying
            delay(400L)
        }
    }
    val progress = if (duration > 0L) position.toFloat() / duration.toFloat() else 0f
    val panelAlpha = if (highContrast) 0.96f else 0.84f

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (highContrast) 0.34f else 0.22f)).focusGroup()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = panelAlpha))
                .padding(if (largeControls) 20.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerBButton("Back", 110, largeControls, highContrast, onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}",
                    color = MiruroColors.AccentSoft,
                    fontSize = if (largeControls) 16.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    episode.title ?: "Episode ${episode.episodeNumber}",
                    color = Color.White,
                    fontSize = if (largeControls) 24.sp else 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${diagnostics.provider} • ${diagnostics.actualResolution} • ${diagnostics.streamType}",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = if (largeControls) 14.sp else 12.sp,
                modifier = Modifier.padding(end = 14.dp)
            )
            PlayerBButton("Hide", 110, largeControls, highContrast, onHide)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (largeControls) 22.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerBButton("−10s", 120, largeControls, highContrast, onSeekBack)
            PlayerBButton(
                if (isPlaying) "Pause" else "Play",
                175,
                largeControls,
                highContrast,
                onPlayPause,
                Modifier.focusRequester(playFocus),
                primary = true
            )
            PlayerBButton("+10s", 120, largeControls, highContrast, onSeekForward)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = panelAlpha))
                .padding(if (largeControls) 19.dp else 14.dp)
        ) {
            ImprovedSeekBar(position, duration, progress, onSeekFraction, highContrast)
            Spacer(Modifier.height(if (largeControls) 15.dp else 11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerBButton("Quality ${qualityMode.label}", 175, largeControls, highContrast, onQuality)
                PlayerBButton("Source ${diagnostics.sourceNumber}", 150, largeControls, highContrast, onSource)
                PlayerBButton("Subtitles", 140, largeControls, highContrast, onSubtitle)
                PlayerBButton("Speed ${speed}x", 135, largeControls, highContrast, onSpeed)
                PlayerBButton("Diagnostics", 150, largeControls, highContrast, onDiagnostics)
                PlayerBButton(
                    if (nextEpisode != null) "Next episode" else "No next",
                    175,
                    largeControls,
                    highContrast,
                    if (nextEpisode != null) onNext else onHide
                )
            }
        }
    }
}

@Composable
private fun PlayerBButton(
    text: String,
    width: Int,
    large: Boolean,
    highContrast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(width.dp).height(if (large) 58.dp else 48.dp),
        shape = RoundedCornerShape(8.dp),
        unfocusedBackground = when {
            primary -> Color.White
            highContrast -> Color.Black
            else -> Color.White.copy(alpha = 0.10f)
        },
        focusedBackground = if (highContrast) Color(0xFFFFE45C) else Color.White
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    if (highContrast && !focused) 2.dp else 0.dp,
                    if (highContrast && !focused) Color.White else Color.Transparent,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (focused || primary) Color.Black else Color.White,
                fontSize = if (large) 16.sp else 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ImprovedSeekBar(
    position: Long,
    duration: Long,
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    highContrast: Boolean
) {
    var preview by remember(progress) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    FocusableSurface(
        onClick = { onSeekFraction(preview) },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        preview = (preview - 0.02f).coerceAtLeast(0f)
                        onSeekFraction(preview)
                        true
                    }
                    Key.DirectionRight -> {
                        preview = (preview + 0.02f).coerceAtMost(1f)
                        onSeekFraction(preview)
                        true
                    }
                    else -> false
                }
            },
        shape = RoundedCornerShape(8.dp),
        unfocusedBackground = if (highContrast) Color.Black else Color.White.copy(alpha = 0.04f),
        focusedBackground = Color.White.copy(alpha = 0.18f)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
            Row {
                Text(improvedPlayerTime(position), color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(improvedPlayerTime(duration), color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { preview },
                modifier = Modifier.fillMaxWidth().height(if (highContrast) 7.dp else 5.dp),
                color = if (highContrast) Color(0xFFFFE45C) else MiruroColors.Accent,
                trackColor = Color.White.copy(alpha = 0.24f)
            )
        }
    }
}

@Composable
private fun ImprovedQualityPanel(
    selected: PlayerQualityMode,
    sources: List<PlaybackSource>,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onSelected: (PlayerQualityMode) -> Unit
) {
    ImprovedPlayerPanel("Video quality") {
        PlayerQualityMode.entries.forEachIndexed { index, choice ->
            val explicit = improvedSourceForQuality(sources, choice, null)
            item {
                ImprovedPanelRow(
                    text = choice.label,
                    supporting = when {
                        choice == PlayerQualityMode.AUTO -> "Adaptive quality and automatic source fallback"
                        explicit != null -> "Uses ${sources[explicit].label}; adaptive streams are also limited"
                        else -> "Limits adaptive playback when an explicit ${choice.label} source is unavailable"
                    },
                    selected = choice == selected,
                    modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                    large = largeControls,
                    highContrast = highContrast
                ) { onSelected(choice) }
            }
        }
    }
}

@Composable
private fun ImprovedSourcePanel(
    sources: List<PlaybackSource>,
    selected: Int,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onSelected: (Int) -> Unit
) {
    ImprovedPlayerPanel("Quality & source") {
        itemsIndexed(sources) { index, source ->
            ImprovedPanelRow(
                text = "${index + 1}. ${source.label}",
                supporting = "${improvedProviderName(source)} • ${improvedQualityLabel(source)} • ${source.type.name}",
                selected = index == selected,
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                large = largeControls,
                highContrast = highContrast
            ) { onSelected(index) }
        }
    }
}

@Composable
private fun ImprovedSubtitlePanel(
    source: PlaybackSource,
    selected: Int,
    automatic: Int,
    textSize: String,
    background: String,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onTrackSelected: (Int, String) -> Unit,
    onSizeSelected: (String) -> Unit,
    onBackgroundSelected: (String) -> Unit
) {
    val choices = buildList {
        add(Triple("Off", -1, "Off"))
        add(Triple("Auto (${source.subtitleTracks.getOrNull(automatic)?.let(::improvedSubtitleLabel) ?: "preferred language"})", automatic, "Auto"))
        source.subtitleTracks.forEachIndexed { index, track ->
            add(Triple(improvedSubtitleLabel(track), index, track.language ?: track.label))
        }
    }
    ImprovedPlayerPanel("Subtitles & captions") {
        item { ImprovedPanelSection("Track") }
        itemsIndexed(choices) { index, choice ->
            ImprovedPanelRow(
                text = choice.first,
                supporting = if (choice.first.contains("SDH") || choice.first.contains("CC")) "Caption track" else null,
                selected = choice.second == selected,
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                large = largeControls,
                highContrast = highContrast
            ) { onTrackSelected(choice.second, choice.third) }
        }
        item { ImprovedPanelSection("Text size") }
        itemsIndexed(listOf("Small", "Medium", "Large", "Extra Large")) { _, option ->
            ImprovedPanelRow(option, null, option == textSize, Modifier, largeControls, highContrast) {
                onSizeSelected(option)
            }
        }
        item { ImprovedPanelSection("Background opacity") }
        itemsIndexed(listOf("Off", "Low", "Medium", "High")) { _, option ->
            ImprovedPanelRow(option, null, option == background, Modifier, largeControls, highContrast) {
                onBackgroundSelected(option)
            }
        }
    }
}

@Composable
private fun ImprovedSpeedPanel(
    selected: Float,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onSelected: (Float) -> Unit
) {
    ImprovedPlayerPanel("Playback speed") {
        itemsIndexed(listOf(0.75f, 1f, 1.25f, 1.5f, 2f)) { index, choice ->
            ImprovedPanelRow(
                text = "${choice}x",
                supporting = null,
                selected = choice == selected,
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                large = largeControls,
                highContrast = highContrast
            ) { onSelected(choice) }
        }
    }
}

@Composable
private fun ImprovedDiagnosticsPanel(
    diagnostics: PlayerDiagnostics,
    fallbackEvents: List<String>,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onClose: () -> Unit
) {
    ImprovedPlayerPanel("Playback diagnostics", width = 500) {
        item {
            ImprovedPanelRow(
                "Close diagnostics",
                "Live values refresh twice per second",
                false,
                Modifier.focusRequester(focusRequester),
                largeControls,
                highContrast,
                onClose
            )
        }
        item { ImprovedDiagnosticLine("Provider", diagnostics.provider) }
        item { ImprovedDiagnosticLine("Source", "${diagnostics.sourceNumber} • ${diagnostics.sourceLabel}") }
        item { ImprovedDiagnosticLine("Stream", diagnostics.streamType) }
        item { ImprovedDiagnosticLine("Quality mode", diagnostics.qualityMode) }
        item { ImprovedDiagnosticLine("Playing resolution", diagnostics.actualResolution) }
        item { ImprovedDiagnosticLine("Video bitrate", diagnostics.bitrate) }
        item { ImprovedDiagnosticLine("Buffer health", "${diagnostics.bufferHealth} • ${diagnostics.bufferedPercent}") }
        item { ImprovedDiagnosticLine("Dropped frames", diagnostics.droppedFrames.toString()) }
        item { ImprovedDiagnosticLine("State", diagnostics.playbackState) }
        item { ImprovedDiagnosticLine("Subtitle", diagnostics.subtitle) }
        item { ImprovedDiagnosticLine("Speed", diagnostics.speed) }
        diagnostics.lastError?.let { error -> item { ImprovedDiagnosticLine("Last error", error) } }
        if (fallbackEvents.isNotEmpty()) {
            item { ImprovedPanelSection("Fallback history") }
            itemsIndexed(fallbackEvents.reversed()) { _, event ->
                Text(
                    event,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ImprovedPlayerPanel(
    title: String,
    width: Int = 460,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .padding(end = 28.dp)
                .width(width.dp)
                .background(Color.Black.copy(alpha = 0.97f), RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ImprovedPanelRow(
    text: String,
    supporting: String?,
    selected: Boolean,
    modifier: Modifier,
    large: Boolean,
    highContrast: Boolean,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(if (large || supporting != null) 68.dp else 54.dp),
        shape = RoundedCornerShape(8.dp),
        unfocusedBackground = when {
            selected -> MiruroColors.Accent.copy(alpha = 0.30f)
            highContrast -> Color.Black
            else -> Color.White.copy(alpha = 0.05f)
        },
        focusedBackground = if (highContrast) Color(0xFFFFE45C) else Color.White
    ) { focused ->
        Column(
            Modifier
                .fillMaxSize()
                .border(
                    if (highContrast && !focused) 1.dp else 0.dp,
                    if (highContrast && !focused) Color.White else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (selected) "✓ $text" else text,
                color = when {
                    focused -> Color.Black
                    selected -> MiruroColors.AccentSoft
                    else -> Color.White
                },
                fontSize = if (large) 17.sp else 15.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            supporting?.let {
                Text(
                    it,
                    color = if (focused) Color.DarkGray else Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ImprovedPanelSection(text: String) {
    Text(
        text.uppercase(Locale.ROOT),
        color = MiruroColors.AccentSoft,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 10.dp, start = 5.dp, bottom = 3.dp)
    )
}

@Composable
private fun ImprovedDiagnosticLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, modifier = Modifier.width(150.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

private fun improvedSourceForQuality(
    sources: List<PlaybackSource>,
    mode: PlayerQualityMode,
    currentProvider: String?
): Int? {
    if (mode == PlayerQualityMode.AUTO) return null
    val indexed = sources.mapIndexedNotNull { index, source ->
        improvedQualityHeight(source)?.let { height -> Triple(index, source, height) }
    }
    if (indexed.isEmpty()) return null
    val target = mode.maxHeight ?: return null
    val eligible = indexed.filter { it.third <= target }
    val pool = eligible.ifEmpty { indexed }
    return pool.sortedWith(
        compareByDescending<Triple<Int, PlaybackSource, Int>> {
            currentProvider != null && improvedProviderName(it.second).equals(currentProvider, ignoreCase = true)
        }.thenBy { kotlin.math.abs(target - it.third) }
            .thenByDescending { it.third }
    ).firstOrNull()?.first
}

private fun improvedProviderName(source: PlaybackSource): String =
    source.label.substringBefore(' ').takeIf { it.isNotBlank() } ?: "Unknown"

private fun improvedQualityHeight(source: PlaybackSource): Int? =
    Regex("""(?i)(2160|1440|1080|720|480|360)p""")
        .find(source.label)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

private fun improvedQualityLabel(source: PlaybackSource): String =
    improvedQualityHeight(source)?.let { "${it}p" } ?: when (source.type) {
        PlaybackType.HLS, PlaybackType.DASH -> "Adaptive"
        else -> "Auto"
    }

private fun improvedSubtitleLabel(track: SubtitleTrack): String {
    val base = track.label.ifBlank { track.language ?: "Subtitle" }
    val lower = base.lowercase(Locale.ROOT)
    return when {
        "sdh" in lower -> base.replace("sdh", "SDH", ignoreCase = true)
        Regex("""(^|\s|\()cc($|\s|\))""", RegexOption.IGNORE_CASE).containsMatchIn(base) -> base.replace("cc", "CC", ignoreCase = true)
        else -> base
    }
}

private fun improvedSubtitleChoiceIndex(
    tracks: List<SubtitleTrack>,
    choice: String,
    automatic: Int
): Int = when {
    choice.equals("Off", ignoreCase = true) -> -1
    choice.equals("Auto", ignoreCase = true) -> automatic
    else -> improvedPreferredSubtitle(tracks, choice).takeIf { it >= 0 } ?: automatic
}

private fun improvedPreferredSubtitle(tracks: List<SubtitleTrack>, language: String): Int {
    val preferred = language.lowercase(Locale.ROOT)
    return tracks.indexOfFirst { track ->
        track.language?.lowercase(Locale.ROOT)?.contains(preferred.take(2)) == true ||
            track.label.lowercase(Locale.ROOT).contains(preferred)
    }.takeIf { it >= 0 } ?: -1
}

private fun improvedCaptionStyle(style: String, background: String): CaptionStyleCompat {
    val alpha = when (background) {
        "Off" -> 0
        "Low" -> 80
        "High" -> 230
        else -> 165
    }
    val highContrast = style == "High Contrast"
    return CaptionStyleCompat(
        AndroidColor.WHITE,
        AndroidColor.argb(if (highContrast) 255 else alpha, 0, 0, 0),
        AndroidColor.TRANSPARENT,
        if (highContrast) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
        AndroidColor.BLACK,
        null
    )
}

private fun improvedSubtitleSize(value: String): Float = when (value) {
    "Small" -> 15f
    "Large" -> 24f
    "Extra Large" -> 29f
    else -> 19f
}

private fun improvedPlayerMediaItem(source: PlaybackSource, subtitleIndex: Int): MediaItem {
    val mime = when (source.type) {
        PlaybackType.HLS -> MimeTypes.APPLICATION_M3U8
        PlaybackType.DASH -> MimeTypes.APPLICATION_MPD
        else -> null
    }
    return MediaItem.Builder()
        .setUri(source.url)
        .setMimeType(mime)
        .setSubtitleConfigurations(
            source.subtitleTracks.mapIndexedNotNull { index, track ->
                if (subtitleIndex == -1 || index != subtitleIndex || track.url.isBlank()) return@mapIndexedNotNull null
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                    .setMimeType(improvedSubtitleMime(track.url))
                    .setLanguage(track.language)
                    .setLabel(track.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
        )
        .build()
}

private fun improvedSubtitleMime(url: String): String {
    val path = url.substringBefore('?').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

private fun improvedPlaybackState(state: Int, isPlaying: Boolean): String = when (state) {
    Player.STATE_IDLE -> "Idle"
    Player.STATE_BUFFERING -> "Buffering"
    Player.STATE_READY -> if (isPlaying) "Playing" else "Paused"
    Player.STATE_ENDED -> "Ended"
    else -> "Unknown"
}

private fun improvedPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
