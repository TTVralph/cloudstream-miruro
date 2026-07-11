package com.ttvralph.miruroapp

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.ui.FocusableSurface

@Composable
internal fun PlayerBButton(
    text: String,
    width: Int,
    large: Boolean,
    highContrast: Boolean,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(width.dp).height(if (large) 58.dp else 48.dp),
        shape = RoundedCornerShape(8.dp),
        unfocusedBackground = if (highContrast) Color.Black else Color.White.copy(alpha = 0.10f),
        focusedBackground = if (highContrast) Color(0xFFFFE45C) else Color.White
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    if (highContrast && !focused) 2.dp else 0.dp,
                    if (highContrast && !focused) Color.White else Color.Transparent,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (focused) Color.Black else Color.White,
                fontSize = if (large) 16.sp else 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
