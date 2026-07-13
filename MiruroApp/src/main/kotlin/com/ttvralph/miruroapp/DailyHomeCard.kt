package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOME_CARD_LONG_PRESS_MS = 520L

@Composable
internal fun DailyHomeCard(
    item: AnimeItem,
    progress: Float? = null,
    supportingText: String? = null,
    badge: String? = null,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale = if (focused) 1.04f else 1f
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val artwork = item.bannerUrl ?: item.posterUrl
    val imageRequest = remember(artwork) {
        ImageRequest.Builder(context)
            .data(artwork)
            .size(456, 256)
            .crossfade(false)
            .allowHardware(true)
            .build()
    }
    var confirmHeld by remember(item.id) { mutableStateOf(false) }
    var longPressConsumed by remember(item.id) { mutableStateOf(false) }
    var longPressJob by remember(item.id) { mutableStateOf<Job?>(null) }

    fun resetPress() {
        longPressJob?.cancel()
        longPressJob = null
        confirmHeld = false
        longPressConsumed = false
    }

    DisposableEffect(item.id) {
        onDispose { longPressJob?.cancel() }
    }

    var modifier = Modifier
        .width(ReliableCardWidth)
        .height(ReliableCardHeight)

    if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)
    if (upFocusRequester != null) modifier = modifier.focusProperties { up = upFocusRequester }

    modifier = modifier
        .onFocusChanged {
            if (it.hasFocus) onFocused() else resetPress()
        }
        .onPreviewKeyEvent { event ->
            val confirmKey = event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter
            if (!confirmKey) return@onPreviewKeyEvent false

            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (!confirmHeld) {
                        confirmHeld = true
                        longPressConsumed = false
                        longPressJob?.cancel()
                        longPressJob = scope.launch {
                            delay(HOME_CARD_LONG_PRESS_MS)
                            if (confirmHeld && !longPressConsumed) {
                                longPressConsumed = true
                            }
                        }
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    val showActions = confirmHeld && longPressConsumed
                    val openNormally = confirmHeld && !longPressConsumed
                    longPressJob?.cancel()
                    longPressJob = null
                    confirmHeld = false
                    longPressConsumed = false
                    if (showActions) onLongClick() else if (openNormally) onClick()
                    true
                }
                else -> true
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

        badge?.let { label ->
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (label.startsWith("NEW")) MiruroColors.Accent else Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        if (focused) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.94f)
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
                Text(
                    supportingText ?: "Hold OK for options",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        progress?.let {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            )
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
