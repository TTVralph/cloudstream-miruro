package com.ttvralph.miruroapp

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
internal fun <T> Crossfade(
    targetState: T,
    animationSpec: FiniteAnimationSpec<Float>,
    label: String,
    content: @Composable (T) -> Unit
) {
    androidx.compose.animation.Crossfade(
        targetState = targetState,
        animationSpec = animationSpec,
        label = label,
        content = content
    )
}

internal fun Brush.Companion.horizontalGradient(
    colorStops: Array<Pair<Float, Color>>
): Brush = Brush.horizontalGradient(*colorStops)

internal fun Brush.Companion.verticalGradient(
    colorStops: Array<Pair<Float, Color>>
): Brush = Brush.verticalGradient(*colorStops)
