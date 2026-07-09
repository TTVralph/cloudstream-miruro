package com.ttvralph.miruroapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MiruroColors {
    val Background = Color(0xFF090B12)
    val BackgroundGradientEnd = Color(0xFF11152A)
    val Panel = Color(0xFF111827)
    val Card = Color(0xFF182033)
    val Focused = Color(0xFF26395F)
    val Text = Color(0xFFE5EEFC)
    val Subtle = Color(0xFF9AA8BD)
    val Accent = Color(0xFF7DD3FC)
    val Accent2 = Color(0xFFC084FC)
    val Danger = Color(0xFFFFB4A2)
    val Border = Color(0x22FFFFFF)
}

private val MiruroColorScheme = darkColorScheme(
    primary = MiruroColors.Accent,
    secondary = MiruroColors.Accent2,
    surface = MiruroColors.Card,
    background = MiruroColors.Background,
    onSurface = MiruroColors.Text,
    onBackground = MiruroColors.Text,
    error = MiruroColors.Danger
)

@Composable
fun MiruroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MiruroColorScheme, content = content)
}
