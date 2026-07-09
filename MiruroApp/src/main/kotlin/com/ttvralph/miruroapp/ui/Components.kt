package com.ttvralph.miruroapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
        unfocusedBackground = MiruroColors.Card,
        focusedBackground = Color.White
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (focused) Color.Black else MiruroColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = MiruroColors.Text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 22.dp, bottom = 12.dp)
    )
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
fun PosterCard(item: AnimeItem, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp)) {
        FocusableSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(212.dp)) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(Color(0xFF1B1E24))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            color = MiruroColors.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(item.year?.toString(), item.type.name).joinToString(" • "),
            color = MiruroColors.Subtle,
            fontSize = 12.sp
        )
    }
}

@Composable
fun NavRail(
    current: String,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(96.dp)
            .background(MiruroColors.Panel),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Text("T", color = MiruroColors.Accent, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(36.dp))
        RailItem(Icons.Filled.Home, "Home", current == "Home", onHome)
        RailItem(Icons.Filled.Search, "Search", current == "Search", onSearch)
        RailItem(Icons.Filled.Favorite, "My List", current == "Favorites", onFavorites)
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Filled.Settings, "Settings", current == "Settings", onSettings)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val tint = if (focused || selected) Color.White else MiruroColors.Subtle
    val shape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 6.dp)
            .width(76.dp)
            .clip(shape)
            .background(if (focused) MiruroColors.Focused else Color.Transparent, shape)
            .border(if (selected) 2.dp else 0.dp, MiruroColors.Accent, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}
