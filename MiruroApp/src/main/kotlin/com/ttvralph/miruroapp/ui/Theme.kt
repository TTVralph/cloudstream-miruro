package com.ttvralph.miruroapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MiruroColors {
    val Background = Color(0xFF050505)
    val Surface = Color(0xFF131313)
    val Panel = Color(0xFF0E0E0E)        // surface-container-lowest, sidebar/cards base
    val Card = Color(0xFF1C1B1B)         // surface-container-low
    val CardHigh = Color(0xFF2A2A2A)     // surface-container-high
    val Focused = Color(0xFF353534)      // surface-variant
    val Text = Color(0xFFE5E2E1)         // on-background
    val Subtle = Color(0xFFE3BDC2)       // on-surface-variant (warm pink-grey)
    val Accent = Color(0xFFD81E5B)       // primary-container crimson
    val AccentSoft = Color(0xFFFFB2BD)   // primary (soft pink, for small labels)
    val Accent2 = Color(0xFFE9C349)      // secondary gold
    val Danger = Color(0xFFFFB4AB)
    val Border = Color(0x1AFFFFFF)       // white @ 10%
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
