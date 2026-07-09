package com.ttvralph.miruroapp

import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.*
import coil.load
import com.ttvralph.miruroapp.data.*

class HomeScreen(private val ui: TvUi, private val nav: MiruroNavigator) {
    fun render(root: LinearLayout, rows: List<HomeRow>) {
        rows.firstOrNull()?.items?.firstOrNull()?.let { hero(root, it) }
        rows.forEach { root.addView(ui.row(it.title, it.items) { nav.openDetails(it.id) }) }
    }
    private fun hero(root: LinearLayout, item: AnimeItem) = FrameLayout(root.context).apply {
        background = ui.rounded(TvTheme.PANEL, root.context.dp(28), 0, 0)
        addView(ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = .42f; load(item.bannerUrl ?: item.posterUrl) { crossfade(true) } }, FrameLayout.LayoutParams(-1, -1))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM; setPadding(context.dp(34), context.dp(28), context.dp(34), context.dp(30))
            addView(ui.label(item.title, 38f, Color.WHITE, true)); addView(ui.label(listOfNotNull(item.year?.toString(), item.type.name, "Trending").joinToString(" • "), 18f, TvTheme.ACCENT, false))
            addView(ui.body("Discover anime, search AniList metadata, manage your watchlist, and jump into episode playback from one polished AniTrack-style UI."))
            addView(ui.button("View Details") { nav.openDetails(item.id) }, LinearLayout.LayoutParams(context.dp(210), context.dp(58)).apply { topMargin = context.dp(18) })
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(this, LinearLayout.LayoutParams(-1, root.context.dp(330)).apply { topMargin = root.context.dp(24) })
    }
}

class SearchScreen(private val ui: TvUi, private val nav: MiruroNavigator, private val query: String) {
    fun renderIdle(root: LinearLayout) = renderMessage(root, if (query.isBlank()) "Enter a title to search." else "Press search to run this query again.")
    fun renderMessage(root: LinearLayout, message: String) { input(root); root.addView(ui.state(message), LinearLayout.LayoutParams(-1, root.context.dp(170)).apply { topMargin = root.context.dp(20) }) }
    fun renderError(root: LinearLayout, retry: () -> Unit) { input(root); root.addView(ui.state("Search failed. Please try again.", TvTheme.DANGER), LinearLayout.LayoutParams(-1, root.context.dp(150)).apply { topMargin = root.context.dp(20) }); root.addView(ui.button("Retry", retry), LinearLayout.LayoutParams(root.context.dp(180), root.context.dp(58)).apply { topMargin = root.context.dp(16) }) }
    fun renderResults(root: LinearLayout, results: List<AnimeItem>) { input(root); if (results.isEmpty()) return root.addView(ui.state("No results found."), LinearLayout.LayoutParams(-1, root.context.dp(160)).apply { topMargin = root.context.dp(20) }); root.addView(ui.row("Results", results) { nav.openDetails(it.id) }) }
    private fun input(root: LinearLayout) = EditText(root.context).apply {
        hint = "Search anime by title"; setText(query); textSize = 24f; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH
        setTextColor(Color.WHITE); setHintTextColor(TvTheme.SUBTLE); isFocusable = true; setPadding(context.dp(22), 0, context.dp(22), 0)
        background = ui.rounded(TvTheme.CARD, context.dp(18), TvTheme.ACCENT, context.dp(1))
        setOnEditorActionListener { _, action, event -> if (action == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) { nav.performSearch(text.toString()); true } else false }
        root.addView(this, LinearLayout.LayoutParams(-1, context.dp(66)).apply { topMargin = context.dp(24) }); requestFocus(); setSelection(text.length)
    }
}

class DetailsScreen(private val ui: TvUi, private val nav: MiruroNavigator, private val favorite: Boolean) {
    fun render(root: LinearLayout, d: AnimeDetails) {
        root.addView(LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, root.context.dp(26), 0, 0)
            addView(ui.poster(d.posterUrl), LinearLayout.LayoutParams(root.context.dp(230), root.context.dp(335)).apply { marginEnd = root.context.dp(30) })
            addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL
                addView(ui.label(d.title, 36f, Color.WHITE, true)); addView(ui.label(listOfNotNull(d.year?.toString(), d.status, d.rating, d.genres.takeIf { it.isNotEmpty() }?.joinToString()).joinToString(" • "), 17f, TvTheme.ACCENT, false))
                addView(ui.body(d.description ?: "No synopsis available.").apply { maxLines = 7 })
                addView(ui.button(if (favorite) "Remove Favorite" else "Add Favorite") { nav.toggleFavorite(d) }, LinearLayout.LayoutParams(context.dp(250), context.dp(58)).apply { topMargin = context.dp(18) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        if (d.seasons.isEmpty()) root.addView(ui.state("No episodes available."), LinearLayout.LayoutParams(-1, root.context.dp(150)).apply { topMargin = root.context.dp(22) }) else d.seasons.forEach { season(root, it) }
    }
    private fun season(root: LinearLayout, s: AnimeSeason) { root.addView(ui.title("Season ${s.seasonNumber}: ${s.title}")); root.addView(LinearLayout(root.context).apply { orientation = LinearLayout.HORIZONTAL; addView(ui.button("SUB") {}, LinearLayout.LayoutParams(root.context.dp(120), root.context.dp(48)).apply { marginEnd = root.context.dp(10) }); addView(ui.button("DUB") {}, LinearLayout.LayoutParams(root.context.dp(120), root.context.dp(48))) }); s.episodes.forEach { ep -> root.addView(ui.button("${ep.episodeNumber}. ${ep.title ?: "Episode ${ep.episodeNumber}"}    ${listOfNotNull(ep.runtimeMinutes?.let { "${it}m" }, ep.releaseDate, if (ep.sourceCandidates.isNotEmpty()) "Playable" else "Details").joinToString(" • ")}") { if (ep.sourceCandidates.isNotEmpty()) nav.openPlayer(ep) else nav.openEpisode(ep) }, LinearLayout.LayoutParams(-1, root.context.dp(66)).apply { topMargin = root.context.dp(10) }) } }
}

class EpisodeDetailsScreen(private val ui: TvUi, private val nav: MiruroNavigator) { fun render(root: LinearLayout, ep: AnimeEpisode) { ep.thumbnailUrl?.let { root.addView(ui.poster(it), LinearLayout.LayoutParams(root.context.dp(360), root.context.dp(210)).apply { topMargin = root.context.dp(24) }) }; root.addView(ui.title("Season ${ep.seasonNumber} • Episode ${ep.episodeNumber}")); listOf("Title" to (ep.title ?: "Episode ${ep.episodeNumber}"), "Runtime" to (ep.runtimeMinutes?.let { "${it}m" } ?: "Unknown"), "Release date" to (ep.releaseDate ?: "Unknown"), "Audio type" to ep.audioType.name, "Playback" to if (ep.sourceCandidates.isNotEmpty()) "Playable source available" else "No playable source is available for this episode.").forEach { root.addView(ui.body("${it.first}: ${it.second}")) }; if (ep.sourceCandidates.isNotEmpty()) root.addView(ui.button("Play") { nav.openPlayer(ep) }, LinearLayout.LayoutParams(root.context.dp(180), root.context.dp(58)).apply { topMargin = root.context.dp(20) }) } }
class FavoritesScreen(private val ui: TvUi, private val nav: MiruroNavigator) { fun render(root: LinearLayout, ids: Set<Int>) { if (ids.isEmpty()) root.addView(ui.state("No favorites yet."), LinearLayout.LayoutParams(-1, root.context.dp(160)).apply { topMargin = root.context.dp(24) }) else ids.forEach { id -> root.addView(ui.button("Anime #$id") { nav.openDetails(id) }, LinearLayout.LayoutParams(-1, root.context.dp(64)).apply { topMargin = root.context.dp(12) }) } } }
class SettingsScreen(private val ui: TvUi) { fun render(root: LinearLayout) { root.addView(ui.state("AniTrack UI has been adapted for Miruro: AniList discovery, search, details, watchlist, and Media3 playback are presented with the same app-style navigation and poster-row layout. Stream sources are resolved from Miruro on demand when you press Play."), LinearLayout.LayoutParams(-1, root.context.dp(230)).apply { topMargin = root.context.dp(24) }) } }
