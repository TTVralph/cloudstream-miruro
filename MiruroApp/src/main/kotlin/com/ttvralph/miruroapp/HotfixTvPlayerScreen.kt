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
import androidx.compose.ui.focus.onFocusChanged
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
import com.ttvralph.miruroapp.data.SkipInterval
import com.ttvralph.miruroapp.data.SubtitleTrack
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOTFIX_PLAYER_TAG = "HotfixTvPlayer"
private const val HOTFIX_SEEK_MS = 10_000L
private const val HOTFIX_PROGRESS_SAVE_INTERVAL_MS = 10_000L
private const val HOTFIX_CONTROLS_HIDE_DELAY_MS = 5_000L

private enum class HotfixPanel {
    NONE,
    QUALITY,
    SOURCES,
    SUBTITLES,
    SPEED,
    DIAGNOSTICS
}

private enum class HotfixQuality(
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
        fun fromSetting(value: String): HotfixQuality =
            entries.firstOrNull { it.setting.equals(value, ignoreCase = true) } ?: AUTO
    }
}

private sealed interface HotfixSubtitleChoice {
    data object Off : HotfixSubtitleChoice
    data object Auto : HotfixSubtitleChoice
    data class Track(val index: Int) : HotfixSubtitleChoice
}

private data class HotfixDiagnostics(
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
fun HotfixTvPlayerScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    episode: AnimeEpisode?,
    nextEpisode: AnimeEpisode?,
    onBack: () -> Unit,
    onPlayNext: (AnimeEpisode) -> Unit
) {
    if (episode == null) {
        HotfixPlayerErrorScreen(
            message = "Episode not found.",
            canTryNext = false,
            onRetry = onBack,
            onTryNext = {},
            onBack = onBack
        )
        return
    }

    LaunchedEffect(episode) { viewModel.resolvePlayback(episode) }
    DisposableEffect(episode) { onDispose { viewModel.clearPlayback() } }
    val state by viewModel.playback.collectAsState()

    when (val current = state) {
        null, is UiState.Loading -> LoadingState("Resolving stream…")
        is UiState.Error -> HotfixPlayerErrorScreen(
            message = current.message,
            canTryNext = false,
            onRetry = { viewModel.resolvePlayback(episode) },
            onTryNext = {},
            onBack = onBack
        )
        is UiState.Success -> HotfixVideoPlayer(
            initialSource = current.data,
            episode = episode,
            nextEpisode = nextEpisode,
            viewModel = viewModel,
            features = features,
            onBack = onBack,
            onPlayNext = onPlayNext
        )
    }
}

@Composable
private fun HotfixVideoPlayer(
    initialSource: PlaybackSource,
    episode: AnimeEpisode,
    nextEpisode: AnimeEpisode?,
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
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
    val resumeSourceIndex = remember(sources, savedProgress?.sourceProvider, savedProgress?.sourceLabel) {
        sources.indexOfFirst { source ->
            savedProgress?.sourceLabel?.let { source.label.equals(it, ignoreCase = true) } == true
        }.takeIf { it >= 0 } ?: sources.indexOfFirst { source ->
            savedProgress?.sourceProvider?.let {
                hotfixProviderName(source).equals(it, ignoreCase = true)
            } == true
        }.coerceAtLeast(0)
    }
    var sourceIndex by remember(initialSource, resumeSourceIndex) { mutableIntStateOf(resumeSourceIndex) }
    val activeSource = sources[sourceIndex.coerceIn(0, sources.lastIndex)]
    var quality by remember(initialSource) { mutableStateOf(HotfixQuality.fromSetting(settings.preferredQuality)) }
    var subtitleChoice by remember(activeSource) {
        mutableStateOf(hotfixSubtitleChoiceFromSetting(activeSource.subtitleTracks, settings.subtitleChoice, settings.subtitleLanguage))
    }
    val subtitleIndex = hotfixSubtitleIndex(activeSource.subtitleTracks, subtitleChoice, settings.subtitleLanguage)
    var subtitleRecoveryChoice by remember(activeSource) { mutableStateOf<HotfixSubtitleChoice?>(null) }
    var subtitleRecoveryUntilMs by remember(activeSource) { mutableLongStateOf(0L) }
    var subtitleSize by remember(settings.subtitleSize) { mutableStateOf(settings.subtitleSize) }
    var subtitleBackground by remember(settings.subtitleBackground) { mutableStateOf(settings.subtitleBackground) }
    var speed by remember(initialSource) { mutableFloatStateOf(1f) }
    var controlsVisible by remember(initialSource) { mutableStateOf(true) }
    var controlsActivity by remember(initialSource) { mutableIntStateOf(0) }
    var panel by remember(initialSource) { mutableStateOf(HotfixPanel.NONE) }
    var playerError by remember(initialSource) { mutableStateOf<String?>(null) }
    var lastPlaybackError by remember(initialSource) { mutableStateOf<String?>(null) }
    var message by remember(initialSource) { mutableStateOf<String?>(null) }
    var ended by remember(initialSource) { mutableStateOf(false) }
    var autoplayCountdown by remember(initialSource) { mutableIntStateOf(0) }
    var pendingPosition by remember(initialSource) { mutableLongStateOf(0L) }
    var retryNonce by remember(initialSource) { mutableIntStateOf(0) }
    var fallbackEvents by remember(initialSource) { mutableStateOf(emptyList<String>()) }
    var diagnostics by remember(initialSource) { mutableStateOf(HotfixDiagnostics()) }
    val skipIntervals by features.skipIntervals.collectAsState()
    var skipPositionMs by remember(episode) { mutableLongStateOf(0L) }
    var skipDurationMs by remember(episode) { mutableLongStateOf(0L) }
    var handledSkipInterval by remember(episode) { mutableStateOf<SkipInterval?>(null) }
    var skipPromptFocused by remember(episode) { mutableStateOf(false) }
    val activeSkip = remember(skipIntervals, episode, skipPositionMs, skipDurationMs, handledSkipInterval) {
        features.intervalsFor(episode, skipDurationMs)
            .hotfixActiveAt(skipPositionMs)
            ?.takeUnless { it == handledSkipInterval }
    }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }

    val trackSelector = remember(activeSource, quality, retryNonce) {
        DefaultTrackSelector(context).apply {
            if (quality != HotfixQuality.AUTO) {
                val builder = buildUponParameters()
                quality.maxWidth?.let { width ->
                    builder.setMaxVideoSize(width, quality.maxHeight ?: Int.MAX_VALUE)
                }
                quality.maxBitrate?.let(builder::setMaxVideoBitrate)
                setParameters(builder)
            }
        }
    }

    val player = remember(activeSource, subtitleIndex, trackSelector, retryNonce) {
        val userAgent = activeSource.headers["User-Agent"]
        val headers = activeSource.headers - "User-Agent"
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .apply { if (userAgent != null) setUserAgent(userAgent) }
        val mediaItem = hotfixMediaItem(activeSource, subtitleIndex)
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
                        if (playbackState == Player.STATE_READY) {
                            playerError = null
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            ended = true
                            controlsVisible = false
                            panel = HotfixPanel.NONE
                            viewModel.setEpisodeWatched(episode, true)
                            autoplayCountdown = if (settings.autoPlayNext && nextEpisode != null) 5 else 0
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val errorText = "${error.errorCodeName}: ${error.message ?: "Playback failed"}"
                        lastPlaybackError = errorText
                        Log.w(HOTFIX_PLAYER_TAG, "Playback failed for ${activeSource.label}", error)

                        val recovery = subtitleRecoveryChoice
                        if (recovery != null && System.currentTimeMillis() <= subtitleRecoveryUntilMs) {
                            pendingPosition = currentPosition.coerceAtLeast(0L)
                            subtitleRecoveryChoice = null
                            subtitleChoice = recovery
                            message = "That subtitle track failed. Restored the previous subtitle setting."
                            return
                        }
                        subtitleRecoveryChoice = null
                        subtitleRecoveryUntilMs = 0L

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
            if (duration > 0L) {
                viewModel.saveProgress(
                    episode,
                    player.currentPosition,
                    duration,
                    hotfixProviderName(activeSource),
                    activeSource.label
                )
            }
            player.release()
        }
    }

    LaunchedEffect(player, activeSource, quality, subtitleChoice, speed, lastPlaybackError) {
        var lastSavedPosition = -HOTFIX_PROGRESS_SAVE_INTERVAL_MS
        while (true) {
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            val position = player.currentPosition.coerceAtLeast(0L)
            skipPositionMs = position
            skipDurationMs = duration
            if (duration > 0L) features.loadSkipTimes(episode, duration)
            if (
                duration > 0L && position > 0L && !ended &&
                abs(position - lastSavedPosition) >= HOTFIX_PROGRESS_SAVE_INTERVAL_MS
            ) {
                viewModel.saveProgress(
                    episode,
                    position,
                    duration,
                    hotfixProviderName(activeSource),
                    activeSource.label
                )
                lastSavedPosition = position
            }
            val format = player.videoFormat
            val width = format?.width?.takeIf { it > 0 }
            val height = format?.height?.takeIf { it > 0 }
            val bitrate = format?.bitrate?.takeIf { it > 0 }
            val bufferMs = (player.bufferedPosition - position).coerceAtLeast(0L)
            val selectedSubtitle = activeSource.subtitleTracks.getOrNull(subtitleIndex)
            diagnostics = HotfixDiagnostics(
                provider = hotfixProviderName(activeSource),
                sourceLabel = activeSource.label,
                sourceNumber = "${sourceIndex + 1}/${sources.size}",
                streamType = activeSource.type.name,
                qualityMode = quality.label,
                actualResolution = when {
                    width != null && height != null -> "${width}×${height} (${height}p)"
                    else -> hotfixQualityLabel(activeSource)
                },
                bitrate = bitrate?.let { "${it / 1_000} kbps" } ?: "Adaptive/unknown",
                bufferHealth = String.format(Locale.US, "%.1fs", bufferMs / 1_000f),
                bufferedPercent = "${player.bufferedPercentage.coerceIn(0, 100)}%",
                droppedFrames = player.videoDecoderCounters?.droppedBufferCount ?: 0,
                playbackState = hotfixPlaybackState(player.playbackState, player.isPlaying),
                subtitle = when (subtitleChoice) {
                    HotfixSubtitleChoice.Off -> "Off"
                    HotfixSubtitleChoice.Auto -> selectedSubtitle?.let(::hotfixSubtitleLabel)?.let { "Auto ($it)" } ?: "Auto"
                    is HotfixSubtitleChoice.Track -> selectedSubtitle?.let(::hotfixSubtitleLabel) ?: "Off"
                },
                speed = "${speed}x",
                lastError = lastPlaybackError
            )
            delay(500L)
        }
    }

    LaunchedEffect(subtitleRecoveryChoice, subtitleRecoveryUntilMs) {
        val remaining = subtitleRecoveryUntilMs - System.currentTimeMillis()
        if (subtitleRecoveryChoice != null && remaining > 0L) {
            delay(remaining)
            subtitleRecoveryChoice = null
            subtitleRecoveryUntilMs = 0L
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500L)
            message = null
        }
    }
    LaunchedEffect(autoplayCountdown) {
        if (autoplayCountdown > 0 && nextEpisode != null) {
            delay(1_000L)
            if (autoplayCountdown == 1) onPlayNext(nextEpisode) else autoplayCountdown -= 1
        }
    }
    LaunchedEffect(controlsVisible, panel, ended, playerError, activeSkip) {
        delay(90L)
        when {
            playerError != null -> Unit
            ended -> Unit
            panel != HotfixPanel.NONE -> runCatching { menuFocus.requestFocus() }
            controlsVisible -> runCatching { playFocus.requestFocus() }
            activeSkip != null -> runCatching { skipFocus.requestFocus() }
            else -> runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(controlsVisible, panel, ended, playerError, controlsActivity) {
        if (controlsVisible && panel == HotfixPanel.NONE && !ended && playerError == null) {
            delay(HOTFIX_CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    fun selectQuality(selected: HotfixQuality) {
        pendingPosition = player.currentPosition.coerceAtLeast(0L)
        quality = selected
        scope.launch { settingsStore.updatePreferredQuality(selected.setting) }
        val candidate = hotfixSourceForQuality(sources, selected, hotfixProviderName(activeSource))
        if (candidate != null && candidate != sourceIndex) {
            val from = activeSource.label
            val to = sources[candidate].label
            sourceIndex = candidate
            fallbackEvents = (fallbackEvents + "Manual quality: $from → $to").takeLast(8)
        }
        message = if (selected == HotfixQuality.AUTO) "Quality set to Auto" else "Quality limited to ${selected.label}"
        panel = HotfixPanel.NONE
    }

    fun selectSubtitle(choice: HotfixSubtitleChoice) {
        if (choice == subtitleChoice) return
        pendingPosition = player.currentPosition.coerceAtLeast(0L)
        subtitleRecoveryChoice = subtitleChoice
        subtitleRecoveryUntilMs = System.currentTimeMillis() + 5_000L
        subtitleChoice = choice
        scope.launch {
            settingsStore.updateSubtitleChoice(
                when (choice) {
                    HotfixSubtitleChoice.Off -> "Off"
                    HotfixSubtitleChoice.Auto -> "Auto"
                    is HotfixSubtitleChoice.Track -> activeSource.subtitleTracks
                        .getOrNull(choice.index)
                        ?.let { it.label.ifBlank { it.language ?: "Subtitle" } }
                        ?: "Auto"
                }
            )
        }
        message = when (choice) {
            HotfixSubtitleChoice.Off -> "Subtitles turned off"
            HotfixSubtitleChoice.Auto -> "Subtitles set to Auto"
            is HotfixSubtitleChoice.Track -> "Subtitle: ${activeSource.subtitleTracks.getOrNull(choice.index)?.let(::hotfixSubtitleLabel) ?: "Selected"}"
        }
        panel = HotfixPanel.NONE
    }

    fun performSkip(interval: SkipInterval) {
        handledSkipInterval = interval
        controlsVisible = false
        panel = HotfixPanel.NONE
        player.seekTo((interval.endMs + 250L).coerceAtMost(skipDurationMs))
        player.play()
    }

    BackHandler {
        when {
            playerError != null -> onBack()
            panel != HotfixPanel.NONE -> panel = HotfixPanel.NONE
            ended -> onBack()
            controlsVisible -> controlsVisible = false
            activeSkip != null -> handledSkipInterval = activeSkip
            else -> onBack()
        }
    }

    val error = playerError
    if (error != null) {
        HotfixPlayerErrorScreen(
            message = error,
            canTryNext = sourceIndex < sources.lastIndex,
            onRetry = {
                pendingPosition = player.currentPosition.coerceAtLeast(0L)
                playerError = null
                retryNonce += 1
            },
            onTryNext = {
                pendingPosition = player.currentPosition.coerceAtLeast(0L)
                if (sourceIndex < sources.lastIndex) sourceIndex += 1
                playerError = null
            },
            onBack = onBack
        )
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
                if (controlsVisible) controlsActivity += 1
                val prompt = activeSkip
                if (skipPromptFocused && prompt != null) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            performSkip(prompt)
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionLeft -> {
                            player.seekTo((player.currentPosition - HOTFIX_SEEK_MS).coerceAtLeast(0L))
                            controlsVisible = false
                            message = "Rewind 10 seconds"
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionRight -> {
                            player.seekTo(player.currentPosition + HOTFIX_SEEK_MS)
                            controlsVisible = false
                            message = "Forward 10 seconds"
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            controlsVisible = true
                            controlsActivity += 1
                            return@onPreviewKeyEvent true
                        }
                        else -> Unit
                    }
                }
                when (event.key) {
                    Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controlsVisible = true
                        true
                    }
                    Key.MediaPlay -> { player.play(); controlsVisible = true; true }
                    Key.MediaPause -> { player.pause(); controlsVisible = true; true }
                    Key.MediaRewind -> {
                        player.seekTo((player.currentPosition - HOTFIX_SEEK_MS).coerceAtLeast(0L))
                        controlsVisible = true
                        true
                    }
                    Key.MediaFastForward -> {
                        player.seekTo(player.currentPosition + HOTFIX_SEEK_MS)
                        controlsVisible = true
                        true
                    }
                    Key.DirectionLeft -> if (!controlsVisible) {
                        player.seekTo((player.currentPosition - HOTFIX_SEEK_MS).coerceAtLeast(0L))
                        message = "Rewind 10 seconds"
                        true
                    } else false
                    Key.DirectionRight -> if (!controlsVisible) {
                        player.seekTo(player.currentPosition + HOTFIX_SEEK_MS)
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
                view.subtitleView?.setStyle(hotfixCaptionStyle(settings.subtitleStyle, subtitleBackground))
                view.subtitleView?.setFixedTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    hotfixSubtitleSize(subtitleSize)
                )
            }
        )

        if (controlsVisible && !ended) {
            HotfixControls(
                player = player,
                episode = episode,
                diagnostics = diagnostics,
                quality = quality,
                speed = speed,
                nextEpisode = nextEpisode,
                playFocus = playFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls,
                onBack = onBack,
                onSeekBack = { player.seekTo((player.currentPosition - HOTFIX_SEEK_MS).coerceAtLeast(0L)) },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onSeekForward = { player.seekTo(player.currentPosition + HOTFIX_SEEK_MS) },
                onSeekFraction = { fraction ->
                    val duration = player.duration.takeIf { it > 0L } ?: 0L
                    if (duration > 0L) player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
                },
                onQuality = { panel = HotfixPanel.QUALITY },
                onSource = { panel = HotfixPanel.SOURCES },
                onSubtitle = { panel = HotfixPanel.SUBTITLES },
                onSpeed = { panel = HotfixPanel.SPEED },
                onDiagnostics = { panel = HotfixPanel.DIAGNOSTICS },
                onNext = { nextEpisode?.let(onPlayNext) },
                onHide = { controlsVisible = false }
            )
        }

        activeSkip?.takeIf { panel == HotfixPanel.NONE && !ended }?.let { interval ->
            HotfixSkipPrompt(
                interval = interval,
                focusRequester = skipFocus,
                controlsVisible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 46.dp,
                        bottom = if (controlsVisible) 128.dp else 42.dp
                    ),
                onFocused = { skipPromptFocused = it },
                onClick = { performSkip(interval) }
            )
        }

        when (panel) {
            HotfixPanel.QUALITY -> HotfixQualityPanel(
                selected = quality,
                sources = sources,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls,
                onSelected = ::selectQuality
            )
            HotfixPanel.SOURCES -> HotfixSourcePanel(
                sources = sources,
                selected = sourceIndex,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls
            ) { index ->
                pendingPosition = player.currentPosition.coerceAtLeast(0L)
                sourceIndex = index
                panel = HotfixPanel.NONE
                message = "Switched to ${sources[index].label}"
            }
            HotfixPanel.SUBTITLES -> HotfixSubtitlePanel(
                source = activeSource,
                selected = subtitleChoice,
                automatic = hotfixPreferredSubtitle(activeSource.subtitleTracks, settings.subtitleLanguage),
                textSize = subtitleSize,
                background = subtitleBackground,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls,
                onClose = { panel = HotfixPanel.NONE },
                onTrackSelected = ::selectSubtitle,
                onSizeSelected = { value ->
                    subtitleSize = value
                    scope.launch { settingsStore.updateSubtitleSize(value) }
                },
                onBackgroundSelected = { value ->
                    subtitleBackground = value
                    scope.launch { settingsStore.updateSubtitleBackground(value) }
                }
            )
            HotfixPanel.SPEED -> HotfixSpeedPanel(
                selected = speed,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls
            ) { selected ->
                speed = selected
                player.setPlaybackSpeed(selected)
                panel = HotfixPanel.NONE
            }
            HotfixPanel.DIAGNOSTICS -> HotfixDiagnosticsPanel(
                diagnostics = diagnostics,
                fallbackEvents = fallbackEvents,
                focusRequester = menuFocus,
                largeControls = settings.largePlayerControls,
                highContrast = settings.highContrastPlayerControls,
                onClose = { panel = HotfixPanel.NONE }
            )
            HotfixPanel.NONE -> Unit
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
private fun HotfixSkipPrompt(
    interval: SkipInterval,
    focusRequester: FocusRequester,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .width(if (controlsVisible) 198.dp else 186.dp)
            .height(52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocused(it.isFocused) },
        shape = RoundedCornerShape(7.dp),
        unfocusedBackground = MiruroColors.Accent.copy(alpha = 0.94f),
        focusedBackground = Color.White,
        focusedBorderColor = MiruroColors.AccentSoft,
        unfocusedBorderColor = Color.White.copy(alpha = 0.24f)
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                interval.kind.label,
                color = if (focused) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            Text("›", color = if (focused) Color.Black else Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HotfixPlayerErrorScreen(
    message: String,
    canTryNext: Boolean,
    onRetry: () -> Unit,
    onTryNext: () -> Unit,
    onBack: () -> Unit
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(message) {
        delay(100L)
        runCatching { retryFocus.requestFocus() }
    }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .background(Color(0xFF111111), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Playback source failed", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 15.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HotfixButton("Retry source", 190, false, false, onRetry, Modifier.focusRequester(retryFocus), primary = true)
                if (canTryNext) HotfixButton("Try next source", 210, false, false, onTryNext)
                HotfixButton("Back", 140, false, false, onBack)
            }
        }
    }
}

@Composable
private fun HotfixControls(
    player: Player,
    episode: AnimeEpisode,
    diagnostics: HotfixDiagnostics,
    quality: HotfixQuality,
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
            HotfixButton("Back", 110, largeControls, highContrast, onBack)
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
            HotfixButton("Hide", 110, largeControls, highContrast, onHide)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (largeControls) 22.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HotfixButton("−10s", 120, largeControls, highContrast, onSeekBack)
            HotfixButton(
                if (isPlaying) "Pause" else "Play",
                175,
                largeControls,
                highContrast,
                onPlayPause,
                Modifier.focusRequester(playFocus),
                primary = true
            )
            HotfixButton("+10s", 120, largeControls, highContrast, onSeekForward)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = panelAlpha))
                .padding(if (largeControls) 19.dp else 14.dp)
        ) {
            HotfixSeekBar(position, duration, progress, onSeekFraction, highContrast)
            Spacer(Modifier.height(if (largeControls) 15.dp else 11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                HotfixButton("Quality ${quality.label}", 175, largeControls, highContrast, onQuality)
                HotfixButton("Source ${diagnostics.sourceNumber}", 150, largeControls, highContrast, onSource)
                HotfixButton("Subtitles", 140, largeControls, highContrast, onSubtitle)
                HotfixButton("Speed ${speed}x", 135, largeControls, highContrast, onSpeed)
                HotfixButton("Diagnostics", 150, largeControls, highContrast, onDiagnostics)
                HotfixButton(
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
private fun HotfixButton(
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
            Modifier.fillMaxSize().border(
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
private fun HotfixSeekBar(
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
                Text(hotfixPlayerTime(position), color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(hotfixPlayerTime(duration), color = Color.White, fontSize = 12.sp)
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
private fun HotfixQualityPanel(
    selected: HotfixQuality,
    sources: List<PlaybackSource>,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onSelected: (HotfixQuality) -> Unit
) {
    HotfixPlayerPanel("Video quality") {
        HotfixQuality.entries.forEachIndexed { index, choice ->
            val explicit = hotfixSourceForQuality(sources, choice, null)
            item {
                HotfixPanelRow(
                    text = choice.label,
                    supporting = when {
                        choice == HotfixQuality.AUTO -> "Adaptive quality and automatic source fallback"
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
private fun HotfixSourcePanel(
    sources: List<PlaybackSource>,
    selected: Int,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onSelected: (Int) -> Unit
) {
    HotfixPlayerPanel("Quality & source") {
        itemsIndexed(sources) { index, source ->
            HotfixPanelRow(
                text = "${index + 1}. ${source.label}",
                supporting = "${hotfixProviderName(source)} • ${hotfixQualityLabel(source)} • ${source.type.name}",
                selected = index == selected,
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                large = largeControls,
                highContrast = highContrast
            ) { onSelected(index) }
        }
    }
}

@Composable
private fun HotfixSubtitlePanel(
    source: PlaybackSource,
    selected: HotfixSubtitleChoice,
    automatic: Int,
    textSize: String,
    background: String,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onClose: () -> Unit,
    onTrackSelected: (HotfixSubtitleChoice) -> Unit,
    onSizeSelected: (String) -> Unit,
    onBackgroundSelected: (String) -> Unit
) {
    HotfixPlayerPanel("Subtitles & captions") {
        item {
            Text(
                if (source.subtitleTracks.isEmpty()) {
                    "No provider returned a switchable subtitle track for this episode. Any text visible in the video is baked in and cannot be hidden, resized, or restyled."
                } else {
                    "Switchable tracks returned across this episode's providers are listed here. Text baked into the video cannot be changed."
                },
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 5.dp, end = 5.dp, bottom = 8.dp)
            )
        }
        if (source.subtitleTracks.isEmpty()) {
            item {
                HotfixPanelRow(
                    text = "Back to player",
                    supporting = "Keep the current video source",
                    selected = false,
                    modifier = Modifier.focusRequester(focusRequester),
                    large = largeControls,
                    highContrast = highContrast,
                    onClick = onClose
                )
            }
        } else {
            item { HotfixPanelSection("External track") }
            item {
                HotfixPanelRow(
                    text = "Off",
                    supporting = null,
                    selected = selected is HotfixSubtitleChoice.Off,
                    modifier = Modifier.focusRequester(focusRequester),
                    large = largeControls,
                    highContrast = highContrast
                ) { onTrackSelected(HotfixSubtitleChoice.Off) }
            }
            item {
                HotfixPanelRow(
                    text = "Auto (${source.subtitleTracks.getOrNull(automatic)?.let(::hotfixSubtitleLabel) ?: "preferred language"})",
                    supporting = "Follows the preferred subtitle language",
                    selected = selected is HotfixSubtitleChoice.Auto,
                    modifier = Modifier,
                    large = largeControls,
                    highContrast = highContrast
                ) { onTrackSelected(HotfixSubtitleChoice.Auto) }
            }
            itemsIndexed(source.subtitleTracks) { index, track ->
                val label = hotfixSubtitleLabel(track)
                HotfixPanelRow(
                    text = label,
                    supporting = if (label.contains("SDH") || label.contains("CC")) "Caption track" else track.language,
                    selected = selected is HotfixSubtitleChoice.Track && selected.index == index,
                    modifier = Modifier,
                    large = largeControls,
                    highContrast = highContrast
                ) { onTrackSelected(HotfixSubtitleChoice.Track(index)) }
            }
            item { HotfixPanelSection("Text size") }
            itemsIndexed(listOf("Small", "Medium", "Large", "Extra Large")) { _, option ->
                HotfixPanelRow(option, "Applies to external captions", option == textSize, Modifier, largeControls, highContrast) {
                    onSizeSelected(option)
                }
            }
            item { HotfixPanelSection("Background opacity") }
            itemsIndexed(listOf("Off", "Low", "Medium", "High")) { _, option ->
                HotfixPanelRow(option, "Applies to external captions", option == background, Modifier, largeControls, highContrast) {
                    onBackgroundSelected(option)
                }
            }
        }
    }
}

@Composable
private fun HotfixSpeedPanel(
    selected: Float,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onSelected: (Float) -> Unit
) {
    HotfixPlayerPanel("Playback speed") {
        itemsIndexed(listOf(0.75f, 1f, 1.25f, 1.5f, 2f)) { index, choice ->
            HotfixPanelRow(
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
private fun HotfixDiagnosticsPanel(
    diagnostics: HotfixDiagnostics,
    fallbackEvents: List<String>,
    focusRequester: FocusRequester,
    largeControls: Boolean,
    highContrast: Boolean,
    onClose: () -> Unit
) {
    HotfixPlayerPanel("Playback diagnostics", width = 500) {
        item {
            HotfixPanelRow(
                "Close diagnostics",
                "Live values refresh twice per second",
                false,
                Modifier.focusRequester(focusRequester),
                largeControls,
                highContrast,
                onClose
            )
        }
        item { HotfixDiagnosticLine("Provider", diagnostics.provider) }
        item { HotfixDiagnosticLine("Source", "${diagnostics.sourceNumber} • ${diagnostics.sourceLabel}") }
        item { HotfixDiagnosticLine("Stream", diagnostics.streamType) }
        item { HotfixDiagnosticLine("Quality mode", diagnostics.qualityMode) }
        item { HotfixDiagnosticLine("Playing resolution", diagnostics.actualResolution) }
        item { HotfixDiagnosticLine("Video bitrate", diagnostics.bitrate) }
        item { HotfixDiagnosticLine("Buffer health", "${diagnostics.bufferHealth} • ${diagnostics.bufferedPercent}") }
        item { HotfixDiagnosticLine("Dropped frames", diagnostics.droppedFrames.toString()) }
        item { HotfixDiagnosticLine("State", diagnostics.playbackState) }
        item { HotfixDiagnosticLine("Subtitle", diagnostics.subtitle) }
        item { HotfixDiagnosticLine("Speed", diagnostics.speed) }
        diagnostics.lastError?.let { error -> item { HotfixDiagnosticLine("Last error", error) } }
        if (fallbackEvents.isNotEmpty()) {
            item { HotfixPanelSection("Fallback history") }
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
private fun HotfixPlayerPanel(
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
private fun HotfixPanelRow(
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
            Modifier.fillMaxSize().border(
                if (highContrast && !focused) 1.dp else 0.dp,
                if (highContrast && !focused) Color.White else Color.Transparent,
                RoundedCornerShape(8.dp)
            ).padding(horizontal = 14.dp),
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
private fun HotfixPanelSection(text: String) {
    Text(
        text.uppercase(Locale.ROOT),
        color = MiruroColors.AccentSoft,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 10.dp, start = 5.dp, bottom = 3.dp)
    )
}

@Composable
private fun HotfixDiagnosticLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, modifier = Modifier.width(150.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

private fun hotfixSourceForQuality(
    sources: List<PlaybackSource>,
    mode: HotfixQuality,
    currentProvider: String?
): Int? {
    if (mode == HotfixQuality.AUTO) return null
    val indexed = sources.mapIndexedNotNull { index, source ->
        hotfixQualityHeight(source)?.let { height -> Triple(index, source, height) }
    }
    if (indexed.isEmpty()) return null
    val target = mode.maxHeight ?: return null
    val eligible = indexed.filter { it.third <= target }
    val pool = eligible.ifEmpty { indexed }
    return pool.sortedWith(
        compareByDescending<Triple<Int, PlaybackSource, Int>> {
            currentProvider != null && hotfixProviderName(it.second).equals(currentProvider, ignoreCase = true)
        }.thenBy { kotlin.math.abs(target - it.third) }
            .thenByDescending { it.third }
    ).firstOrNull()?.first
}

private fun hotfixProviderName(source: PlaybackSource): String =
    source.label.substringBefore(' ').takeIf { it.isNotBlank() } ?: "Unknown"

private fun List<SkipInterval>.hotfixActiveAt(positionMs: Long): SkipInterval? = firstOrNull { interval ->
    positionMs >= (interval.startMs - 350L).coerceAtLeast(0L) && positionMs < interval.endMs
}

private fun hotfixQualityHeight(source: PlaybackSource): Int? =
    Regex("""(?i)(2160|1440|1080|720|480|360)p""")
        .find(source.label)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

private fun hotfixQualityLabel(source: PlaybackSource): String =
    hotfixQualityHeight(source)?.let { "${it}p" } ?: when (source.type) {
        PlaybackType.HLS, PlaybackType.DASH -> "Adaptive"
        else -> "Auto"
    }

private fun hotfixSubtitleLabel(track: SubtitleTrack): String {
    val base = track.label.ifBlank { track.language ?: "Subtitle" }
    val lower = base.lowercase(Locale.ROOT)
    return when {
        "sdh" in lower -> base.replace("sdh", "SDH", ignoreCase = true)
        Regex("""(^|\s|\()cc($|\s|\))""", RegexOption.IGNORE_CASE).containsMatchIn(base) ->
            base.replace("cc", "CC", ignoreCase = true)
        else -> base
    }
}

private fun hotfixSubtitleChoiceFromSetting(
    tracks: List<SubtitleTrack>,
    setting: String,
    preferredLanguage: String
): HotfixSubtitleChoice = when {
    setting.equals("Off", ignoreCase = true) -> HotfixSubtitleChoice.Off
    setting.equals("Auto", ignoreCase = true) -> HotfixSubtitleChoice.Auto
    else -> {
        val index = tracks.indexOfFirst { track ->
            track.label.equals(setting, ignoreCase = true) ||
                track.language?.equals(setting, ignoreCase = true) == true ||
                hotfixSubtitleLabel(track).equals(setting, ignoreCase = true)
        }
        if (index >= 0) HotfixSubtitleChoice.Track(index)
        else if (hotfixPreferredSubtitle(tracks, preferredLanguage) >= 0) HotfixSubtitleChoice.Auto
        else HotfixSubtitleChoice.Off
    }
}

private fun hotfixSubtitleIndex(
    tracks: List<SubtitleTrack>,
    choice: HotfixSubtitleChoice,
    preferredLanguage: String
): Int = when (choice) {
    HotfixSubtitleChoice.Off -> -1
    HotfixSubtitleChoice.Auto -> hotfixPreferredSubtitle(tracks, preferredLanguage)
    is HotfixSubtitleChoice.Track -> choice.index.takeIf { it in tracks.indices } ?: -1
}

private fun hotfixPreferredSubtitle(tracks: List<SubtitleTrack>, language: String): Int {
    val preferred = language.lowercase(Locale.ROOT)
    return tracks.indexOfFirst { track ->
        track.language?.lowercase(Locale.ROOT)?.contains(preferred.take(2)) == true ||
            track.label.lowercase(Locale.ROOT).contains(preferred)
    }.takeIf { it >= 0 } ?: -1
}

private fun hotfixCaptionStyle(style: String, background: String): CaptionStyleCompat {
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

private fun hotfixSubtitleSize(value: String): Float = when (value) {
    "Small" -> 15f
    "Large" -> 24f
    "Extra Large" -> 29f
    else -> 19f
}

private fun hotfixMediaItem(source: PlaybackSource, subtitleIndex: Int): MediaItem {
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
                    .setMimeType(hotfixSubtitleMime(track.url))
                    .setLanguage(track.language)
                    .setLabel(track.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
        )
        .build()
}

private fun hotfixSubtitleMime(url: String): String {
    val path = url.substringBefore('?').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

private fun hotfixPlaybackState(state: Int, isPlaying: Boolean): String = when (state) {
    Player.STATE_IDLE -> "Idle"
    Player.STATE_BUFFERING -> "Buffering"
    Player.STATE_READY -> if (isPlaying) "Playing" else "Paused"
    Player.STATE_ENDED -> "Ended"
    else -> "Unknown"
}

private fun hotfixPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
