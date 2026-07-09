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
import coil.load
import com.ttvralph.miruroapp.data.AnimeItem

object TvTheme {
    val BG = 0xff070a12.toInt(); val PANEL = 0xff0d1320.toInt(); val CARD = 0xff141c2b.toInt(); val FOCUSED = 0xff243557.toInt()
    val TEXT = 0xffdbe7f7.toInt(); val SUBTLE = 0xff94a3b8.toInt(); val ACCENT = 0xff7dd3fc.toInt(); val DANGER = 0xffffb4a2.toInt()
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

class TvUi(private val context: Context) {
    fun root() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(38), context.dp(24), context.dp(38), context.dp(36))
        setBackgroundColor(TvTheme.BG)
        isFocusable = false
    }

    fun shell(content: LinearLayout) = ScrollView(context).apply {
        setBackgroundColor(TvTheme.BG)
        isFillViewport = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        addView(content, ScrollView.LayoutParams(-1, -2))
    }

    fun nav(current: String, onHome: () -> Unit, onSearch: () -> Unit, onFav: () -> Unit, onSettings: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(label("Miruro", 32f, Color.WHITE, true), LinearLayout.LayoutParams(0, context.dp(58), 1f))
        listOf("Home" to onHome, "Search" to onSearch, "Favorites" to onFav, "Settings" to onSettings).forEach { (name, click) ->
            addView(button(if (name == current) "● $name" else name, click), LinearLayout.LayoutParams(context.dp(170), context.dp(52)).apply { marginStart = context.dp(10) })
        }
    }

    fun label(text: String, size: Float = 18f, color: Int = TvTheme.TEXT, bold: Boolean = false) = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(color); includeFontPadding = true
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun title(text: String) = label(text, 24f, Color.WHITE, true).apply { setPadding(0, context.dp(26), 0, context.dp(12)) }

    fun body(text: String) = label(text, 18f, TvTheme.TEXT, false).apply { lineSpacingMultiplier = 1.08f }

    fun state(message: String, color: Int = TvTheme.TEXT) = FrameLayout(context).apply {
        background = rounded(TvTheme.PANEL, context.dp(22), 0, 0)
        addView(label(message, 21f, color, false).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
    }

    fun button(text: String, click: () -> Unit) = TextView(context).apply {
        this.text = text; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true; isClickable = true
        minHeight = context.dp(48); setPadding(context.dp(16), 0, context.dp(16), 0)
        background = rounded(TvTheme.CARD, context.dp(18), 0, 0); setOnClickListener { click() }; focusFx()
    }

    fun poster(url: String?, radius: Int = 18) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = rounded(0xff20283a.toInt(), context.dp(radius), 0, 0)
        load(url) { crossfade(true); placeholder(android.R.color.darker_gray); error(android.R.color.darker_gray) }
    }

    fun card(item: AnimeItem, click: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true
        background = rounded(TvTheme.CARD, context.dp(20), 0, 0)
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        addView(poster(item.posterUrl), LinearLayout.LayoutParams(-1, context.dp(252)))
        addView(label(item.title, 16f, Color.WHITE, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, context.dp(52)))
        addView(label(listOfNotNull(item.year?.toString(), item.type.name).joinToString(" • "), 13f, TvTheme.SUBTLE, false))
        setOnClickListener { click() }; focusFx()
    }

    fun row(title: String, items: List<AnimeItem>, click: (AnimeItem) -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; addView(this@TvUi.title(title))
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; items.forEach { addView(card(it) { click(it) }, LinearLayout.LayoutParams(context.dp(190), context.dp(350)).apply { marginEnd = context.dp(18) }) } })
        }, LinearLayout.LayoutParams(-1, context.dp(370)))
    }

    fun View.focusFx() = setOnFocusChangeListener { v, focused ->
        v.animate().scaleX(if (focused) 1.07f else 1f).scaleY(if (focused) 1.07f else 1f).setDuration(120).start()
        v.background = rounded(if (focused) TvTheme.FOCUSED else TvTheme.CARD, context.dp(20), if (focused) TvTheme.ACCENT else 0, if (focused) context.dp(3) else 0)
        if (focused) v.bringToFront()
    }

    fun rounded(color: Int, radius: Int, strokeColor: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat(); if (stroke > 0) setStroke(stroke, strokeColor)
    }
}
