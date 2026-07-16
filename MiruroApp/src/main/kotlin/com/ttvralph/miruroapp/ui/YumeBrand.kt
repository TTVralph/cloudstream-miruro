package com.ttvralph.miruroapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ttvralph.miruroapp.R

object YumeBrand {
    const val Name = "Yume"
    const val Tagline = "Dream. Stream. Escape."
    const val LibraryLabel = "My Yume"
}

/**
 * Uses the exact raster crops from the approved Yume identity board. The
 * branding remains independent from the active profile theme, which continues
 * to own buttons, focus borders, and progress accents.
 */
@Composable
fun Logo(
    modifier: Modifier = Modifier,
    showTagline: Boolean = false
) {
    val image = if (showTagline) {
        R.drawable.yume_lockup_exact
    } else {
        R.drawable.yume_wordmark_exact
    }
    val width = if (showTagline) 198.dp else 112.dp
    val height = if (showTagline) 110.dp else 46.dp

    Image(
        painter = painterResource(image),
        contentDescription = if (showTagline) {
            "${YumeBrand.Name}. ${YumeBrand.Tagline}"
        } else {
            YumeBrand.Name
        },
        modifier = modifier.size(width, height),
        contentScale = ContentScale.Fit
    )
}
