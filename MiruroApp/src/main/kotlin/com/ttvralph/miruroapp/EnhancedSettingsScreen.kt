package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.SecondaryButton

@Composable
fun EnhancedSettingsScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel
) {
    val settings by viewModel.settings.collectAsState()

    Box(Modifier.fillMaxSize()) {
        AuditSettingsScreen(viewModel)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(330.dp)
                .background(Color.Black.copy(alpha = 0.90f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                "No-spoiler mode",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Hides future episode titles and thumbnails until you reach them.",
                color = MiruroColors.Subtle,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                if (settings.noSpoilerMode) "✓ On" else "Off",
                Modifier.width(300.dp)
            ) { features.updateNoSpoilerMode(!settings.noSpoilerMode) }
        }
    }
}
