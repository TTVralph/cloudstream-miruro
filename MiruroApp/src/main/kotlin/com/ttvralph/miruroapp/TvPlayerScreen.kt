package com.ttvralph.miruroapp

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.PlaybackType
import com.ttvralph.miruroapp.data.SubtitleTrack
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import java.util.Locale
import kotlinx.coroutines.delay

private const val TV_PLAYER_TAG = "TvPlayerScreen"
private const val TV_SEEK_MS = 10_000L

private enum class TvPlayerPanel { NONE, SOURCES, SUBTITLES, SPEED }

@Composable
fun TvPlayerScreen(
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
    DisposableEffect(episode) { onDispose { viewModel.clearPlayback(episode) } }
    val playback by viewModel.playback.collectAsState()
    val state = playback.stateFor(episode)
    when (val current = state) {
        null, is UiState.Loading -> LoadingState("Resolving stream…")
        is UiState.Error -> ErrorState(current.message, onBack)
        is UiState.Success -> TvVideoPlayer(
            current.data,
            episode,
            nextEpisode,
            viewModel,
            onBack,
            onPlayNext
        )
    }
}

@Composable
private fun TvVideoPlayer(
    initialSource: PlaybackSource,
    episode: AnimeEpisode,
    nextEpisode: AnimeEpisode?,
    viewModel: MiruroViewModel,
    onBack: () -> Unit,
    onPlayNext: (AnimeEpisode) -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val watchProgress by viewModel.watchProgress.collectAsState()
    val savedProgress = watchProgress.firstOrNull {
        it.animeId == episode.anilistId &&
            it.seasonNumber == episode.seasonNumber &&
            it.episodeNumber == episode.episodeNumber &&
            it.audioType == episode.audioType
    }
    val sources = remember(initialSource) {
        listOf(initialSource.copy(fallbackSources = emptyList())) + initialSource.fallbackSources
    }
    var sourceIndex by remember(initialSource) { mutableIntStateOf(0) }
    val activeSource = sources[sourceIndex.coerceIn(0, sources.lastIndex)]
    val autoSubtitle = remember(activeSource, settings.subtitleLanguage) {
        preferredTvSubtitle(activeSource.subtitleTracks, settings.subtitleLanguage)
    }
    val defaultSubtitle = remember(activeSource, settings.subtitleChoice, autoSubtitle) {
        subtitleChoiceIndex(activeSource.subtitleTracks, settings.subtitleChoice, autoSubtitle)
    }
    var subtitleIndex by remember(activeSource, defaultSubtitle) { mutableIntStateOf(defaultSubtitle) }
    var speed by remember(initialSource) { mutableFloatStateOf(1f) }
    var controlsVisible by remember(initialSource) { mutableStateOf(true) }
    var panel by remember(initialSource) { mutableStateOf(TvPlayerPanel.NONE) }
    var playerError by remember(initialSource) { mutableStateOf<String?>(null) }
    var message by remember(initialSource) { mutableStateOf<String?>(null) }
    var ended by remember(initialSource) { mutableStateOf(false) }
    var countdown by remember(initialSource) { mutableIntStateOf(0) }
    var pendingPosition by remember(initialSource) { mutableLongStateOf(0L) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    val player = remember(activeSource, subtitleIndex) {
        val userAgent = activeSource.headers["User-Agent"]
        val headers = activeSource.headers - "User-Agent"
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .apply { if (userAgent != null) setUserAgent(userAgent) }
        val item = playerMediaItem(activeSource, subtitleIndex)
        val mediaSource = when (activeSource.type) {
            PlaybackType.HLS -> HlsMediaSource.Factory(dataSource).createMediaSource(item)
            PlaybackType.DASH -> DashMediaSource.Factory(dataSource).createMediaSource(item)
            else -> ProgressiveMediaSource.Factory(dataSource).createMediaSource(item)
        }
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        ended = true
                        controlsVisible = true
                        panel = TvPlayerPanel.NONE
                        viewModel.setEpisodeWatched(episode, true)
                        countdown = if (settings.autoPlayNext && nextEpisode != null) 5 else 0
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TV_PLAYER_TAG, "Playback failed for ${activeSource.label}", error)
                    if (sourceIndex < sources.lastIndex) {
                        pendingPosition = currentPosition.coerceAtLeast(0L)
                        message = "Source failed. Trying another source…"
                        sourceIndex += 1
                    } else {
                        playerError = "${error.errorCodeName}: ${error.message ?: "Playback failed"}"
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
                ?: savedProgress?.takeIf { settings.resumePlayback && !it.watched && it.positionMs > 10_000L }?.positionMs
            resume?.let { player.seekTo(it) }
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

    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            if (duration > 0L && player.currentPosition > 0L && !ended) {
                viewModel.saveProgress(episode, player.currentPosition, duration)
            }
            delay(2_000L)
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(1_500L)
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
            ended -> nextFocus.requestFocus()
            panel != TvPlayerPanel.NONE -> menuFocus.requestFocus()
            controlsVisible -> playFocus.requestFocus()
            else -> rootFocus.requestFocus()
        }
    }

    BackHandler {
        when {
            panel != TvPlayerPanel.NONE -> panel = TvPlayerPanel.NONE
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
                PrimaryButton("Try another source", Modifier.width(240.dp)) {
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
                    Key.MediaRewind -> { player.seekTo((player.currentPosition - TV_SEEK_MS).coerceAtLeast(0L)); controlsVisible = true; true }
                    Key.MediaFastForward -> { player.seekTo(player.currentPosition + TV_SEEK_MS); controlsVisible = true; true }
                    Key.DirectionLeft -> if (!controlsVisible) {
                        player.seekTo((player.currentPosition - TV_SEEK_MS).coerceAtLeast(0L))
                        controlsVisible = true
                        message = "Rewind 10 seconds"
                        true
                    } else false
                    Key.DirectionRight -> if (!controlsVisible) {
                        player.seekTo(player.currentPosition + TV_SEEK_MS)
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
                view.subtitleView?.setStyle(tvPlayerCaptionStyle(settings.subtitleStyle))
                view.subtitleView?.setFixedTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    if (settings.subtitleStyle == "Large") 24f else 18f
                )
            }
        )

        if (controlsVisible && !ended) {
            TvPlayerControls(
                player = player,
                episode = episode,
                activeSource = activeSource,
                sourceCount = sources.size,
                sourceIndex = sourceIndex,
                speed = speed,
                nextEpisode = nextEpisode,
                playFocus = playFocus,
                onBack = onBack,
                onSeekBack = { player.seekTo((player.currentPosition - TV_SEEK_MS).coerceAtLeast(0L)) },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onSeekForward = { player.seekTo(player.currentPosition + TV_SEEK_MS) },
                onSeekFraction = { fraction ->
                    val duration = player.duration.takeIf { it > 0L } ?: 0L
                    if (duration > 0L) player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
                },
                onSource = { panel = TvPlayerPanel.SOURCES },
                onSubtitle = { panel = TvPlayerPanel.SUBTITLES },
                onSpeed = { panel = TvPlayerPanel.SPEED },
                onNext = { nextEpisode?.let(onPlayNext) },
                onHide = { controlsVisible = false }
            )
        }

        when (panel) {
            TvPlayerPanel.SOURCES -> TvSourcePanel(sources, sourceIndex, menuFocus) { index ->
                pendingPosition = player.currentPosition.coerceAtLeast(0L)
                sourceIndex = index
                panel = TvPlayerPanel.NONE
            }
            TvPlayerPanel.SUBTITLES -> TvSubtitlePanel(activeSource, subtitleIndex, autoSubtitle, menuFocus) { index, choice ->
                pendingPosition = player.currentPosition.coerceAtLeast(0L)
                subtitleIndex = index
                viewModel.updateSubtitleChoice(choice)
                panel = TvPlayerPanel.NONE
            }
            TvPlayerPanel.SPEED -> TvSpeedPanel(speed, menuFocus) { selected ->
                speed = selected
                player.setPlaybackSpeed(selected)
                panel = TvPlayerPanel.NONE
            }
            TvPlayerPanel.NONE -> Unit
        }

        message?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        if (ended) {
            TvPlayerUpNext(
                modifier = Modifier.align(Alignment.Center),
                nextEpisode = nextEpisode,
                countdown = countdown,
                focusRequester = nextFocus,
                onNext = onPlayNext,
                onReplay = {
                    ended = false
                    countdown = 0
                    player.seekTo(0L)
                    player.play()
                    controlsVisible = true
                },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun TvPlayerControls(
    player: Player,
    episode: AnimeEpisode,
    activeSource: PlaybackSource,
    sourceCount: Int,
    sourceIndex: Int,
    speed: Float,
    nextEpisode: AnimeEpisode?,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSource: () -> Unit,
    onSubtitle: () -> Unit,
    onSpeed: () -> Unit,
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
            delay(500L)
        }
    }
    val progress = if (duration > 0L) position.toFloat() / duration.toFloat() else 0f

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)).focusGroup()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton("Back", Modifier.width(110.dp), onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}", color = MiruroColors.AccentSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(episode.title ?: "Episode ${episode.episodeNumber}", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SecondaryButton("Hide", Modifier.width(110.dp), onHide)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton("-10s", Modifier.width(120.dp), onSeekBack)
            PrimaryButton(
                if (isPlaying) "Pause" else "Play",
                Modifier.width(170.dp).focusRequester(playFocus),
                onPlayPause
            )
            SecondaryButton("+10s", Modifier.width(120.dp), onSeekForward)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.86f))
                .padding(16.dp)
        ) {
            TvSeekBar(position, duration, progress, onSeekFraction)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Sources", Modifier.width(130.dp), onSource)
                SecondaryButton("${activeSource.label} (${sourceIndex + 1}/$sourceCount)", Modifier.width(290.dp), onSource)
                SecondaryButton("Subtitles", Modifier.width(145.dp), onSubtitle)
                SecondaryButton("Speed ${speed}x", Modifier.width(145.dp), onSpeed)
                SecondaryButton(
                    if (nextEpisode != null) "Next episode" else "No next episode",
                    Modifier.width(190.dp),
                    if (nextEpisode != null) onNext else onHide
                )
            }
        }
    }
}

@Composable
private fun TvSeekBar(
    position: Long,
    duration: Long,
    progress: Float,
    onSeekFraction: (Float) -> Unit
) {
    var preview by remember(progress) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    FocusableSurface(
        onClick = { onSeekFraction(preview) },
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
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
        unfocusedBackground = Color.White.copy(alpha = 0.04f),
        focusedBackground = Color.White.copy(alpha = 0.16f)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
            Row {
                Text(tvPlayerTime(position), color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(tvPlayerTime(duration), color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { preview },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = MiruroColors.Accent,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun TvPlayerUpNext(
    modifier: Modifier,
    nextEpisode: AnimeEpisode?,
    countdown: Int,
    focusRequester: FocusRequester,
    onNext: (AnimeEpisode) -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier
            .width(620.dp)
            .background(Color.Black.copy(alpha = 0.93f), RoundedCornerShape(18.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("EPISODE COMPLETE", color = MiruroColors.AccentSoft, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        if (nextEpisode != null) {
            Text("Up Next", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Season ${nextEpisode.seasonNumber} • Episode ${nextEpisode.episodeNumber}", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(nextEpisode.title ?: "Episode ${nextEpisode.episodeNumber}", color = MiruroColors.Subtle, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (countdown > 0) Text("Playing automatically in ${countdown}s", color = MiruroColors.AccentSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Play next episode", Modifier.width(250.dp).focusRequester(focusRequester)) { onNext(nextEpisode) }
        } else {
            Text("You reached the latest available episode.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Replay", Modifier.width(150.dp).then(if (nextEpisode == null) Modifier.focusRequester(focusRequester) else Modifier), onReplay)
            SecondaryButton("Back", Modifier.width(150.dp), onBack)
        }
    }
}

@Composable
private fun TvSourcePanel(
    sources: List<PlaybackSource>,
    selected: Int,
    focusRequester: FocusRequester,
    onSelected: (Int) -> Unit
) {
    TvPlayerPanel("Select source") {
        itemsIndexed(sources) { index, source ->
            TvPanelRow(
                "${index + 1}. ${sourceLabel(source)}",
                index == selected,
                if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
            ) { onSelected(index) }
        }
    }
}

@Composable
private fun TvSubtitlePanel(
    source: PlaybackSource,
    selected: Int,
    automatic: Int,
    focusRequester: FocusRequester,
    onSelected: (Int, String) -> Unit
) {
    val choices = buildList {
        add(Triple("Off", -1, "Off"))
        add(Triple("Auto (${source.subtitleTracks.getOrNull(automatic)?.label ?: "preferred language"})", automatic, "Auto"))
        source.subtitleTracks.forEachIndexed { index, track ->
            add(Triple(track.label.ifBlank { track.language ?: "Subtitle ${index + 1}" }, index, track.language ?: track.label))
        }
    }
    TvPlayerPanel("Subtitles") {
        itemsIndexed(choices) { index, choice ->
            TvPanelRow(
                choice.first,
                choice.second == selected,
                if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
            ) { onSelected(choice.second, choice.third) }
        }
    }
}

@Composable
private fun TvSpeedPanel(
    selected: Float,
    focusRequester: FocusRequester,
    onSelected: (Float) -> Unit
) {
    val choices = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    TvPlayerPanel("Playback speed") {
        itemsIndexed(choices) { index, choice ->
            TvPanelRow(
                "${choice}x",
                choice == selected,
                if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
            ) { onSelected(choice) }
        }
    }
}

@Composable
private fun TvPlayerPanel(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .padding(end = 30.dp)
                .width(430.dp)
                .background(Color.Black.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun TvPanelRow(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(8.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.04f),
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                if (selected) "✓ $text" else text,
                color = when {
                    focused -> Color.Black
                    selected -> MiruroColors.AccentSoft
                    else -> Color.White
                },
                fontSize = 15.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun sourceLabel(source: PlaybackSource): String {
    val quality = Regex("""(?i)(2160p|1440p|1080p|720p|480p|360p|4k)""")
        .find(source.label)?.value?.uppercase(Locale.ROOT) ?: source.type.name
    return "${source.label} • $quality"
}

private fun subtitleChoiceIndex(tracks: List<SubtitleTrack>, choice: String, automatic: Int): Int = when {
    choice.equals("Off", ignoreCase = true) -> -1
    choice.equals("Auto", ignoreCase = true) -> automatic
    else -> preferredTvSubtitle(tracks, choice).takeIf { it >= 0 } ?: automatic
}

private fun preferredTvSubtitle(tracks: List<SubtitleTrack>, language: String): Int {
    val preferred = language.lowercase(Locale.ROOT)
    return tracks.indexOfFirst { track ->
        track.language?.lowercase(Locale.ROOT)?.contains(preferred.take(2)) == true ||
            track.label.lowercase(Locale.ROOT).contains(preferred)
    }.takeIf { it >= 0 } ?: -1
}

private fun tvPlayerCaptionStyle(style: String): CaptionStyleCompat = when (style) {
    "High Contrast" -> CaptionStyleCompat(
        AndroidColor.WHITE,
        AndroidColor.BLACK,
        AndroidColor.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        AndroidColor.BLACK,
        null
    )
    else -> CaptionStyleCompat.DEFAULT
}

private fun playerMediaItem(source: PlaybackSource, subtitleIndex: Int): MediaItem {
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
                    .setMimeType(subtitleMime(track.url))
                    .setLanguage(track.language)
                    .setLabel(track.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
        )
        .build()
}

private fun subtitleMime(url: String): String {
    val path = url.substringBefore('?').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

private fun tvPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
