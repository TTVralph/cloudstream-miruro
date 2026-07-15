package com.ttvralph.miruroapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object YumeBrand {
    const val Name = "Yume"
    const val Tagline = "Your world of anime."
    const val LibraryLabel = "My Yume"

    val Violet = Color(0xFF9B5CFF)
    val ElectricBlue = Color(0xFF55C8FF)
}

/**
 * Yume's identity is deliberately independent from the active profile color.
 * Profile themes continue to own buttons, focus borders, and progress accents.
 */
@Composable
fun Logo(
    modifier: Modifier = Modifier,
    showTagline: Boolean = false
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YumeMark(Modifier.size(42.dp))
            Spacer(Modifier.width(11.dp))
            Text(
                text = "u",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            Text(
                text = "me",
                color = YumeBrand.ElectricBlue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
        }
        if (showTagline) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = YumeBrand.Tagline,
                color = MiruroColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun YumeMark(modifier: Modifier = Modifier) {
    val gradient = Brush.linearGradient(
        listOf(YumeBrand.Violet, YumeBrand.ElectricBlue)
    )
    Box(
        modifier = modifier
            .semantics { contentDescription = "Yume" }
            .background(Color(0xFF0C0916), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(36.dp)) {
            val diameter = size.minDimension
            val arcStroke = diameter * 0.115f
            drawArc(
                brush = gradient,
                startAngle = -52f,
                sweepAngle = 254f,
                useCenter = false,
                topLeft = Offset(arcStroke, arcStroke),
                size = Size(diameter - arcStroke * 2f, diameter - arcStroke * 2f),
                style = Stroke(width = arcStroke, cap = StrokeCap.Round)
            )

            val yStroke = diameter * 0.09f
            val join = Offset(diameter * 0.53f, diameter * 0.52f)
            drawLine(
                brush = gradient,
                start = Offset(diameter * 0.34f, diameter * 0.30f),
                end = join,
                strokeWidth = yStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                brush = gradient,
                start = Offset(diameter * 0.70f, diameter * 0.28f),
                end = join,
                strokeWidth = yStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                brush = gradient,
                start = join,
                end = Offset(diameter * 0.50f, diameter * 0.73f),
                strokeWidth = yStroke,
                cap = StrokeCap.Round
            )
        }
    }
}
