package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AppSettings
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.MiruroColors

@Composable
internal fun DiscoveryChoice(
    text: String,
    selected: Boolean,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(if (settings.largeUiText) 50.dp else 44.dp),
        shape = shape,
        unfocusedBackground = when {
            selected -> MiruroColors.Accent
            settings.highContrastUi -> Color.Black
            else -> Color.White.copy(alpha = 0.07f)
        },
        focusedBackground = Color.White
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (settings.highContrastUi && !focused) {
                        Modifier.border(2.dp, Color.White.copy(alpha = 0.75f), shape)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (selected) "✓ $text" else text,
                color = if (focused) Color.Black else Color.White,
                fontSize = (if (settings.largeUiText) 15 else 13).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 13.dp)
            )
        }
    }
}

@Composable
internal fun DiscoveryMediaCard(
    item: AnimeItem,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    width: Dp = 180.dp,
    height: Dp = 255.dp,
    subtitle: String? = null,
    badge: String? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(width).height(height),
        shape = shape,
        unfocusedBackground = if (settings.highContrastUi) Color.Black else Color(0xFF171717),
        focusedBackground = Color.White
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val value = if (focused && !settings.reducedUiMotion) 1.025f else 1f
                    scaleX = value
                    scaleY = value
                }
                .clip(shape)
                .then(
                    if (settings.highContrastUi || focused) {
                        Modifier.border(
                            if (focused) 3.dp else 2.dp,
                            if (focused) MiruroColors.AccentSoft else Color.White.copy(alpha = 0.72f),
                            shape
                        )
                    } else Modifier
                )
        ) {
            AsyncImage(
                model = item.posterUrl ?: item.bannerUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.96f))
                        )
                    )
            )
            badge?.let { value ->
                Text(
                    value,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(MiruroColors.Accent, RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(11.dp)
            ) {
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = (if (settings.largeUiText) 16 else 14).sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = (if (settings.largeUiText) 12 else 10).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiscoverySectionHeading(
    title: String,
    settings: AppSettings,
    eyebrow: String? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = (if (settings.largeUiText) 28 else 24).sp,
            fontWeight = FontWeight.Black
        )
        eyebrow?.let {
            Text(
                it,
                color = MiruroColors.AccentSoft,
                fontSize = (if (settings.largeUiText) 13 else 11).sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
internal fun DiscoveryInfoTile(
    label: String,
    value: String,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(
                if (settings.highContrastUi) Color.Black else Color.White.copy(alpha = 0.055f),
                RoundedCornerShape(10.dp)
            )
            .then(
                if (settings.highContrastUi) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                } else Modifier
            )
            .padding(12.dp)
    ) {
        Text(
            label.uppercase(),
            color = MiruroColors.AccentSoft,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = Color.White,
            fontSize = (if (settings.largeUiText) 16 else 14).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
