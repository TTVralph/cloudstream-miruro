package com.ttvralph.miruroapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.LocalProfile
import com.ttvralph.miruroapp.data.PROFILE_AVATAR_IDS
import com.ttvralph.miruroapp.data.PROFILE_THEME_COLOR_IDS
import com.ttvralph.miruroapp.data.ProfileState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.profileThemeColor
import com.ttvralph.miruroapp.ui.profileThemeSoftColor
import kotlinx.coroutines.delay

@Composable
fun ProfilePickerScreen(
    state: ProfileState,
    onSelect: (LocalProfile) -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        ProfileEditorOverlay(
            profile = null,
            suggestedName = "Profile ${state.profiles.size + 1}",
            onCancel = { creating = false },
            onSave = { name, avatar, themeColor ->
                onCreate(name, avatar, themeColor)
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
        Spacer(Modifier.height(24.dp))
        Text("Who's watching?", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text("Choose a profile to continue", color = MiruroColors.Muted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(horizontal = 74.dp, vertical = 12.dp)
        ) {
            itemsIndexed(state.profiles, key = { _, profile -> profile.id }) { index, profile ->
                ProfileTile(
                    profile = profile,
                    active = profile.id == state.activeId,
                    modifier = if (index == focusIndex) Modifier.focusRequester(initialFocus) else Modifier,
                    onClick = { onSelect(profile) }
                )
            }
            item(key = "add_profile") {
                AddProfileTile { creating = true }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Names, avatars, and colors can be changed from My AniStream › Profiles.",
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
    val profileColor = profileThemeColor(profile.themeColorId)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusableSurface(
            onClick = onClick,
            modifier = modifier.size(144.dp),
            shape = RoundedCornerShape(999.dp),
            unfocusedBackground = if (active) profileColor.copy(alpha = 0.35f) else Color.Transparent,
            focusedBackground = Color.White,
            focusedBorderColor = profileColor,
            unfocusedBorderColor = if (active) profileColor else Color.White.copy(alpha = 0.18f)
        ) { focused ->
            ProfileAvatarArtwork(
                name = profile.name,
                avatarId = profile.avatarId,
                modifier = Modifier.fillMaxSize().padding(if (focused) 5.dp else 3.dp),
                focused = focused
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (active) "${profile.name} • Active" else profile.name,
            color = if (active) profileThemeSoftColor(profile.themeColorId) else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.size(144.dp),
            shape = RoundedCornerShape(999.dp),
            unfocusedBackground = Color.White.copy(alpha = 0.04f),
            focusedBackground = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.20f)
        ) { focused ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+", color = if (focused) Color.Black else MiruroColors.Muted, fontSize = 62.sp, fontWeight = FontWeight.Light)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Add profile", color = MiruroColors.Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ProfileEditorOverlay(
    profile: LocalProfile?,
    suggestedName: String,
    onCancel: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    BackHandler(onBack = onCancel)
    var name by remember(profile?.id) { mutableStateOf(profile?.name ?: suggestedName) }
    var avatarId by remember(profile?.id) { mutableStateOf(profile?.avatarId ?: PROFILE_AVATAR_IDS.first()) }
    var themeColorId by remember(profile?.id) { mutableStateOf(profile?.themeColorId ?: "red") }
    val firstFocus = remember { FocusRequester() }
    val initialAvatar = remember(profile?.id) { profile?.avatarId ?: PROFILE_AVATAR_IDS.first() }

    LaunchedEffect(profile?.id) {
        delay(100L)
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 76.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (profile == null) "Create profile" else "Edit profile",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.width(1010.dp).height(210.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(190.dp)) {
                Box(
                    Modifier
                        .size(174.dp)
                        .clip(CircleShape)
                        .background(profileThemeColor(themeColorId))
                        .padding(5.dp)
                ) {
                    ProfileAvatarArtwork(name, avatarId, Modifier.fillMaxSize())
                }
                Text(profileAvatarLabel(avatarId), color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Profile name", color = MiruroColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        name.ifBlank { "Enter a profile name" },
                        color = if (name.isBlank()) MiruroColors.Subtle else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Avatar", color = MiruroColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp)
                ) {
                    itemsIndexed(PROFILE_AVATAR_IDS, key = { _, id -> id }) { _, id ->
                        FocusableSurface(
                            onClick = { avatarId = id },
                            modifier = Modifier
                                .size(69.dp)
                                .then(if (id == initialAvatar) Modifier.focusRequester(firstFocus) else Modifier),
                            shape = RoundedCornerShape(999.dp),
                            unfocusedBackground = if (id == avatarId) profileThemeColor(themeColorId).copy(alpha = 0.45f) else Color.Transparent,
                            focusedBackground = Color.White,
                            focusedBorderColor = profileThemeColor(themeColorId),
                            unfocusedBorderColor = if (id == avatarId) profileThemeColor(themeColorId) else Color.White.copy(alpha = 0.14f)
                        ) { focused ->
                            ProfileAvatarArtwork(
                                name = name,
                                avatarId = id,
                                modifier = Modifier.fillMaxSize().padding(if (focused || id == avatarId) 4.dp else 2.dp),
                                focused = focused
                            )
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text("Theme color", color = MiruroColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PROFILE_THEME_COLOR_IDS.forEach { id ->
                        val color = profileThemeColor(id)
                        FocusableSurface(
                            onClick = { themeColorId = id },
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(999.dp),
                            unfocusedBackground = Color.Transparent,
                            focusedBackground = Color.White,
                            focusedBorderColor = color,
                            unfocusedBorderColor = if (themeColorId == id) color else Color.Transparent
                        ) { focused ->
                            Box(
                                modifier = Modifier.fillMaxSize().padding(if (focused) 5.dp else 4.dp).clip(CircleShape).background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                if (themeColorId == id) {
                                    Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ProfileNameKeyboard(
            onCharacter = { character -> if (name.length < 24) name += character },
            onBackspace = { if (name.isNotEmpty()) name = name.dropLast(1) },
            onSpace = { if (name.isNotBlank() && !name.endsWith(' ') && name.length < 24) name += " " },
            onClear = { name = "" }
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Cancel", Modifier.width(150.dp), onCancel)
            PrimaryButton(
                if (profile == null) "Create profile" else "Save profile",
                Modifier.width(220.dp)
            ) { onSave(name.trim().ifBlank { suggestedName }, avatarId, themeColorId) }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("ABCDEFGHI", "JKLMNOPQR", "STUVWXYZ").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { character ->
                    ProfileKeyboardKey(character.toString(), 58.dp) { onCharacter(character.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ProfileKeyboardKey("⌫", 86.dp, onBackspace)
            ProfileKeyboardKey("Space", 160.dp, onSpace)
            ProfileKeyboardKey("Clear", 110.dp, onClear)
        }
    }
}

@Composable
private fun ProfileKeyboardKey(text: String, width: Dp, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(width).height(38.dp),
        shape = RoundedCornerShape(6.dp),
        unfocusedBackground = Color.White.copy(alpha = 0.07f),
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = if (focused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

private data class AvatarPalette(
    val background: List<Color>,
    val skin: Color,
    val hair: Color,
    val shirt: Color,
    val iris: Color
)

@Composable
internal fun ProfileAvatarArtwork(
    name: String,
    avatarId: String,
    modifier: Modifier = Modifier,
    focused: Boolean = false
) {
    val palette = profileAvatarPalette(avatarId)
    Box(modifier = modifier.clip(CircleShape).background(Brush.linearGradient(palette.background))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = if (focused) 0.13f else 0.08f), radius = size.minDimension * 0.44f, center = Offset(size.width * 0.72f, size.height * 0.20f))
            drawAnimeAvatar(avatarId, palette)
        }
    }
}

private fun DrawScope.drawAnimeAvatar(id: String, palette: AvatarPalette) {
    val w = size.width
    val h = size.height

    drawOval(palette.shirt, topLeft = Offset(w * 0.14f, h * 0.73f), size = Size(w * 0.72f, h * 0.36f))
    drawRect(palette.skin, topLeft = Offset(w * 0.43f, h * 0.62f), size = Size(w * 0.14f, h * 0.19f))
    drawOval(palette.hair.copy(alpha = 0.98f), topLeft = Offset(w * 0.19f, h * 0.12f), size = Size(w * 0.62f, h * 0.68f))
    drawCircle(palette.skin, radius = w * 0.075f, center = Offset(w * 0.245f, h * 0.48f))
    drawCircle(palette.skin, radius = w * 0.075f, center = Offset(w * 0.755f, h * 0.48f))
    drawOval(palette.skin, topLeft = Offset(w * 0.245f, h * 0.20f), size = Size(w * 0.51f, h * 0.58f))

    val hair = Path()
    when (id) {
        "ocean" -> {
            hair.moveTo(w * 0.22f, h * 0.34f)
            hair.lineTo(w * 0.29f, h * 0.13f)
            hair.lineTo(w * 0.39f, h * 0.23f)
            hair.lineTo(w * 0.48f, h * 0.10f)
            hair.lineTo(w * 0.55f, h * 0.24f)
            hair.lineTo(w * 0.68f, h * 0.14f)
            hair.lineTo(w * 0.78f, h * 0.35f)
            hair.lineTo(w * 0.64f, h * 0.31f)
            hair.lineTo(w * 0.57f, h * 0.47f)
            hair.lineTo(w * 0.47f, h * 0.30f)
            hair.lineTo(w * 0.36f, h * 0.45f)
            hair.close()
        }
        "violet" -> {
            hair.moveTo(w * 0.20f, h * 0.34f)
            hair.quadraticBezierTo(w * 0.30f, h * 0.07f, w * 0.66f, h * 0.13f)
            hair.lineTo(w * 0.80f, h * 0.33f)
            hair.lineTo(w * 0.65f, h * 0.29f)
            hair.lineTo(w * 0.53f, h * 0.53f)
            hair.lineTo(w * 0.45f, h * 0.28f)
            hair.lineTo(w * 0.30f, h * 0.43f)
            hair.close()
        }
        "sunset" -> {
            hair.moveTo(w * 0.22f, h * 0.36f)
            hair.quadraticBezierTo(w * 0.24f, h * 0.10f, w * 0.52f, h * 0.12f)
            hair.quadraticBezierTo(w * 0.78f, h * 0.13f, w * 0.78f, h * 0.39f)
            hair.lineTo(w * 0.67f, h * 0.31f)
            hair.lineTo(w * 0.60f, h * 0.48f)
            hair.lineTo(w * 0.50f, h * 0.30f)
            hair.lineTo(w * 0.39f, h * 0.47f)
            hair.lineTo(w * 0.31f, h * 0.31f)
            hair.close()
        }
        "forest" -> {
            hair.moveTo(w * 0.20f, h * 0.37f)
            hair.lineTo(w * 0.26f, h * 0.18f)
            hair.lineTo(w * 0.37f, h * 0.10f)
            hair.lineTo(w * 0.47f, h * 0.17f)
            hair.lineTo(w * 0.58f, h * 0.09f)
            hair.lineTo(w * 0.72f, h * 0.18f)
            hair.lineTo(w * 0.80f, h * 0.38f)
            hair.lineTo(w * 0.66f, h * 0.31f)
            hair.lineTo(w * 0.59f, h * 0.45f)
            hair.lineTo(w * 0.48f, h * 0.31f)
            hair.lineTo(w * 0.36f, h * 0.44f)
            hair.close()
        }
        "gold" -> {
            hair.moveTo(w * 0.20f, h * 0.35f)
            hair.quadraticBezierTo(w * 0.32f, h * 0.05f, w * 0.71f, h * 0.15f)
            hair.lineTo(w * 0.82f, h * 0.36f)
            hair.lineTo(w * 0.66f, h * 0.27f)
            hair.lineTo(w * 0.58f, h * 0.43f)
            hair.lineTo(w * 0.49f, h * 0.28f)
            hair.lineTo(w * 0.38f, h * 0.42f)
            hair.lineTo(w * 0.30f, h * 0.28f)
            hair.close()
        }
        else -> {
            hair.moveTo(w * 0.18f, h * 0.36f)
            hair.lineTo(w * 0.25f, h * 0.16f)
            hair.lineTo(w * 0.36f, h * 0.20f)
            hair.lineTo(w * 0.43f, h * 0.08f)
            hair.lineTo(w * 0.53f, h * 0.20f)
            hair.lineTo(w * 0.65f, h * 0.10f)
            hair.lineTo(w * 0.82f, h * 0.36f)
            hair.lineTo(w * 0.67f, h * 0.29f)
            hair.lineTo(w * 0.58f, h * 0.46f)
            hair.lineTo(w * 0.49f, h * 0.29f)
            hair.lineTo(w * 0.36f, h * 0.45f)
            hair.lineTo(w * 0.29f, h * 0.29f)
            hair.close()
        }
    }
    drawPath(hair, palette.hair)

    val eyeY = h * 0.51f
    listOf(w * 0.38f, w * 0.62f).forEach { eyeX ->
        drawOval(Color.White, topLeft = Offset(eyeX - w * 0.055f, eyeY - h * 0.045f), size = Size(w * 0.11f, h * 0.09f))
        drawCircle(palette.iris, radius = w * 0.030f, center = Offset(eyeX, eyeY))
        drawCircle(Color(0xFF151515), radius = w * 0.015f, center = Offset(eyeX, eyeY))
        drawCircle(Color.White, radius = w * 0.006f, center = Offset(eyeX - w * 0.008f, eyeY - h * 0.008f))
    }
    drawLine(palette.hair.copy(alpha = 0.75f), Offset(w * 0.33f, h * 0.43f), Offset(w * 0.43f, h * 0.42f), strokeWidth = w * 0.018f)
    drawLine(palette.hair.copy(alpha = 0.75f), Offset(w * 0.57f, h * 0.42f), Offset(w * 0.67f, h * 0.43f), strokeWidth = w * 0.018f)
    drawArc(
        color = Color(0xFF9F4A4A),
        startAngle = 16f,
        sweepAngle = 148f,
        useCenter = false,
        topLeft = Offset(w * 0.43f, h * 0.59f),
        size = Size(w * 0.14f, h * 0.09f),
        style = Stroke(width = w * 0.014f)
    )
}

private fun profileAvatarPalette(id: String): AvatarPalette = when (id) {
    "ocean" -> AvatarPalette(listOf(Color(0xFF176B87), Color(0xFF64CCC5)), Color(0xFFF2C6A4), Color(0xFF153E5C), Color(0xFFEF5B5B), Color(0xFF1E88E5))
    "violet" -> AvatarPalette(listOf(Color(0xFF3F2B63), Color(0xFF8F70C8)), Color(0xFFE7B998), Color(0xFF201D3A), Color(0xFF5165AE), Color(0xFF7E57C2))
    "sunset" -> AvatarPalette(listOf(Color(0xFF9C4668), Color(0xFFFF8F70)), Color(0xFFFFD1B3), Color(0xFFF1789A), Color(0xFF334155), Color(0xFF43A047))
    "forest" -> AvatarPalette(listOf(Color(0xFF1D5B4F), Color(0xFF5DBB8A)), Color(0xFF8D5A42), Color(0xFF29231F), Color(0xFFF2B84B), Color(0xFF7BC8B2))
    "gold" -> AvatarPalette(listOf(Color(0xFF8C5A18), Color(0xFFFFC857)), Color(0xFFF3C5A2), Color(0xFFD56B2D), Color(0xFF5A3A86), Color(0xFF2E73B8))
    else -> AvatarPalette(listOf(Color(0xFF7B1522), Color(0xFFE94F5F)), Color(0xFFE9B690), Color(0xFF27191B), Color(0xFF24334D), Color(0xFFB62D45))
}

private fun profileAvatarLabel(id: String): String = when (id) {
    "ocean" -> "Kai"
    "violet" -> "Nova"
    "sunset" -> "Mika"
    "forest" -> "Sora"
    "gold" -> "Leo"
    else -> "Rin"
}
