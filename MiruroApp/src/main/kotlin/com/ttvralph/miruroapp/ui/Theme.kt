package com.ttvralph.miruroapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ttvralph.miruroapp.data.ThemeMode

object MiruroColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF050505)
    val Panel = Color(0xFF0A0A0A)
    val Card = Color(0xFF141414)
    val CardHigh = Color(0xFF1B1B1B)
    val Focused = Color(0xFF2A2A2A)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFB3B3B3)
    val Subtle = Color(0xFF808080)
    var Accent = Color(0xFFE50914)
        private set
    var AccentSoft = Color(0xFFFF3340)
        private set
    val Accent2 = Color(0xFFFFD235)
    val Danger = Color(0xFFFFB4AB)
    val Border = Color(0x26FFFFFF)

    fun useProfileTheme(id: String) {
        Accent = profileThemeColor(id)
        AccentSoft = profileThemeSoftColor(id)
    }
}

fun profileThemeColor(id: String): Color = when (id) {
    "orange" -> Color(0xFFFF5A1F)
    "teal" -> Color(0xFF00A896)
    "blue" -> Color(0xFF2478FF)
    "purple" -> Color(0xFF8B5CF6)
    else -> Color(0xFFE50914)
}

fun profileThemeSoftColor(id: String): Color = when (id) {
    "orange" -> Color(0xFFFF7A3D)
    "teal" -> Color(0xFF35D0BA)
    "blue" -> Color(0xFF5FA0FF)
    "purple" -> Color(0xFFAE7BFF)
    else -> Color(0xFFFF3340)
}

@Composable
fun MiruroTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    profileThemeColorId: String = "red",
    content: @Composable () -> Unit
) {
    MiruroColors.useProfileTheme(profileThemeColorId)
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = MiruroColors.Accent,
            secondary = MiruroColors.Accent2,
            surface = MiruroColors.Card,
            background = MiruroColors.Background,
            onSurface = MiruroColors.Text,
            onBackground = MiruroColors.Text,
            error = MiruroColors.Danger
        )
    } else {
        lightColorScheme(
            primary = MiruroColors.Accent,
            secondary = MiruroColors.Accent2,
            surface = Color(0xFFFFFBFF),
            background = Color(0xFFFFFBFF),
            onSurface = Color(0xFF1C1B1F),
            onBackground = Color(0xFF1C1B1F),
            error = Color(0xFFBA1A1A)
        )
    }
    MaterialTheme(colorScheme = colorScheme) {
        ProvideTextStyle(value = YumeBaseTextStyle, content = content)
    }
}
