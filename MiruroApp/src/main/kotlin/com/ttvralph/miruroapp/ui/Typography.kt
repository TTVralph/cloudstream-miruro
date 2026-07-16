package com.ttvralph.miruroapp.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Shared text metrics for the ten-foot UI.
 *
 * Android's legacy font padding leaves extra space above and below glyphs. That
 * is useful for old TextViews, but it makes short Compose controls look low or
 * clipped on a television. Keep one explicit, padding-free sans-serif baseline
 * for every screen and use semantic weights instead of the very dense Black
 * weight for small labels.
 */
val YumeBaseTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

object YumeFontWeight {
    val Display = FontWeight.Bold
    val Title = FontWeight.SemiBold
    val Control = FontWeight.SemiBold
    val Eyebrow = FontWeight.Bold
}

@Composable
fun TvControlLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    textAlign: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = YumeBaseTextStyle,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.18f).sp,
        fontWeight = YumeFontWeight.Control,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun TvBadgeLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = YumeBaseTextStyle,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.16f).sp,
        fontWeight = YumeFontWeight.Eyebrow,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
