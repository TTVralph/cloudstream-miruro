package com.ttvralph.miruroapp.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeItem

@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    unfocusedBackground: Color = MiruroColors.Card,
    focusedBackground: Color = MiruroColors.Focused,
    content: @Composable (focused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "focusScale")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (focused) focusedBackground else unfocusedBackground, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) MiruroColors.Accent else MiruroColors.Border, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        content(focused)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(8.dp),
        unfocusedBackground = MiruroColors.Accent,
        focusedBackground = Color.White
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (focused) Color.Black else Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, color = if (focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(8.dp),
        unfocusedBackground = Color.White.copy(alpha = 0.08f),
        focusedBackground = Color.White
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (focused) Color.Black else MiruroColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionTitle(text: String, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 26.dp, bottom = 14.dp)) {
        Text(text, color = MiruroColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (badge != null) {
            Spacer(Modifier.width(10.dp))
            Badge(badge)
        }
    }
}

@Composable
fun Badge(text: String, container: Color = MiruroColors.Accent, content: Color = Color.White) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun GenreChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = MiruroColors.Subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text,
        color = MiruroColors.Subtle,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun StateMessage(message: String, color: Color = MiruroColors.Text) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiruroColors.Card, RoundedCornerShape(12.dp))
            .border(1.dp, MiruroColors.Border, RoundedCornerShape(12.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = color, fontSize = 18.sp)
    }
}

@Composable
fun LoadingState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MiruroColors.Accent)
        Spacer(Modifier.height(16.dp))
        Text(message, color = MiruroColors.Subtle, fontSize = 16.sp)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StateMessage(message, color = MiruroColors.Danger)
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Retry", modifier = Modifier.width(180.dp), onClick = onRetry)
    }
}

@Composable
fun RatingLabel(score: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = MiruroColors.Accent2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(score, color = MiruroColors.Accent2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PosterCard(item: AnimeItem, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, modifier = Modifier.width(160.dp).height(240.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(MiruroColors.CardHigh)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(item.year?.toString(), item.type.name).joinToString(" • "),
                    color = MiruroColors.Subtle,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private data class NavEntryData(val icon: ImageVector, val label: String, val key: String, val onClick: () -> Unit)

@Composable
fun SideNav(
    current: String,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onGenres: () -> Unit,
    onSettings: () -> Unit
) {
    var focusedEntry by remember { mutableStateOf<String?>(null) }
    val expanded = focusedEntry != null
    val width by animateDpAsState(if (expanded) 232.dp else 88.dp, label = "navWidth")
    val onEntryFocus: (String, Boolean) -> Unit = { key, focused ->
        if (focused) focusedEntry = key else if (focusedEntry == key) focusedEntry = null
    }
    val entries = listOf(
        NavEntryData(Icons.Filled.Home, "Home", "Home", onHome),
        NavEntryData(Icons.Filled.Search, "Search", "Search", onSearch),
        NavEntryData(Icons.Filled.Favorite, "Library", "Favorites", onLibrary),
        NavEntryData(Icons.Filled.PlayArrow, "Movies", "Movies", onMovies),
        NavEntryData(Icons.Filled.List, "Series", "Series", onSeries),
        NavEntryData(Icons.Filled.Star, "Genres", "Genres", onGenres)
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .background(MiruroColors.Panel.copy(alpha = 0.92f)),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            if (expanded) "Tanji" else "T",
            color = MiruroColors.Accent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 30.dp, top = 28.dp, bottom = 24.dp)
        )
        entries.forEach { entry ->
            NavEntry(entry.icon, entry.label, current == entry.key, expanded, { f -> onEntryFocus(entry.key, f) }, entry.onClick)
        }
        Spacer(Modifier.weight(1f))
        NavEntry(Icons.Filled.Settings, "Settings", current == "Settings", expanded, { f -> onEntryFocus("Settings", f) }, onSettings)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NavEntry(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // Report focus into the rail so it can expand while any entry is focused.
    LaunchedEffect(focused) { onFocusChanged(focused) }
    val tint = when {
        selected -> MiruroColors.Accent
        focused -> Color.White
        else -> MiruroColors.Subtle.copy(alpha = 0.6f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .background(if (selected) MiruroColors.Accent else Color.Transparent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(14.dp))
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                color = tint,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
