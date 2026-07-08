package com.ttvralph.miruroapp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.ttvralph.miruroapp.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repo = AniListRepository()
    private lateinit var store: WatchlistStore
    private lateinit var root: LinearLayout
    private var screen = Screen.HOME

    private enum class Screen { HOME, SEARCH, FAVORITES, SETTINGS, DETAILS, PLAYER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchlistStore(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(28), dp(40), dp(40))
            setBackgroundColor(BG)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
        showHome()
    }

    override fun onBackPressed() {
        if (screen == Screen.HOME) super.onBackPressed() else showHome()
    }

    private fun showHome() {
        screen = Screen.HOME; root.removeAllViews(); header("Home"); state("Loading home rows…")
        lifecycleScope.launch {
            val rows = runCatching { repo.homeRows() }.getOrDefault(emptyList())
            root.removeAllViews(); header("Home")
            if (rows.isEmpty()) state("Could not load home rows") else rows.forEach { animeRow(it.title, it.items) }
        }
    }

    private fun showSearch() {
        screen = Screen.SEARCH; root.removeAllViews(); header("Search")
        val input = EditText(this).apply {
            hint = "Search anime by title"; textSize = 24f; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.WHITE); setHintTextColor(SUBTLE); setPadding(dp(20), 0, dp(20), 0)
            background = rounded(0xff182034.toInt(), dp(18), 0xff394867.toInt(), dp(2))
            setOnEditorActionListener { _, _, _ -> doSearch(text.toString()); true }
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(64)).apply { setMargins(0, dp(20), 0, dp(24)) })
        input.requestFocus()
    }

    private fun doSearch(q: String) {
        root.removeViews(2.coerceAtMost(root.childCount), (root.childCount - 2).coerceAtLeast(0)); state("Searching…")
        lifecycleScope.launch {
            val results = runCatching { repo.search(q) }.getOrDefault(emptyList())
            root.removeAllViews(); header("Search")
            if (results.isEmpty()) state("No results found") else results.forEach { searchResult(it) }
        }
    }

    private fun showFavorites() {
        screen = Screen.FAVORITES; root.removeAllViews(); header("Favorites"); state("Loading favorites…")
        lifecycleScope.launch {
            val ids = store.favoriteIds.first(); root.removeAllViews(); header("Favorites")
            if (ids.isEmpty()) state("No favorites yet") else ids.forEach { id -> navButton("Anime #$id", "Saved show") { showDetails(id) } }
        }
    }

    private fun showSettings() {
        screen = Screen.SETTINGS; root.removeAllViews(); header("Settings")
        state("Miruro Anime uses AniList for browsing and Miruro-backed HLS/DASH sources for episode playback. If a provider is missing, playback automatically tries equivalent episodes from other providers.")
    }

    private fun showDetails(id: Int) {
        screen = Screen.DETAILS; root.removeAllViews(); header("Details"); state("Loading details…")
        lifecycleScope.launch {
            val d = runCatching { repo.details(id) }.getOrNull(); root.removeAllViews(); header("Details")
            if (d == null) return@launch state("Could not load details")
            d.bannerUrl?.let { root.addView(ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; load(it) }, LinearLayout.LayoutParams(-1, dp(220)).apply { setMargins(0, dp(10), 0, dp(18)) }) }
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(poster(d.posterUrl), LinearLayout.LayoutParams(dp(220), dp(320)).apply { setMargins(0,0,dp(28),0) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(d.title, 34f, Color.WHITE, true))
                    addView(text(listOfNotNull(d.status, d.year?.toString(), d.rating, d.genres.takeIf { it.isNotEmpty() }?.joinToString()).joinToString(" • "), 18f, ACCENT, false))
                    addView(text(d.description ?: "No synopsis available", 18f, TEXT, false).apply { maxLines = 7 })
                    val favs = store.favoriteIds.first()
                    addView(tvButton(if (id in favs) "Remove from favorites" else "Add to favorites") { lifecycleScope.launch { store.setFavorite(id, id !in store.favoriteIds.first()); showDetails(id) } }, LinearLayout.LayoutParams(dp(260), dp(54)).apply { topMargin = dp(18) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }.also { root.addView(it) }
            if (d.seasons.isEmpty()) state("No episodes available") else d.seasons.forEach { season -> seasonBlock(season) }
        }
    }

    private fun header(current: String) { title("Miruro Anime"); LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; listOf("Search" to ::showSearch, "Favorites" to ::showFavorites, "Settings" to ::showSettings, "Home" to ::showHome).forEach { (t,f) -> addView(tvButton(if (t==current) "• $t" else t) { f() }, LinearLayout.LayoutParams(dp(180), dp(52)).apply { rightMargin = dp(12) }) } ; root.addView(this) } }
    private fun animeRow(label: String, items: List<AnimeItem>) { title(label); HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; items.forEach { addView(animeCard(it), LinearLayout.LayoutParams(dp(190), dp(330)).apply { rightMargin = dp(18) }) } }); root.addView(this) } }
    private fun animeCard(item: AnimeItem) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isFocusable = true; background = rounded(CARD, dp(18), 0, 0); setPadding(dp(8),dp(8),dp(8),dp(8)); addView(poster(item.posterUrl), LinearLayout.LayoutParams(-1, dp(250))); addView(text(item.title, 16f, Color.WHITE, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }); addView(text("${item.year ?: ""} ${item.type}", 13f, SUBTLE, false)); setOnClickListener { showDetails(item.id) }; focusFx() }
    private fun searchResult(item: AnimeItem) = root.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isFocusable = true; background = rounded(CARD, dp(16), 0, 0); setPadding(dp(10),dp(10),dp(18),dp(10)); addView(poster(item.posterUrl), LinearLayout.LayoutParams(dp(96), dp(138)).apply { rightMargin=dp(18) }); addView(LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL; addView(text(item.title, 22f, Color.WHITE, true)); addView(text("${item.year ?: ""} • ${item.type}", 16f, SUBTLE, false)) }, LinearLayout.LayoutParams(0,-1,1f)); setOnClickListener { showDetails(item.id) }; focusFx() }, LinearLayout.LayoutParams(-1, dp(162)).apply { setMargins(0,0,0,dp(14)) })
    private fun seasonBlock(season: AnimeSeason) { title("Season ${season.seasonNumber}: ${season.title}"); LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; AudioType.values().forEach { addView(tvButton(it.name) {}, LinearLayout.LayoutParams(dp(120), dp(48)).apply { rightMargin = dp(10) }) }; root.addView(this) }; if (season.episodes.isEmpty()) state("No episodes available") else season.episodes.groupBy { it.audioType }.forEach { (audio, episodes) -> title(audio.name); episodes.forEach { ep -> navButton("${ep.episodeNumber}. ${ep.title ?: "Episode ${ep.episodeNumber}"}", listOfNotNull(ep.runtimeMinutes?.let { "${it}m" }, ep.releaseDate, ep.playback?.provider?.takeIf { it.isNotBlank() }?.uppercase()).joinToString(" • ")) { playEpisode(ep) } } } }

    private fun playEpisode(ep: AnimeEpisode) {
        val playback = ep.playback ?: return Toast.makeText(this, "Episode source missing", Toast.LENGTH_LONG).show()
        screen = Screen.PLAYER; root.removeAllViews(); header("Details"); state("Loading stream for episode ${ep.episodeNumber}…")
        lifecycleScope.launch {
            val source = runCatching { repo.playableStream(playback) }.getOrNull()
            root.removeAllViews()
            if (source == null) { header("Details"); state("No playable stream found for episode ${ep.episodeNumber}"); return@launch }
            val video = VideoView(this@MainActivity).apply {
                val headers = source.referer?.let { mapOf("Referer" to it, "Origin" to "https://www.miruro.to") }
                if (headers == null) setVideoURI(Uri.parse(source.url)) else setVideoURI(Uri.parse(source.url), headers)
                setOnPreparedListener { player: MediaPlayer -> player.start() }
                setOnErrorListener { _, _, _ -> Toast.makeText(this@MainActivity, "Playback failed", Toast.LENGTH_LONG).show(); true }
                setMediaController(MediaController(this@MainActivity).also { it.setAnchorView(this) })
                requestFocus()
            }
            root.addView(video, LinearLayout.LayoutParams(-1, dp(520)))
            root.addView(text("Playing ${ep.title ?: "Episode ${ep.episodeNumber}"} • ${source.label}", 18f, TEXT, false))
        }
    }
    private fun navButton(a:String,b:String,click:()->Unit)=root.addView(tvButton("$a\n$b", click), LinearLayout.LayoutParams(-1, dp(68)).apply{bottomMargin=dp(10)})
    private fun poster(url: String?) = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(0xff20283a.toInt(), dp(14), 0, 0); load(url) }
    private fun title(s:String)=root.addView(text(s, if(s=="Miruro Anime") 36f else 24f, Color.WHITE, true).apply{setPadding(0,dp(18),0,dp(10))})
    private fun state(s:String)=root.addView(text(s,20f,TEXT,false).apply{gravity=Gravity.CENTER; setPadding(dp(32),dp(80),dp(32),dp(80))}, LinearLayout.LayoutParams(-1,-2))
    private fun text(s:String, size:Float, color:Int, bold:Boolean)=TextView(this).apply{text=s; textSize=size; setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD; setPadding(0,dp(4),0,dp(4))}
    private fun tvButton(s:String, click:()->Unit)=TextView(this).apply{text=s; textSize=18f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; isFocusable=true; background=rounded(0xff1d2638.toInt(), dp(16), 0, 0); setOnClickListener{click()}; focusFx()}
    private fun View.focusFx()=setOnFocusChangeListener{v,has-> v.animate().scaleX(if(has)1.06f else 1f).scaleY(if(has)1.06f else 1f).setDuration(120).start(); v.background=rounded(if(has)0xff273655.toInt() else CARD, dp(18), if(has) ACCENT else 0, if(has) dp(3) else 0) }
    private fun rounded(color:Int, radius:Int, strokeColor:Int, strokeWidth:Int)=GradientDrawable().apply{setColor(color); cornerRadius=radius.toFloat(); if(strokeWidth>0)setStroke(strokeWidth, strokeColor)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    companion object { private val BG=0xff070a12.toInt(); private val CARD=0xff111827.toInt(); private val TEXT=0xffd9e2f2.toInt(); private val SUBTLE=0xff94a3b8.toInt(); private val ACCENT=0xff7dd3fc.toInt() }
}
