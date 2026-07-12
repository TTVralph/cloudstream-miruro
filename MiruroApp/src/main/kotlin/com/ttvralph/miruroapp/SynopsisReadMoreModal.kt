package com.ttvralph.miruroapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.SecondaryButton
import kotlinx.coroutines.launch

internal data class SynopsisModalContent(
    val eyebrow: String,
    val title: String,
    val metadata: String? = null,
    val synopsis: String
)

@Composable
internal fun SynopsisReadMoreModal(
    content: SynopsisModalContent,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val closeFocus = remember(content) { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(content) {
        scrollState.scrollTo(0)
        runCatching { closeFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(860.dp)
                .heightIn(min = 430.dp, max = 650.dp)
                .background(Color(0xFF101010), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        content.eyebrow.uppercase(),
                        color = MiruroColors.AccentSoft,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        content.title,
                        color = Color.White,
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    content.metadata?.takeIf { it.isNotBlank() }?.let { metadata ->
                        Spacer(Modifier.height(5.dp))
                        Text(
                            metadata,
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                SecondaryButton(
                    "Close",
                    Modifier.width(120.dp).focusRequester(closeFocus),
                    onDismiss
                )
            }

            Text(
                "Use Up/Down to scroll",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 12.sp
            )

            FocusableSurface(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (scrollState.value < scrollState.maxValue) {
                                    scope.launch {
                                        scrollState.animateScrollTo(
                                            (scrollState.value + 150).coerceAtMost(scrollState.maxValue)
                                        )
                                    }
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                if (scrollState.value > 0) {
                                    scope.launch {
                                        scrollState.animateScrollTo((scrollState.value - 150).coerceAtLeast(0))
                                    }
                                    true
                                } else false
                            }
                            else -> false
                        }
                    },
                shape = RoundedCornerShape(10.dp),
                unfocusedBackground = Color.White.copy(alpha = 0.04f),
                focusedBackground = Color.White.copy(alpha = 0.09f)
            ) { focused ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(
                            if (focused) 2.dp else 1.dp,
                            if (focused) Color.White else Color.White.copy(alpha = 0.10f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        Text(
                            content.synopsis,
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 18.sp,
                            lineHeight = 27.sp
                        )
                    }
                }
            }
        }
    }
}
