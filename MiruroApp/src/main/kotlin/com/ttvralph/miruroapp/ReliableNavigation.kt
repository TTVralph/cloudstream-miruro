package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.TvControlLabel
import com.ttvralph.miruroapp.ui.YumeBrand

internal val ReliableSafeX = 52.dp
internal val ReliableNavHeight = 76.dp
internal val ReliableCardWidth = 228.dp
internal val ReliableCardHeight = 128.dp
internal val ReliableRadius = 5.dp

internal data class ReliableResumeItem(val anime: AnimeItem, val progress: WatchProgress)

internal object ReliableHomeFocusBridge {
    var playRequester: FocusRequester? = null

    fun requestPlay(): Boolean = runCatching {
        val requester = playRequester ?: return@runCatching false
        requester.requestFocus()
        true
    }.getOrDefault(false)
}

@Composable
fun ReliableTopBar(
    current: String,
    profileName: String,
    profileAvatarId: String,
    onHome: () -> Unit,
    onAnime: () -> Unit,
    onMovies: () -> Unit,
    onDiscover: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onMoveDown = if (current == "Home") {
        { ReliableHomeFocusBridge.requestPlay() }
    } else {
        null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ReliableNavHeight)
            .background(Color.Black.copy(alpha = 0.97f))
            .padding(horizontal = ReliableSafeX),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo()
        Spacer(Modifier.width(30.dp))
        ReliableNavText("Home", current == "Home", onHome, onDown = onMoveDown)
        ReliableNavText("Anime", current == "Anime", onAnime, onDown = onMoveDown)
        ReliableNavText("Movies", current == "Movies", onMovies, onDown = onMoveDown)
        ReliableNavText("Discover", current == "Discover", onDiscover, 104.dp, onMoveDown)
        ReliableNavText(YumeBrand.LibraryLabel, current == YumeBrand.LibraryLabel, onMyList, 112.dp, onMoveDown)
        Spacer(Modifier.weight(1f))
        ReliableNavIcon(Icons.Filled.Search, "Search", current == "Search", onSearch, onMoveDown)
        Spacer(Modifier.width(10.dp))
        ReliableNavIcon(Icons.Filled.Settings, "Settings", current == "Settings", onSettings, onMoveDown)
        Spacer(Modifier.width(10.dp))
        ReliableProfileButton(profileName, profileAvatarId, current == "Profiles", onProfiles, onMoveDown)
    }
}

@Composable
private fun ReliableProfileButton(
    profileName: String,
    profileAvatarId: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDown: (() -> Boolean)? = null
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .onPreviewKeyEvent { event ->
                if (
                    onDown != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown
                ) {
                    onDown()
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent else MiruroColors.Accent.copy(alpha = 0.34f),
        focusedBackground = Color.White
    ) { focused ->
        ProfileAvatarArtwork(
            name = profileName,
            avatarId = profileAvatarId,
            modifier = Modifier.fillMaxSize().padding(if (focused) 4.dp else 2.dp),
            focused = focused
        )
    }
}

@Composable
private fun ReliableNavText(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    width: Dp = 82.dp,
    onDown: (() -> Boolean)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .width(width)
            .height(46.dp)
            .onPreviewKeyEvent { event ->
                if (
                    onDown != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown
                ) {
                    onDown()
                } else {
                    false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        TvControlLabel(
            text = label,
            color = if (focused || selected) Color.White else Color.White.copy(alpha = 0.68f),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 4.dp)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .width(if (selected) 26.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) MiruroColors.Accent else Color.Transparent)
        )
    }
}

@Composable
private fun ReliableNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDown: (() -> Boolean)? = null
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .onPreviewKeyEvent { event ->
                if (
                    onDown != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown
                ) {
                    onDown()
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent.copy(alpha = 0.35f) else Color.Transparent,
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (focused) Color.Black else Color.White)
        }
    }
}
