package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.ui.MiruroColors
import java.util.Locale

@Composable
internal fun ReliableBackdrop(item: AnimeItem, dim: Float) {
    val context = LocalContext.current
    val artwork = item.bannerUrl ?: item.posterUrl
    val imageRequest = remember(artwork) {
        ImageRequest.Builder(context)
            .data(artwork)
            .size(1280, 720)
            .crossfade(false)
            .allowHardware(true)
            .build()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black,
                        Color.Black.copy(alpha = 0.96f),
                        Color.Black.copy(alpha = 0.70f),
                        Color.Black.copy(alpha = 0.22f),
                        Color.Black.copy(alpha = 0.10f)
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.38f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.58f),
                        Color.Black.copy(alpha = 0.96f),
                        Color.Black
                    )
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

@Composable
internal fun ReliableHero(
    item: AnimeItem,
    inList: Boolean,
    alpha: Float,
    playFocus: FocusRequester,
    firstRowFocus: FocusRequester,
    onFocused: () -> Unit,
    onMoveDown: () -> Unit,
    onPlay: () -> Unit,
    onList: () -> Unit
) {
    DisposableEffect(playFocus) {
        ReliableHomeFocusBridge.playRequester = playFocus
        onDispose {
            if (ReliableHomeFocusBridge.playRequester === playFocus) {
                ReliableHomeFocusBridge.playRequester = null
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(start = ReliableSafeX, top = 188.dp)
            .width(560.dp)
            .graphicsLayer { this.alpha = alpha }
            .zIndex(4f)
    ) {
        Text(
            "YUME  •  FEATURED",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            item.title,
            color = Color.White,
            fontSize = 38.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.score?.let {
                Text(
                    "★ $it/100 AniList",
                    color = Color(0xFF46D369),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                listOfNotNull(
                    item.year?.toString(),
                    item.type.name.takeIf { it != "UNKNOWN" }
                        ?.lowercase(Locale.ROOT)
                        ?.replaceFirstChar { character -> character.titlecase(Locale.ROOT) }
                ).joinToString("   "),
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReliableHeroButton(
                text = "▶  Play",
                primary = true,
                modifier = Modifier
                    .width(150.dp)
                    .focusRequester(playFocus)
                    .focusProperties { down = firstRowFocus },
                onFocused = onFocused,
                onDown = onMoveDown,
                onClick = onPlay
            )
            ReliableHeroButton(
                text = if (inList) "✓  My List" else "+  My List",
                primary = false,
                modifier = Modifier
                    .width(170.dp)
                    .focusProperties { down = firstRowFocus },
                onFocused = onFocused,
                onDown = onMoveDown,
                onClick = onList
            )
        }
    }
}

@Composable
internal fun ReliableHeroButton(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onDown: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(52.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onDown()
                    true
                } else {
                    false
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(if (primary || focused) Color.White else Color(0xFF4A4A4A).copy(alpha = 0.88f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (primary || focused) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ReliableHomeRow(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = ReliableSafeX)
        )
        content()
    }
}

@Composable
internal fun ReliableHomeCard(
    item: AnimeItem,
    progress: Float? = null,
    supportingText: String? = null,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale = if (focused) 1.04f else 1f
    val context = LocalContext.current
    val artwork = item.bannerUrl ?: item.posterUrl
    val imageRequest = remember(artwork) {
        ImageRequest.Builder(context)
            .data(artwork)
            .size(456, 256)
            .crossfade(false)
            .allowHardware(true)
            .build()
    }

    var modifier = Modifier
        .width(ReliableCardWidth)
        .height(ReliableCardHeight)

    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }
    if (upFocusRequester != null) {
        modifier = modifier.focusProperties { up = upFocusRequester }
    }

    modifier = modifier
        .onFocusChanged { if (it.hasFocus) onFocused() }
        .onPreviewKeyEvent { event ->
            if (
                upFocusRequester != null &&
                event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionUp
            ) {
                runCatching {
                    upFocusRequester.requestFocus()
                    true
                }.getOrDefault(false)
            } else {
                false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .zIndex(if (focused) 2f else 0f)
        .clip(RoundedCornerShape(ReliableRadius))
        .background(Color(0xFF171717))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)

    Box(modifier) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (focused) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                supportingText?.let {
                    Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                }
            }
        }
        progress?.let {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(it.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(MiruroColors.Accent)
            )
        }
    }
}
