package com.ttvralph.miruroapp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ttvralph.miruroapp.data.AnimeItem

object TvTheme {
    val BG = 0xff090b12.toInt(); val PANEL = 0xff111827.toInt(); val CARD = 0xff182033.toInt(); val FOCUSED = 0xff26395f.toInt()
    val TEXT = 0xffe5eefc.toInt(); val SUBTLE = 0xff9aa8bd.toInt(); val ACCENT = 0xff7dd3fc.toInt(); val ACCENT_2 = 0xffc084fc.toInt(); val DANGER = 0xffffb4a2.toInt()
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

class TvUi(private val context: Context) {
    fun root() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(36), context.dp(22), context.dp(36), context.dp(32))
        background = verticalGradient(TvTheme.BG, 0xff11152a.toInt())
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    fun shell(content: LinearLayout) = ScrollView(context).apply {
        setBackgroundColor(TvTheme.BG)
        isFillViewport = true
        addView(content, FrameLayout.LayoutParams(-1, -2))
    }

    fun nav(current: String, onHome: () -> Unit, onSearch: () -> Unit, onFav: () -> Unit, onSettings: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(0xcc111827.toInt(), context.dp(28), 0x22ffffff, context.dp(1))
        setPadding(context.dp(18), context.dp(12), context.dp(18), context.dp(12))
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("AniTrack", 30f, Color.WHITE, true))
            addView(label("Miruro", 15f, TvTheme.ACCENT, false))
        }, LinearLayout.LayoutParams(0, context.dp(62), 1f))
        listOf("Home" to onHome, "Search" to onSearch, "Watchlist" to onFav, "Settings" to onSettings).forEach { (name, click) ->
            val selected = (name == current) || (name == "Watchlist" && current == "Favorites")
            addView(navPill(if (selected) "● $name" else name, selected, click), LinearLayout.LayoutParams(context.dp(158), context.dp(54)).apply { marginStart = context.dp(10) })
        }
    }

    fun label(text: String, size: Float = 18f, color: Int = TvTheme.TEXT, bold: Boolean = false) = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(color); includeFontPadding = true
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun title(text: String) = label(text, 25f, Color.WHITE, true).apply { setPadding(0, context.dp(28), 0, context.dp(12)) }

    fun body(text: String) = label(text, 18f, TvTheme.TEXT, false).apply { setLineSpacing(0f, 1.12f) }

    fun state(message: String, color: Int = TvTheme.TEXT) = FrameLayout(context).apply {
        background = rounded(TvTheme.PANEL, context.dp(24), 0x1fffffff, context.dp(1))
        addView(label(message, 21f, color, false).apply { gravity = Gravity.CENTER; setPadding(context.dp(20), 0, context.dp(20), 0) }, FrameLayout.LayoutParams(-1, -1))
    }

    fun button(text: String, click: () -> Unit) = TextView(context).apply {
        this.text = text; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true; isClickable = true
        minHeight = context.dp(48); setPadding(context.dp(16), 0, context.dp(16), 0)
        background = rounded(TvTheme.CARD, context.dp(18), 0x22ffffff, context.dp(1)); setOnClickListener { click() }; focusFx()
    }

    fun poster(url: String?, radius: Int = 18) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = rounded(0xff20283a.toInt(), context.dp(radius), 0, 0)
        load(url) { crossfade(true); placeholder(android.R.color.darker_gray); error(android.R.color.darker_gray) }
    }

    fun card(item: AnimeItem, click: () -> Unit) = animeCard(item, click)

    fun row(title: String, items: List<AnimeItem>, click: (AnimeItem) -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isFocusable = false
        addView(this@TvUi.title(title))
        addView(RecyclerView(context).apply {
            id = View.generateViewId()
            isFocusable = false
            clipToPadding = false
            clipChildren = false
            setPadding(context.dp(2), 0, context.dp(18), 0)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = PosterRowAdapter(items, click)
        }, LinearLayout.LayoutParams(-1, context.dp(374)))
    }

    private fun navPill(text: String, selected: Boolean, click: () -> Unit) = TextView(context).apply {
        this.text = text; textSize = 17f; setTextColor(if (selected) Color.WHITE else TvTheme.TEXT); gravity = Gravity.CENTER; isFocusable = true; isClickable = true
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        background = rounded(if (selected) 0xff1f3b57.toInt() else TvTheme.CARD, context.dp(24), if (selected) TvTheme.ACCENT else 0x22ffffff, context.dp(1))
        setOnClickListener { click() }; focusFx()
    }

    private fun animeCard(item: AnimeItem, click: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; isFocusable = true; isFocusableInTouchMode = false; isClickable = true
        background = rounded(TvTheme.CARD, context.dp(22), 0x20ffffff, context.dp(1))
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(10))
        addView(poster(item.posterUrl), LinearLayout.LayoutParams(-1, context.dp(252)))
        addView(label(item.title, 16f, Color.WHITE, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, context.dp(52)))
        addView(label(listOfNotNull(item.year?.toString(), item.type.name).joinToString(" • "), 13f, TvTheme.SUBTLE, false))
        setOnClickListener { click() }; focusFx()
    }

    fun View.focusFx() = setOnFocusChangeListener { v, focused ->
        v.animate().scaleX(if (focused) 1.06f else 1f).scaleY(if (focused) 1.06f else 1f).setDuration(120).start()
        v.background = rounded(if (focused) TvTheme.FOCUSED else TvTheme.CARD, context.dp(22), if (focused) TvTheme.ACCENT else 0x22ffffff, if (focused) context.dp(3) else context.dp(1))
        if (focused) v.bringToFront()
    }

    fun rounded(color: Int, radius: Int, strokeColor: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat(); if (stroke > 0) setStroke(stroke, strokeColor)
    }

    private fun verticalGradient(top: Int, bottom: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))

    private inner class PosterRowAdapter(private val items: List<AnimeItem>, private val click: (AnimeItem) -> Unit) : RecyclerView.Adapter<PosterRowAdapter.Holder>() {
        inner class Holder(cardView: LinearLayout) : RecyclerView.ViewHolder(cardView) {
            val image = cardView.getChildAt(0) as ImageView
            val name = cardView.getChildAt(1) as TextView
            val meta = cardView.getChildAt(2) as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = animeCard(AnimeItem(0, "", null, null, com.ttvralph.miruroapp.data.AnimeType.UNKNOWN, null)) {}
            view.layoutParams = RecyclerView.LayoutParams(context.dp(190), context.dp(352)).apply { marginEnd = context.dp(18) }
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.image.load(item.posterUrl) { crossfade(true); placeholder(android.R.color.darker_gray); error(android.R.color.darker_gray) }
            holder.name.text = item.title
            holder.meta.text = listOfNotNull(item.year?.toString(), item.type.name).joinToString(" • ")
            holder.itemView.contentDescription = item.title
            holder.itemView.setOnClickListener { click(item) }
        }

        override fun getItemCount() = items.size
    }
}
