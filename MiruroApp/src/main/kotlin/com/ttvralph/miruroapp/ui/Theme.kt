package com.ttvralph.miruroapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ttvralph.miruroapp.data.ThemeMode

object MiruroColors {
    val Background = Color(0xFF07090D)
    val Surface = Color(0xFF0D1118)
    val Panel = Color(0xFF12171F)
    val Card = Color(0xFF1F242E)
    val CardHigh = Color(0xFF2B313D)
    val Focused = Color(0xFF333A46)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFF9FB0BD)
    val Subtle = Color(0xFF637380)
    val Accent = Color(0xFFF40612)
    val AccentSoft = Color(0xFFFF2838)
    val Accent2 = Color(0xFFFFD235)
    val Danger = Color(0xFFFFB4AB)
    val Border = Color(0x1AFFFFFF)
}

private val MiruroDarkColorScheme = darkColorScheme(
    primary = MiruroColors.Accent,
    secondary = MiruroColors.Accent2,
    surface = MiruroColors.Card,
    background = MiruroColors.Background,
    onSurface = MiruroColors.Text,
    onBackground = MiruroColors.Text,
    error = MiruroColors.Danger
)

private val MiruroLightColorScheme = lightColorScheme(
    primary = MiruroColors.Accent,
    secondary = MiruroColors.Accent2,
    surface = Color(0xFFFFFBFF),
    background = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    onBackground = Color(0xFF1C1B1F),
    error = Color(0xFFBA1A1A)
)

@Composable
fun MiruroTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) MiruroDarkColorScheme else MiruroLightColorScheme, content = content)
}
