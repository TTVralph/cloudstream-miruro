package com.ttvralph.miruroapp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
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
import coil.load
import com.ttvralph.miruroapp.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repo = AniListRepository()
    private lateinit var store: WatchlistStore
    private lateinit var root: LinearLayout
    private var screen = Screen.HOME
    private var homeRows: List<HomeRow>? = null
    private val detailsCache = linkedMapOf<Int, AnimeDetails>()
    private var currentDetails: AnimeDetails? = null
    private var currentEpisode: AnimeEpisode? = null
    private var lastQuery = ""
    private var loadingJob: Job? = null
    private var player: ExoPlayer? = null

    private enum class Screen { HOME, SEARCH, FAVORITES, SETTINGS, DETAILS, EPISODE_DETAILS, PLAYER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchlistStore(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(40), dp(28), dp(40), dp(40)); setBackgroundColor(BG) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
        showHome()
    }

    override fun onBackPressed() {
        when (screen) {
            Screen.PLAYER -> { releasePlayer(); currentEpisode?.let { showEpisodeDetails(it) } ?: currentDetails?.let { renderDetails(it) } ?: showHome() }
            Screen.EPISODE_DETAILS -> currentDetails?.let { renderDetails(it) } ?: showHome()
            Screen.DETAILS, Screen.SEARCH, Screen.FAVORITES, Screen.SETTINGS -> showHome()
            Screen.HOME -> super.onBackPressed()
        }
    }

    override fun onStop() { super.onStop(); if (screen == Screen.PLAYER) player?.pause() }
    override fun onDestroy() { releasePlayer(); super.onDestroy() }

    private fun showHome() {
        releasePlayer(); screen = Screen.HOME; loadingJob?.cancel(); root.removeAllViews(); header("Home")
        homeRows?.let { renderHomeRows(it); return }
        state("Loading home rows…")
        loadingJob = lifecycleScope.launch {
            val result = runCatching { repo.homeRows() }
            root.removeAllViews(); header("Home")
            result.onSuccess { rows ->
                homeRows = rows
                if (rows.isEmpty()) errorState("Home rows are empty.") { homeRows = null; showHome() } else renderHomeRows(rows)
            }.onFailure { errorState("Could not load home rows.") { homeRows = null; showHome() } }
        }
    }

    private fun renderHomeRows(rows: List<HomeRow>) { rows.forEach { animeRow(it.title, it.items) } }

    private fun showSearch() { releasePlayer(); screen = Screen.SEARCH; root.removeAllViews(); header("Search"); addSearchInput(lastQuery); if (lastQuery.isBlank()) state("Enter a title to search.") }

    private fun addSearchInput(value: String): EditText {
        val input = EditText(this).apply {
            hint = "Search anime by title"; textSize = 24f; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH
            setText(value); setTextColor(Color.WHITE); setHintTextColor(SUBTLE); setPadding(dp(20), 0, dp(20), 0)
            background = rounded(0xff182034.toInt(), dp(18), 0xff394867.toInt(), dp(2)); isFocusable = true
            setOnEditorActionListener { _, action, event -> if (action == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) { doSearch(text.toString()); true } else false }
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(64)).apply { setMargins(0, dp(20), 0, dp(24)) }); input.requestFocus(); return input
    }

    private fun doSearch(q: String) {
        val query = q.trim(); if (query.isEmpty()) return
        lastQuery = query; loadingJob?.cancel(); root.removeAllViews(); header("Search"); addSearchInput(query); state("Searching…")
        loadingJob = lifecycleScope.launch {
            val result = runCatching { repo.search(query) }
            root.removeAllViews(); header("Search"); addSearchInput(query)
            result.onSuccess { if (it.isEmpty()) state("No results found.") else it.forEach { item -> searchResult(item) } }
                .onFailure { errorState("Search failed. Please try again.") { doSearch(query) } }
        }
    }

    private fun showFavorites() { releasePlayer(); screen = Screen.FAVORITES; root.removeAllViews(); header("Favorites"); state("Loading favorites…"); lifecycleScope.launch { val ids = store.favoriteIds.first(); root.removeAllViews(); header("Favorites"); if (ids.isEmpty()) state("No favorites yet") else ids.forEach { id -> navButton("Anime #$id", "Saved show") { showDetails(id) } } } }
    private fun showSettings() { releasePlayer(); screen = Screen.SETTINGS; root.removeAllViews(); header("Settings"); state("Miruro Anime uses AniList for browsing. Playback only starts when an episode already contains a direct playable URL; protected extraction and provider guessing are not part of this app.") }

    private fun showDetails(id: Int) {
        releasePlayer(); screen = Screen.DETAILS; loadingJob?.cancel(); detailsCache[id]?.let { renderDetails(it); return }
        root.removeAllViews(); header("Details"); state("Loading details…")
        loadingJob = lifecycleScope.launch { val d = runCatching { repo.details(id) }.getOrNull(); root.removeAllViews(); header("Details"); if (d == null) errorState("Could not load details.") { showDetails(id) } else { detailsCache[id] = d; renderDetails(d) } }
    }

    private fun renderDetails(d: AnimeDetails) {
        releasePlayer(); screen = Screen.DETAILS; currentDetails = d; root.removeAllViews(); header("Details")
        d.bannerUrl?.let { root.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; load(it) }, LinearLayout.LayoutParams(-1, dp(220)).apply { setMargins(0, dp(10), 0, dp(18)) }) }
        LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(poster(d.posterUrl), LinearLayout.LayoutParams(dp(220), dp(320)).apply { setMargins(0,0,dp(28),0) }); addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(text(d.title, 34f, Color.WHITE, true)); addView(text(listOfNotNull(d.status, d.year?.toString(), d.rating, d.genres.takeIf { it.isNotEmpty() }?.joinToString()).joinToString(" • "), 18f, ACCENT, false)); addView(text(d.description ?: "No synopsis available", 18f, TEXT, false).apply { maxLines = 7 }); lifecycleScope.launch { val favs = store.favoriteIds.first(); addView(tvButton(if (d.id in favs) "Remove from favorites" else "Add to favorites") { lifecycleScope.launch { store.setFavorite(d.id, d.id !in store.favoriteIds.first()); renderDetails(d) } }, LinearLayout.LayoutParams(dp(280), dp(54)).apply { topMargin = dp(18) }) } }, LinearLayout.LayoutParams(0, -2, 1f)) }.also { root.addView(it) }
        if (d.seasons.isEmpty()) state("No episodes available") else d.seasons.forEach { seasonBlock(it) }
    }

    private fun showEpisodeDetails(ep: AnimeEpisode) {
        releasePlayer(); screen = Screen.EPISODE_DETAILS; currentEpisode = ep; root.removeAllViews(); header("Details")
        title("Season ${ep.seasonNumber} • Episode ${ep.episodeNumber}"); ep.thumbnailUrl?.let { root.addView(poster(it), LinearLayout.LayoutParams(dp(300), dp(170)).apply { bottomMargin = dp(16) }) }
        listOf("Title" to (ep.title ?: "Episode ${ep.episodeNumber}"), "Runtime" to (ep.runtimeMinutes?.let { "${it}m" } ?: "Unknown"), "Release date" to (ep.releaseDate ?: "Unknown"), "Audio" to ep.audioType.name, "Playback" to if (ep.playbackSource != null) "Direct playable URL available" else "No direct playable URL is available for this episode yet.").forEach { (a,b) -> stateLine(a, b) }
        ep.playbackSource?.let { root.addView(tvButton("Play") { showPlayer(ep) }, LinearLayout.LayoutParams(dp(220), dp(58)).apply { topMargin = dp(18); bottomMargin = dp(10) }) }
        root.addView(tvButton("Back to details") { currentDetails?.let { renderDetails(it) } ?: showHome() }, LinearLayout.LayoutParams(dp(260), dp(58)))
    }

    private fun showPlayer(ep: AnimeEpisode) {
        val source = ep.playbackSource ?: return showEpisodeDetails(ep)
        releasePlayer(); screen = Screen.PLAYER; currentEpisode = ep; root.removeAllViews(); state("Loading player…")
        val dataSource = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
        val mediaSource = when (source.type) {
            PlaybackType.HLS -> HlsMediaSource.Factory(dataSource).createMediaSource(mediaItem(source, MimeTypes.APPLICATION_M3U8))
            PlaybackType.DASH -> DashMediaSource.Factory(dataSource).createMediaSource(mediaItem(source, MimeTypes.APPLICATION_MPD))
            else -> ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem(source, null))
        }
        val playerView = PlayerView(this).apply { useController = true; isFocusable = true; requestFocus() }
        val status = text("Loading ${ep.title ?: "Episode ${ep.episodeNumber}"}…", 18f, TEXT, false)
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo; exo.setMediaSource(mediaSource); exo.playWhenReady = true
            exo.addListener(object : Player.Listener { override fun onPlaybackStateChanged(state: Int) { status.text = if (state == Player.STATE_BUFFERING) "Buffering…" else "Playing ${source.label}" }; override fun onPlayerError(error: PlaybackException) { status.text = "Playback failed: ${error.errorCodeName}" } })
            exo.prepare()
        }
        root.removeAllViews(); root.addView(playerView, LinearLayout.LayoutParams(-1, dp(560))); root.addView(status); root.addView(tvButton("Back to details") { onBackPressed() }, LinearLayout.LayoutParams(dp(260), dp(58)).apply { topMargin = dp(18) })
    }

    private fun mediaItem(source: PlaybackSource, mime: String?) = MediaItem.Builder().setUri(source.url).setMimeType(mime).setSubtitleConfigurations(source.subtitleTracks.map { MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(it.url)).setMimeType(MimeTypes.TEXT_VTT).setLanguage(it.language).setLabel(it.label).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build() }).build()
    private fun releasePlayer() { player?.release(); player = null }
    private fun header(current: String) { title("Miruro Anime"); LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; listOf("Search" to ::showSearch, "Favorites" to ::showFavorites, "Settings" to ::showSettings, "Home" to ::showHome).forEach { (t,f) -> addView(tvButton(if (t==current) "• $t" else t) { f() }, LinearLayout.LayoutParams(dp(180), dp(52)).apply { rightMargin = dp(12) }) }; root.addView(this) } }
    private fun animeRow(label: String, items: List<AnimeItem>) { title(label); HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; items.forEach { addView(animeCard(it), LinearLayout.LayoutParams(dp(190), dp(330)).apply { rightMargin = dp(18) }) } }); root.addView(this) } }
    private fun animeCard(item: AnimeItem) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isFocusable = true; background = rounded(CARD, dp(18), 0, 0); setPadding(dp(8),dp(8),dp(8),dp(8)); addView(poster(item.posterUrl), LinearLayout.LayoutParams(-1, dp(250))); addView(text(item.title, 16f, Color.WHITE, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }); addView(text("${item.year ?: ""} ${item.type}", 13f, SUBTLE, false)); setOnClickListener { showDetails(item.id) }; focusFx() }
    private fun searchResult(item: AnimeItem) = root.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isFocusable = true; background = rounded(CARD, dp(16), 0, 0); setPadding(dp(10),dp(10),dp(18),dp(10)); addView(poster(item.posterUrl), LinearLayout.LayoutParams(dp(96), dp(138)).apply { rightMargin=dp(18) }); addView(LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL; addView(text(item.title, 22f, Color.WHITE, true)); addView(text("${item.year ?: ""} • ${item.type}", 16f, SUBTLE, false)) }, LinearLayout.LayoutParams(0,-1,1f)); setOnClickListener { showDetails(item.id) }; focusFx() }, LinearLayout.LayoutParams(-1, dp(162)).apply { setMargins(0,0,0,dp(14)) })
    private fun seasonBlock(season: AnimeSeason) { title("Season ${season.seasonNumber}: ${season.title}"); if (season.episodes.isEmpty()) state("No episodes available") else season.episodes.groupBy { it.audioType }.forEach { (audio, episodes) -> title(audio.name); episodes.forEach { ep -> navButton("${ep.episodeNumber}. ${ep.title ?: "Episode ${ep.episodeNumber}"}", listOfNotNull(ep.runtimeMinutes?.let { "${it}m" }, ep.releaseDate, if (ep.playbackSource != null) "Playable" else "Details").joinToString(" • ")) { if (ep.playbackSource != null) showPlayer(ep) else showEpisodeDetails(ep) } } } }
    private fun navButton(a:String,b:String,click:()->Unit)=root.addView(tvButton("$a\n$b", click), LinearLayout.LayoutParams(-1, dp(74)).apply{bottomMargin=dp(10)})
    private fun poster(url: String?) = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(0xff20283a.toInt(), dp(14), 0, 0); load(url) }
    private fun title(s:String)=root.addView(text(s, if(s=="Miruro Anime") 36f else 24f, Color.WHITE, true).apply{setPadding(0,dp(18),0,dp(10))})
    private fun state(s:String)=root.addView(text(s,20f,TEXT,false).apply{gravity=Gravity.CENTER; setPadding(dp(32),dp(40),dp(32),dp(40))}, LinearLayout.LayoutParams(-1,-2))
    private fun stateLine(a:String,b:String)=root.addView(text("$a: $b",20f,TEXT,false).apply{setPadding(0,dp(6),0,dp(6))})
    private fun errorState(s:String,retry:()->Unit){ state(s); root.addView(tvButton("Retry", retry), LinearLayout.LayoutParams(dp(180), dp(58)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(14) }) }
    private fun text(s:String, size:Float, color:Int, bold:Boolean)=TextView(this).apply{text=s; textSize=size; setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD; setPadding(0,dp(4),0,dp(4))}
    private fun tvButton(s:String, click:()->Unit)=TextView(this).apply{text=s; textSize=18f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; isFocusable=true; isClickable=true; background=rounded(0xff1d2638.toInt(), dp(16), 0, 0); setOnClickListener{click()}; focusFx()}
    private fun View.focusFx()=setOnFocusChangeListener{v,has-> v.animate().scaleX(if(has)1.06f else 1f).scaleY(if(has)1.06f else 1f).setDuration(120).start(); v.background=rounded(if(has)0xff273655.toInt() else CARD, dp(18), if(has) ACCENT else 0, if(has) dp(3) else 0) }
    private fun rounded(color:Int, radius:Int, strokeColor:Int, strokeWidth:Int)=GradientDrawable().apply{setColor(color); cornerRadius=radius.toFloat(); if(strokeWidth>0)setStroke(strokeWidth, strokeColor)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    companion object { private val BG=0xff070a12.toInt(); private val CARD=0xff111827.toInt(); private val TEXT=0xffd9e2f2.toInt(); private val SUBTLE=0xff94a3b8.toInt(); private val ACCENT=0xff7dd3fc.toInt() }
}
