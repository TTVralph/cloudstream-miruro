package com.ttvralph.miruroapp

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.ttvralph.miruroapp.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repo = AniListRepository()
    private lateinit var store: WatchlistStore
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchlistStore(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24); setBackgroundColor(0xff08080c.toInt()) }
        setContentView(ScrollView(this).apply { addView(root) })
        showHome()
    }

    private fun showHome() { root.removeAllViews(); nav("Home"); message("Loading home rows…"); lifecycleScope.launch { val rows = repo.homeRows(); root.removeAllViews(); nav("Home"); if (rows.isEmpty()) message("Could not load home rows") else rows.forEach { row(it.title, it.items) { showDetails(it.id) } } } }
    private fun showSearch() { root.removeAllViews(); nav("Search"); val input = EditText(this).apply { hint = "Search anime by title"; textSize = 24f; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH; setOnEditorActionListener { _, _, _ -> doSearch(text.toString()); true } }; root.addView(input); input.requestFocus() }
    private fun doSearch(q: String) { message("Searching…"); lifecycleScope.launch { val results = runCatching { repo.search(q) }.getOrDefault(emptyList()); root.removeAllViews(); nav("Search"); if (results.isEmpty()) message("No results found") else grid(results) { showDetails(it.id) } } }
    private fun showFavorites() { root.removeAllViews(); nav("Favorites"); lifecycleScope.launch { val ids = store.favoriteIds.first(); if (ids.isEmpty()) message("No favorites yet") else ids.forEach { id -> button("Anime #$id") { showDetails(id) } } } }
    private fun showSettings() { root.removeAllViews(); nav("Settings"); message("Miruro Anime is a standalone Android TV metadata app using public AniList APIs. No Cloudstream dependency or account login required.") }

    private fun showDetails(id: Int) { root.removeAllViews(); nav("Details"); message("Loading details…"); lifecycleScope.launch { val result = runCatching { repo.details(id) }; root.removeAllViews(); nav("Details"); val d = result.getOrNull() ?: return@launch message("Could not load details"); title(d.title); message(listOfNotNull(d.status, d.year?.toString(), d.rating, d.genres.takeIf { it.isNotEmpty() }?.joinToString()).joinToString(" • ")); message(d.description ?: "No synopsis available"); val favs = store.favoriteIds.first(); button(if (id in favs) "Remove from favorites" else "Add to favorites") { lifecycleScope.launch { store.setFavorite(id, id !in store.favoriteIds.first()); showDetails(id) } }; if (d.seasons.isEmpty()) message("No episodes available") else d.seasons.forEach { season -> title("Season ${season.seasonNumber}: ${season.title}"); val audios = season.episodes.groupBy { it.audioType }; AudioType.values().forEach { audio -> val eps = audios[audio].orEmpty(); if (eps.isNotEmpty()) { title(audio.name); eps.forEach { ep -> button("${ep.episodeNumber}. ${ep.title ?: "Episode ${ep.episodeNumber}"}${ep.runtimeMinutes?.let { " • ${it}m" }.orEmpty()}${ep.releaseDate?.let { " • $it" }.orEmpty()}") {} } } }; if (season.episodes.isEmpty()) message("No episodes available") } } }

    private fun nav(current: String) { title("Miruro Anime - $current"); LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; listOf("Home" to ::showHome, "Search" to ::showSearch, "Favorites" to ::showFavorites, "Settings" to ::showSettings).forEach { (t, f) -> addView(Button(context).apply { text = t; textSize = 18f; isFocusable = true; setOnClickListener { f() } }) }; root.addView(this) } }
    private fun row(label: String, items: List<AnimeItem>, open: (AnimeItem) -> Unit) { title(label); HorizontalScrollView(this).apply { addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; items.forEach { item -> addView(Button(context).apply { text = "${item.title}\n${item.year ?: item.type}"; width = 260; height = 360; textSize = 18f; isFocusable = true; setOnClickListener { open(item) } }) } }); root.addView(this) } }
    private fun grid(items: List<AnimeItem>, open: (AnimeItem) -> Unit) { items.forEach { item -> button("${item.title} (${item.year ?: item.type})") { open(item) } } }
    private fun title(s: String) = root.addView(TextView(this).apply { text = s; textSize = 28f; setTextColor(0xffffffff.toInt()); setPadding(0, 24, 0, 12); gravity = Gravity.START })
    private fun message(s: String) = root.addView(TextView(this).apply { text = s; textSize = 20f; setTextColor(0xffdddddd.toInt()); setPadding(0, 8, 0, 8) })
    private fun button(s: String, click: () -> Unit) = root.addView(Button(this).apply { text = s; textSize = 20f; isFocusable = true; setOnClickListener { click() } })
}
