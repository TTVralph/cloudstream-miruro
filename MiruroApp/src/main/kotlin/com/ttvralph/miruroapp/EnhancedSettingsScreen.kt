package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.SettingsStore
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.SecondaryButton
import kotlinx.coroutines.launch

@Composable
fun EnhancedSettingsScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val store = remember(context) { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AuditSettingsScreen(
            viewModel = viewModel,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        LazyColumn(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.94f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Player & accessibility",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Defaults are saved separately for each local profile.",
                    color = MiruroColors.Subtle,
                    fontSize = 12.sp
                )
            }

            item {
                PlayerSettingChoices(
                    title = "Preferred quality",
                    description = "Auto keeps adaptive playback and provider fallback enabled.",
                    options = listOf("Auto", "1080p", "720p", "480p", "Data Saver"),
                    selected = settings.preferredQuality
                ) { value -> scope.launch { store.updatePreferredQuality(value) } }
            }

            item {
                PlayerSettingChoices(
                    title = "Subtitle size",
                    description = "Changes caption text size during playback.",
                    options = listOf("Small", "Medium", "Large", "Extra Large"),
                    selected = settings.subtitleSize
                ) { value -> scope.launch { store.updateSubtitleSize(value) } }
            }

            item {
                PlayerSettingChoices(
                    title = "Subtitle background",
                    description = "Controls the black background behind caption text.",
                    options = listOf("Off", "Low", "Medium", "High"),
                    selected = settings.subtitleBackground
                ) { value -> scope.launch { store.updateSubtitleBackground(value) } }
            }

            item {
                PlayerSettingToggle(
                    title = "Large player controls",
                    description = "Uses taller buttons and larger player text.",
                    enabled = settings.largePlayerControls
                ) { enabled -> scope.launch { store.updateLargePlayerControls(enabled) } }
            }

            item {
                PlayerSettingToggle(
                    title = "High-contrast player",
                    description = "Uses stronger black panels, borders, and a bright focus state.",
                    enabled = settings.highContrastPlayerControls
                ) { enabled -> scope.launch { store.updateHighContrastPlayerControls(enabled) } }
            }

            item {
                PlayerSettingToggle(
                    title = "Large interface text",
                    description = "Enlarges text and controls in Search, Discover, and anime guides.",
                    enabled = settings.largeUiText
                ) { enabled -> scope.launch { store.updateLargeUiText(enabled) } }
            }

            item {
                PlayerSettingToggle(
                    title = "High-contrast interface",
                    description = "Adds brighter borders and stronger panels outside the player.",
                    enabled = settings.highContrastUi
                ) { enabled -> scope.launch { store.updateHighContrastUi(enabled) } }
            }

            item {
                PlayerSettingToggle(
                    title = "Reduce interface motion",
                    description = "Disables focus scaling in the new discovery and search interfaces.",
                    enabled = settings.reducedUiMotion
                ) { enabled -> scope.launch { store.updateReducedUiMotion(enabled) } }
            }

            item {
                PlayerSettingToggle(
                    title = "No-spoiler mode",
                    description = "Hides future episode titles and thumbnails until you reach them.",
                    enabled = settings.noSpoilerMode
                ) { enabled -> features.updateNoSpoilerMode(enabled) }
            }
        }
    }
}

@Composable
private fun PlayerSettingChoices(
    title: String,
    description: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(description, color = MiruroColors.Subtle, fontSize = 11.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(8.dp))
        options.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                row.forEach { option ->
                    FocusableSurface(
                        onClick = { onSelected(option) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(7.dp),
                        unfocusedBackground = if (selected == option) MiruroColors.Accent else Color.White.copy(alpha = 0.06f),
                        focusedBackground = Color.White
                    ) { focused ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (selected == option) "✓ $option" else option,
                                color = if (focused) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PlayerSettingToggle(
    title: String,
    description: String,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(description, color = MiruroColors.Subtle, fontSize = 11.sp, lineHeight = 14.sp)
        }
        SecondaryButton(
            if (enabled) "✓ On" else "Off",
            Modifier.width(105.dp)
        ) { onChanged(!enabled) }
    }
}
