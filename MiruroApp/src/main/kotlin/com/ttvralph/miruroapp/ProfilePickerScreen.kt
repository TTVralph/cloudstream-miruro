package com.ttvralph.miruroapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.LocalProfile
import com.ttvralph.miruroapp.data.PROFILE_AVATAR_IDS
import com.ttvralph.miruroapp.data.ProfileState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import kotlinx.coroutines.delay

@Composable
fun ProfilePickerScreen(
    state: ProfileState,
    onSelect: (LocalProfile) -> Unit,
    onCreate: (String, String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        ProfileEditorOverlay(
            profile = null,
            suggestedName = "Profile ${state.profiles.size + 1}",
            onCancel = { creating = false },
            onSave = { name, avatar ->
                onCreate(name, avatar)
                creating = false
            }
        )
        return
    }

    val initialFocus = remember { FocusRequester() }
    val focusIndex = state.profiles.indexOfFirst { it.id == state.activeId }.coerceAtLeast(0)
    LaunchedEffect(state.profiles.map { it.id }, state.activeId) {
        delay(120L)
        runCatching { initialFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Logo()
        Spacer(Modifier.height(34.dp))
        Text("Who's watching?", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(30.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(horizontal = 70.dp, vertical = 12.dp)
        ) {
            itemsIndexed(state.profiles, key = { _, profile -> profile.id }) { index, profile ->
                ProfileTile(
                    profile = profile,
                    active = profile.id == state.activeId,
                    modifier = if (index == focusIndex) Modifier.focusRequester(initialFocus) else Modifier,
                    onClick = { onSelect(profile) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Add profile", Modifier.width(190.dp)) { creating = true }
        Spacer(Modifier.height(8.dp))
        Text(
            "Names and avatars can be changed from My AniStream › Profiles.",
            color = MiruroColors.Subtle,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ProfileTile(
    profile: LocalProfile,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusableSurface(
            onClick = onClick,
            modifier = modifier.size(150.dp),
            shape = RoundedCornerShape(18.dp),
            unfocusedBackground = if (active) MiruroColors.Accent.copy(alpha = 0.34f) else MiruroColors.Card,
            focusedBackground = Color.White
        ) { focused ->
            ProfileAvatarArtwork(
                name = profile.name,
                avatarId = profile.avatarId,
                modifier = Modifier.fillMaxSize().padding(if (focused) 5.dp else 2.dp),
                focused = focused
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (active) "${profile.name} • Active" else profile.name,
            color = if (active) MiruroColors.AccentSoft else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ProfileEditorOverlay(
    profile: LocalProfile?,
    suggestedName: String,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit
) {
    BackHandler(onBack = onCancel)
    var name by remember(profile?.id) { mutableStateOf(profile?.name ?: suggestedName) }
    var avatarId by remember(profile?.id) { mutableStateOf(profile?.avatarId ?: PROFILE_AVATAR_IDS.first()) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(profile?.id) {
        delay(100L)
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 90.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (profile == null) "Create profile" else "Edit profile",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .width(690.dp)
                .height(54.dp)
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                name.ifBlank { "Enter a profile name" },
                color = if (name.isBlank()) MiruroColors.Subtle else Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("Choose an avatar", color = MiruroColors.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.width(690.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            itemsIndexed(PROFILE_AVATAR_IDS, key = { _, id -> id }) { index, id ->
                FocusableSurface(
                    onClick = { avatarId = id },
                    modifier = Modifier
                        .size(94.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    shape = RoundedCornerShape(14.dp),
                    unfocusedBackground = if (id == avatarId) MiruroColors.Accent.copy(alpha = 0.44f) else MiruroColors.Card,
                    focusedBackground = Color.White
                ) { focused ->
                    ProfileAvatarArtwork(
                        name = name,
                        avatarId = id,
                        modifier = Modifier.fillMaxSize().padding(if (focused || id == avatarId) 5.dp else 2.dp),
                        focused = focused
                    )
                }
            }
        }
        Text(profileAvatarLabel(avatarId), color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        ProfileNameKeyboard(
            onCharacter = { character -> if (name.length < 24) name += character },
            onBackspace = { if (name.isNotEmpty()) name = name.dropLast(1) },
            onSpace = { if (name.isNotBlank() && !name.endsWith(' ') && name.length < 24) name += " " },
            onClear = { name = "" }
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Cancel", Modifier.width(150.dp), onCancel)
            PrimaryButton(
                if (profile == null) "Create profile" else "Save profile",
                Modifier.width(220.dp)
            ) { onSave(name.trim().ifBlank { suggestedName }, avatarId) }
        }
    }
}

@Composable
private fun ProfileNameKeyboard(
    onCharacter: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onClear: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf("ABCDEFGHI", "JKLMNOPQR", "STUVWXYZ").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { character ->
                    ProfileKeyboardKey(character.toString(), 64.dp) { onCharacter(character.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileKeyboardKey("⌫", 90.dp, onBackspace)
            ProfileKeyboardKey("Space", 170.dp, onSpace)
            ProfileKeyboardKey("Clear", 120.dp, onClear)
        }
    }
}

@Composable
private fun ProfileKeyboardKey(text: String, width: Dp, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(width).height(44.dp),
        shape = RoundedCornerShape(7.dp),
        unfocusedBackground = Color.White.copy(alpha = 0.08f),
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = if (focused) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
internal fun ProfileAvatarArtwork(
    name: String,
    avatarId: String,
    modifier: Modifier = Modifier,
    focused: Boolean = false
) {
    val colors = profileAvatarColors(avatarId)
    Box(
        modifier = modifier.background(Brush.linearGradient(colors), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase().ifBlank { "?" },
            color = Color.White,
            fontSize = if (focused) 52.sp else 46.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            profileAvatarMark(avatarId),
            color = Color.White.copy(alpha = 0.74f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp)
        )
    }
}

private fun profileAvatarColors(id: String): List<Color> = when (id) {
    "ocean" -> listOf(Color(0xFF0067A8), Color(0xFF00A9C7))
    "violet" -> listOf(Color(0xFF5E35B1), Color(0xFFAB47BC))
    "sunset" -> listOf(Color(0xFFE65100), Color(0xFFFFB300))
    "forest" -> listOf(Color(0xFF1B5E20), Color(0xFF43A047))
    "gold" -> listOf(Color(0xFF8D6E00), Color(0xFFFFC107))
    else -> listOf(Color(0xFFB00020), Color(0xFFFF334F))
}

private fun profileAvatarLabel(id: String): String = when (id) {
    "ocean" -> "Ocean"
    "violet" -> "Violet"
    "sunset" -> "Sunset"
    "forest" -> "Forest"
    "gold" -> "Gold"
    else -> "Crimson"
}

private fun profileAvatarMark(id: String): String = when (id) {
    "ocean" -> "●"
    "violet" -> "◆"
    "sunset" -> "▲"
    "forest" -> "✦"
    "gold" -> "★"
    else -> "▶"
}
