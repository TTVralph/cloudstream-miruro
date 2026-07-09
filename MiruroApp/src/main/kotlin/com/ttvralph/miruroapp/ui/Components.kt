package com.ttvralph.miruroapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    content: @Composable (focused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (focused) MiruroColors.Focused else MiruroColors.Card, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) MiruroColors.Accent else MiruroColors.Border, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        content(focused)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, modifier = modifier.height(52.dp)) { focused ->
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (focused) Color.White else MiruroColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MiruroColors.Text, fontSize = 16.sp, lineHeight = 22.sp, modifier = modifier)
}

@Composable
fun StateMessage(message: String, color: Color = MiruroColors.Text) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MiruroColors.Panel, RoundedCornerShape(20.dp))
            .border(1.dp, MiruroColors.Border, RoundedCornerShape(20.dp))
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
        Text(message, color = MiruroColors.Text, fontSize = 16.sp)
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
fun PosterCard(item: AnimeItem, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, modifier = Modifier.width(150.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF20283A))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(item.year?.toString(), item.type.name).joinToString(" • "),
                color = MiruroColors.Subtle,
                fontSize = 12.sp
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MiruroColors.Panel.copy(alpha = 0.85f), RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("AniTrack", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Miruro", color = MiruroColors.Accent, fontSize = 13.sp)
        }
        NavPill("Home", current == "Home", onHome)
        Spacer(Modifier.width(10.dp))
        NavPill("Search", current == "Search", onSearch)
        Spacer(Modifier.width(10.dp))
        NavPill("Watchlist", current == "Favorites", onFavorites)
        Spacer(Modifier.width(10.dp))
        NavPill("Settings", current == "Settings", onSettings)
    }
}

@Composable
private fun NavPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(24.dp)
    val background = when {
        focused -> MiruroColors.Focused
        selected -> Color(0xFF1F3B57)
        else -> MiruroColors.Card
    }
    val borderColor = if (focused || selected) MiruroColors.Accent else MiruroColors.Border
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(shape)
            .background(background, shape)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (selected) "● $label" else label,
            color = if (selected || focused) Color.White else MiruroColors.Text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp
        )
    }
}
