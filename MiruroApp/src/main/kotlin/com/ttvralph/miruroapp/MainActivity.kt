package com.ttvralph.miruroapp

import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.ttvralph.miruroapp.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), MiruroNavigator {
    private val repo = AniListRepository()
    private lateinit var store: WatchlistStore
    private lateinit var ui: TvUi
    private lateinit var root: LinearLayout
    private var screen = Screen.HOME
    private var homeRows: List<HomeRow>? = null
    private val detailsCache = linkedMapOf<Int, AnimeDetails>()
    private var currentDetails: AnimeDetails? = null
    private var currentEpisode: AnimeEpisode? = null
    private var lastQuery = ""
    private var job: Job? = null
    private var player: ExoPlayer? = null

    private enum class Screen { HOME, SEARCH, FAVORITES, SETTINGS, DETAILS, EPISODE_DETAILS, PLAYER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchlistStore(this); ui = TvUi(this); root = ui.root()
        setContentView(ui.shell(root)); openHome()
    }

    override fun onBackPressed() = when (screen) {
        Screen.PLAYER -> { releasePlayer(); currentEpisode?.let { openEpisode(it) } ?: currentDetails?.let { renderDetails(it) } ?: openHome() }
        Screen.EPISODE_DETAILS -> currentDetails?.let { renderDetails(it) } ?: openHome()
        Screen.DETAILS, Screen.SEARCH, Screen.FAVORITES, Screen.SETTINGS -> openHome()
        Screen.HOME -> super.onBackPressed()
    }

    override fun onStop() { super.onStop(); if (screen == Screen.PLAYER) player?.pause() }
    override fun onDestroy() { releasePlayer(); super.onDestroy() }

    override fun openHome() {
        releasePlayer(); screen = Screen.HOME; job?.cancel(); reset("Home")
        homeRows?.let { HomeScreen(ui, this).render(root, it); return }
        loading("Loading home rows…")
        job = lifecycleScope.launch {
            runCatching { repo.homeRows() }.onSuccess { rows ->
                homeRows = rows; reset("Home")
                if (rows.isEmpty()) error("Home rows are empty.") { homeRows = null; openHome() } else HomeScreen(ui, this@MainActivity).render(root, rows)
            }.onFailure { reset("Home"); homeRows?.let { HomeScreen(ui, this@MainActivity).render(root, it) } ?: error("Could not load home rows.") { openHome() } }
        }
    }

    override fun openSearch() { releasePlayer(); screen = Screen.SEARCH; job?.cancel(); reset("Search"); SearchScreen(ui, this, lastQuery).renderIdle(root) }
    override fun performSearch(query: String) {
        val q = query.trim(); screen = Screen.SEARCH; job?.cancel(); reset("Search")
        if (q.isEmpty()) return SearchScreen(ui, this, "").renderMessage(root, "Enter a title to search.")
        lastQuery = q; SearchScreen(ui, this, q).renderMessage(root, "Searching…")
        job = lifecycleScope.launch {
            runCatching { repo.search(q) }.onSuccess { results -> reset("Search"); SearchScreen(ui, this@MainActivity, q).renderResults(root, results) }
                .onFailure { reset("Search"); SearchScreen(ui, this@MainActivity, q).renderError(root) { performSearch(q) } }
        }
    }

    override fun openFavorites() {
        releasePlayer(); screen = Screen.FAVORITES; job?.cancel(); reset("Favorites"); loading("Loading favorites…")
        job = lifecycleScope.launch { val ids = store.favoriteIds.first(); reset("Favorites"); FavoritesScreen(ui, this@MainActivity).render(root, ids) }
    }

    override fun openSettings() { releasePlayer(); screen = Screen.SETTINGS; job?.cancel(); reset("Settings"); SettingsScreen(ui).render(root) }

    override fun openDetails(id: Int) {
        releasePlayer(); screen = Screen.DETAILS; job?.cancel(); detailsCache[id]?.let { renderDetails(it); return }
        reset("Details"); loading("Loading details…")
        job = lifecycleScope.launch { runCatching { repo.details(id) }.onSuccess { detailsCache[id] = it; renderDetails(it) }.onFailure { reset("Details"); error("Could not load details.") { openDetails(id) } } }
    }

    private fun renderDetails(details: AnimeDetails) { releasePlayer(); currentDetails = details; screen = Screen.DETAILS; reset("Details"); lifecycleScope.launch { DetailsScreen(ui, this@MainActivity, store.favoriteIds.first().contains(details.id)).render(root, details) } }
    override fun toggleFavorite(details: AnimeDetails) { lifecycleScope.launch { store.setFavorite(details.id, !store.favoriteIds.first().contains(details.id)); renderDetails(details) } }
    override fun openEpisode(episode: AnimeEpisode) { releasePlayer(); currentEpisode = episode; screen = Screen.EPISODE_DETAILS; reset("Episode"); EpisodeDetailsScreen(ui, this).render(root, episode) }
    override fun openPlayer(episode: AnimeEpisode) {
        if (episode.sourceCandidates.isEmpty()) return openEpisode(episode)
        releasePlayer(); screen = Screen.PLAYER; currentEpisode = episode; job?.cancel(); root.removeAllViews(); loading("Resolving stream…")
        job = lifecycleScope.launch {
            val source = runCatching { repo.resolveEpisodeSource(episode) }.getOrNull()
            if (source == null) {
                root.removeAllViews(); loading("No playable source found.")
                root.addView(ui.button("Back") { openEpisode(episode) }, LinearLayout.LayoutParams(dp(180), dp(58)).apply { topMargin = dp(16) })
                return@launch
            }
            startPlayback(source)
        }
    }

    private fun startPlayback(source: PlaybackSource) {
        root.removeAllViews()
        val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
        val mediaSource = when (source.type) {
            PlaybackType.HLS -> HlsMediaSource.Factory(factory).createMediaSource(mediaItem(source, MimeTypes.APPLICATION_M3U8))
            PlaybackType.DASH -> DashMediaSource.Factory(factory).createMediaSource(mediaItem(source, MimeTypes.APPLICATION_MPD))
            else -> ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem(source, null))
        }
        root.addView(PlayerView(this).apply { useController = true; isFocusable = true }, LinearLayout.LayoutParams(-1, dp(560)))
        player = ExoPlayer.Builder(this).build().also { exo -> (root.getChildAt(0) as PlayerView).player = exo; exo.setMediaSource(mediaSource); exo.playWhenReady = true; exo.addListener(object : Player.Listener {}); exo.prepare() }
    }

    private fun mediaItem(source: PlaybackSource, mime: String?) = MediaItem.Builder().setUri(source.url).setMimeType(mime).setSubtitleConfigurations(source.subtitleTracks.map { MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(it.url)).setMimeType(MimeTypes.TEXT_VTT).setLanguage(it.language).setLabel(it.label).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build() }).build()
    private fun reset(current: String) { root.removeAllViews(); root.addView(ui.nav(current, ::openHome, ::openSearch, ::openFavorites, ::openSettings)) }
    private fun loading(message: String) = root.addView(ui.state(message), LinearLayout.LayoutParams(-1, dp(180)).apply { topMargin = dp(28) })
    private fun error(message: String, retry: () -> Unit) { loading(message); root.addView(ui.button("Retry", retry), LinearLayout.LayoutParams(dp(180), dp(58)).apply { topMargin = dp(16) }) }
    private fun releasePlayer() { player?.release(); player = null }
}

interface MiruroNavigator {
    fun openHome(); fun openSearch(); fun performSearch(query: String); fun openFavorites(); fun openSettings(); fun openDetails(id: Int)
    fun toggleFavorite(details: AnimeDetails); fun openEpisode(episode: AnimeEpisode); fun openPlayer(episode: AnimeEpisode)
}
