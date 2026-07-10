package com.ttvralph.miruroapp

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable

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
