package com.ttvralph.miruroapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MiruroColors {
    val Background = Color(0xFF08080A)
    val BackgroundGradientEnd = Color(0xFF0F0F13)
    val Panel = Color(0xFF17171C)
    val Card = Color(0xFF1C1C22)
    val Focused = Color(0xFF2A2A32)
    val Text = Color(0xFFF2F2F5)
    val Subtle = Color(0xFF9B9BA5)
    val Accent = Color(0xFFE63946)
    val Accent2 = Color(0xFFF4B740)
    val Danger = Color(0xFFFF6B6B)
    val Border = Color(0x1FFFFFFF)
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
