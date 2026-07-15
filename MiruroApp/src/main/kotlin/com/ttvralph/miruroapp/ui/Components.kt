package com.ttvralph.miruroapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeItem
import java.util.Locale

@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    unfocusedBackground: Color = MiruroColors.Card,
    focusedBackground: Color = MiruroColors.Focused,
    focusedBorderColor: Color = MiruroColors.Accent,
    unfocusedBorderColor: Color = MiruroColors.Border,
    content: @Composable (focused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "focusScale")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (focused) focusedBackground else unfocusedBackground, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) focusedBorderColor else unfocusedBorderColor, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        content(focused)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, modifier = modifier.height(58.dp), shape = RoundedCornerShape(8.dp), unfocusedBackground = MiruroColors.Accent, focusedBackground = Color.White) { focused ->
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = if (focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    MinimalActionButton(text = text, modifier = modifier, onClick = onClick)
}

/**
 * A TV-first secondary action that stays visually quiet until it receives focus.
 * This keeps screens artwork-led while retaining an unmistakable D-pad focus target.
 */
@Composable
fun MinimalActionButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "minimalActionFocusScale")
    Column(
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(5.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            color = when {
                focused -> Color.Black
                selected -> MiruroColors.AccentSoft
                else -> MiruroColors.Text
            },
            fontSize = 14.sp,
            fontWeight = if (focused || selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(if (focused || selected) 28.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (focused || selected) MiruroColors.Accent else Color.Transparent)
        )
    }
}

@Composable
fun TopBar(
    current: String,
    onHome: () -> Unit,
    onAnime: () -> Unit,
    onMovies: () -> Unit,
    onNewPopular: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(Brush.verticalGradient(listOf(MiruroColors.Background.copy(alpha = 0.98f), MiruroColors.Background.copy(alpha = 0.72f), Color.Transparent)))
            .padding(horizontal = 58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo()
        Spacer(Modifier.width(42.dp))
        NavPill("Home", current == "Home", onHome)
        NavPill("Anime", current == "Anime", onAnime)
        NavPill("Movies", current == "Movies", onMovies)
        NavPill("New & Popular", current == "New & Popular", onNewPopular)
        NavPill("My List", current == "My List", onMyList)
        Spacer(Modifier.weight(1f))
        HeaderIcon(Icons.Filled.Search, "Search", selected = current == "Search", onClick = onSearch)
        Spacer(Modifier.width(14.dp))
        HeaderIcon(Icons.Filled.Settings, "Settings", selected = current == "Settings", onClick = onSettings)
        Spacer(Modifier.width(16.dp))
        AvatarDot()
    }
}

@Composable
private fun NavPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "navFocusScale")
    Column(
        modifier = Modifier
            .height(48.dp)
            .width(if (label.length > 8) 150.dp else 88.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = when {
                focused -> MiruroColors.AccentSoft
                selected -> MiruroColors.Text
                else -> MiruroColors.Muted
            },
            fontSize = 16.sp,
            fontWeight = if (selected || focused) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(if (selected || focused) 34.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (focused) MiruroColors.AccentSoft else if (selected) MiruroColors.Accent else Color.Transparent)
        )
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(999.dp), unfocusedBackground = if (selected) MiruroColors.Accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.04f), focusedBackground = Color.White) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (focused) Color.Black else MiruroColors.Text, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun AvatarDot() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.78f), CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFE8F8FF), Color(0xFF63D8FF), Color(0xFF04283C))))
    )
}

@Composable
fun SectionTitle(text: String, badge: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp)) {
        Text(text, color = MiruroColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.7).sp)
        if (badge != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                badge,
                color = MiruroColors.AccentSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Badge(text: String, container: Color = MiruroColors.Accent, content: Color = Color.White) {
    Box(modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(container, RoundedCornerShape(7.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text.uppercase(Locale.ROOT), color = content, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}

@Composable
fun GenreChip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val chipContent: @Composable (Boolean) -> Unit = { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected || focused) Color.White else MiruroColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
    if (onClick != null) {
        FocusableSurface(onClick = onClick, modifier = Modifier.height(44.dp).width((text.length * 9).coerceIn(70, 150).dp), shape = RoundedCornerShape(999.dp), unfocusedBackground = if (selected) MiruroColors.Accent else Color.White.copy(alpha = 0.045f), focusedBackground = if (selected) MiruroColors.AccentSoft else Color.White.copy(alpha = 0.14f), content = chipContent)
    } else {
        Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) MiruroColors.Accent else Color.White.copy(alpha = 0.045f), RoundedCornerShape(999.dp)).border(1.dp, if (selected) Color.Transparent else MiruroColors.Border, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text, color = if (selected) Color.White else MiruroColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, color = MiruroColors.Muted, fontSize = 17.sp, lineHeight = 25.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun StateMessage(message: String, color: Color = MiruroColors.Text) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            message,
            color = color.copy(alpha = 0.78f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LoadingState(message: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MiruroColors.Accent)
        Spacer(Modifier.height(18.dp))
        Text(message, color = MiruroColors.Muted, fontSize = 16.sp)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StateMessage(message, color = MiruroColors.Danger)
        Spacer(Modifier.height(18.dp))
        SecondaryButton("Retry", modifier = Modifier.width(180.dp), onClick = onRetry)
    }
}

@Composable
fun RatingLabel(score: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = MiruroColors.Accent2, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(score, color = MiruroColors.Accent2, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun PosterCard(
    item: AnimeItem,
    modifier: Modifier = Modifier,
    width: Dp = 166.dp,
    rank: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(width).height(width * 1.48f),
        unfocusedBackground = MiruroColors.Card
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize()) {
            CardImage(item.posterUrl, item.title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.88f)))))
            if (rank != null) SmallBadge(rank, container = if (selected || focused) MiruroColors.Accent else MiruroColors.CardHigh, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(item.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Serif)
                Text(listOfNotNull(item.year?.toString(), item.type.name.takeIf { it != "UNKNOWN" }).joinToString(" • "), color = MiruroColors.Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun LandscapeCard(item: AnimeItem, width: Dp = 340.dp, height: Dp = 190.dp, rank: String? = null, progressPercent: Float? = null, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, modifier = Modifier.width(width).height(height), unfocusedBackground = MiruroColors.Card) {
        Box(Modifier.fillMaxSize()) {
            CardImage(item.bannerUrl ?: item.posterUrl, item.title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.86f)))))
            rank?.let { Text(it, color = if (it == "1") MiruroColors.Accent else Color.White.copy(alpha = 0.25f), fontSize = 76.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 4.dp)) }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = if (rank == null) 18.dp else 72.dp, end = 16.dp, bottom = 16.dp)) {
                Text(item.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Serif)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.score?.let {
                        RatingLabel(String.format(Locale.US, "%.1f", it / 10f))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(listOfNotNull(item.year?.toString(), item.type.name.takeIf { t -> t != "UNKNOWN" }).joinToString(" • "), color = MiruroColors.Muted, fontSize = 13.sp)
                }
            }
            progressPercent?.let { percent ->
                LinearProgressIndicator(progress = { percent.coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp), color = MiruroColors.Accent, trackColor = Color.White.copy(alpha = 0.22f))
            }
        }
    }
}

@Composable
private fun CardImage(url: String?, contentDescription: String?) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(MiruroColors.CardHigh))
    } else {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF101826), Color(0xFF3C0922), Color(0xFF07090D))))) {
            Box(Modifier.align(Alignment.Center).size(92.dp).clip(CircleShape).background(MiruroColors.Accent.copy(alpha = 0.22f)))
            Box(Modifier.align(Alignment.Center).width(58.dp).fillMaxHeight(0.58f).clip(RoundedCornerShape(48.dp, 48.dp, 12.dp, 12.dp)).background(Color.Black.copy(alpha = 0.42f)))
        }
    }
}

@Composable
private fun SmallBadge(text: String, container: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(container, RoundedCornerShape(7.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text.uppercase(Locale.ROOT), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}
