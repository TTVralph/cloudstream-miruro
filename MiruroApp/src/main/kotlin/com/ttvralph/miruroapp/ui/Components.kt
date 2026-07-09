package com.ttvralph.miruroapp.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
    unfocusedBackground: Color = MiruroColors.Card,
    focusedBackground: Color = MiruroColors.Focused,
    content: @Composable (focused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focusScale")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (focused) focusedBackground else unfocusedBackground, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else MiruroColors.Border, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        content(focused)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(4.dp),
        unfocusedBackground = MiruroColors.Accent,
        focusedBackground = Color.White
    ) { focused ->
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = if (focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(4.dp),
        unfocusedBackground = MiruroColors.Card,
        focusedBackground = Color.White
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (focused) Color.Black else MiruroColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionTitle(text: String, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 22.dp, bottom = 12.dp)) {
        Text(text, color = MiruroColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (badge != null) {
            Spacer(Modifier.width(10.dp))
            Badge(badge)
        }
    }
}

@Composable
fun Badge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MiruroColors.Accent, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MiruroColors.Subtle, fontSize = 16.sp, lineHeight = 22.sp, modifier = modifier)
}

@Composable
fun StateMessage(message: String, color: Color = MiruroColors.Text) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MiruroColors.Panel, RoundedCornerShape(8.dp))
            .border(1.dp, MiruroColors.Border, RoundedCornerShape(8.dp))
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
        PrimaryButton("Retry", modifier = Modifier.width(180.dp), onClick = onRetry)
    }
}

@Composable
fun PosterCard(item: AnimeItem, onClick: () -> Unit, rank: Int? = null) {
    FocusableSurface(onClick = onClick, modifier = Modifier.width(168.dp).height(236.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(Color(0xFF1B1E24))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            if (rank != null) {
                Text(
                    "$rank",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp).offset(y = 8.dp)
                )
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(
                    item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
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

@Composable
fun TopNavBar(
    current: String,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TanjiLogo()
        Spacer(Modifier.width(36.dp))
        NavLink("Home", current == "Home", onHome)
        NavLink("Search", current == "Search", onSearch)
        NavLink("My List", current == "Favorites", onFavorites)
        NavLink("Settings", current == "Settings", onSettings)
    }
}

@Composable
private fun TanjiLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MiruroColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Tanji", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NavLink(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(shape)
            .background(if (focused) MiruroColors.Focused else Color.Transparent, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (selected || focused) Color.White else MiruroColors.Subtle,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
    }
}

@Composable
fun RatingLabel(score: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = MiruroColors.Accent2, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(score, color = MiruroColors.Accent2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
