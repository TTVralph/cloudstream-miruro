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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.LocalProfile
import com.ttvralph.miruroapp.data.PROFILE_AVATAR_IDS
import com.ttvralph.miruroapp.data.PROFILE_THEME_COLOR_IDS
import com.ttvralph.miruroapp.data.ProfileState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
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
    var name by remember(profile?.id) { mutableStateOf(profile?.name ?: suggestedName) }
    var avatarId by remember(profile?.id) { mutableStateOf(profile?.avatarId ?: PROFILE_AVATAR_IDS.first()) }
    var themeColorId by remember(profile?.id) { mutableStateOf(profile?.themeColorId ?: "red") }
    var editingName by remember(profile?.id) { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    if (editingName) {
        ProfileNameKeyboardOverlay(
            initialName = name,
            onCancel = { editingName = false },
            onDone = { updatedName ->
                name = updatedName
                editingName = false
            }
        )
        return
    }

    BackHandler(onBack = onCancel)

    LaunchedEffect(profile?.id, editingName) {
        delay(100L)
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 72.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.width(990.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (profile == null) "Create profile" else "Edit profile",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            ProfilePrimaryButton(
                if (profile == null) "Create profile" else "Save profile",
                Modifier.width(190.dp)
            ) { onSave(name.trim().ifBlank { suggestedName }, avatarId, themeColorId) }
            SecondaryButton("Cancel", Modifier.width(130.dp), onCancel)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.width(990.dp).height(330.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(240.dp)) {
                Box(
                    Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(profileThemeColor(themeColorId))
                        .padding(6.dp)
                ) {
                    ProfileAvatarArtwork(name, avatarId, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(8.dp))
                Text(profileAvatarLabel(avatarId), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("Profile name", color = MiruroColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                FocusableSurface(
                    onClick = { editingName = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp).focusRequester(firstFocus),
                    shape = RoundedCornerShape(7.dp),
                    unfocusedBackground = Color.White.copy(alpha = 0.08f),
                    focusedBackground = Color.White
                ) { focused ->
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name.ifBlank { "Enter a profile name" },
                            color = if (focused) Color.Black else if (name.isBlank()) MiruroColors.Subtle else Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Edit", color = if (focused) Color.Black else MiruroColors.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Avatar", color = MiruroColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp)
                ) {
                    itemsIndexed(PROFILE_AVATAR_IDS, key = { _, id -> id }) { _, id ->
                        FocusableSurface(
                            onClick = { avatarId = id },
                            modifier = Modifier.size(70.dp),
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
                Spacer(Modifier.height(10.dp))
                Text("Theme color", color = MiruroColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
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
    }
}

@Composable
private fun ProfileNameKeyboardOverlay(
    initialName: String,
    onCancel: () -> Unit,
    onDone: (String) -> Unit
) {
    BackHandler(onBack = onCancel)
    var draft by remember(initialName) { mutableStateOf(initialName) }
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100L)
        runCatching { firstKey.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Edit profile name", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        draft.ifBlank { "Enter a profile name" },
                        color = if (draft.isBlank()) MiruroColors.Subtle else Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM").forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        row.forEachIndexed { characterIndex, character ->
                            val keyModifier = Modifier
                                .weight(1f)
                                .then(
                                    if (rowIndex == 1 && characterIndex == 0) {
                                        Modifier.focusRequester(firstKey)
                                    } else {
                                        Modifier
                                    }
                                )
                            ProfileKeyboardKey(
                                text = character.toString(),
                                modifier = keyModifier
                            ) {
                                if (draft.length < 24) draft += character
                            }
                        }
                    }
                    if (rowIndex < 3) Spacer(Modifier.height(6.dp))
                }
            }
            Column(
                modifier = Modifier.width(150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileKeyboardKey("Delete", Modifier.fillMaxWidth()) {
                    if (draft.isNotEmpty()) draft = draft.dropLast(1)
                }
                ProfileKeyboardKey("Space", Modifier.fillMaxWidth()) {
                    if (draft.isNotBlank() && !draft.endsWith(' ') && draft.length < 24) draft += " "
                }
                ProfileKeyboardKey("Clear", Modifier.fillMaxWidth()) { draft = "" }
                SecondaryButton("Cancel", Modifier.fillMaxWidth(), onCancel)
                ProfilePrimaryButton("Done", Modifier.fillMaxWidth()) { onDone(draft.trim()) }
            }
        }
    }
}

@Composable
private fun ProfilePrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(7.dp),
        unfocusedBackground = MiruroColors.Accent,
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
private fun ProfileKeyboardKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
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
    val imageUrl = profileAvatarImageUrl(avatarId)
    var imageLoaded by remember(imageUrl) { mutableStateOf(false) }
    Box(modifier = modifier.clip(CircleShape).background(Brush.linearGradient(palette.background))) {
        if (!imageLoaded) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = if (focused) 0.13f else 0.08f), radius = size.minDimension * 0.44f, center = Offset(size.width * 0.72f, size.height * 0.20f))
                drawAnimeAvatar(avatarId, palette)
            }
        }
        AsyncImage(
            model = imageUrl,
            contentDescription = profileAvatarLabel(avatarId),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            onSuccess = { imageLoaded = true },
            onError = { imageLoaded = false }
        )
    }
}

private fun DrawScope.drawAnimeAvatar(id: String, palette: AvatarPalette) {
    val w = size.width
    val h = size.height

    drawOval(palette.shirt, topLeft = Offset(w * 0.14f, h * 0.73f), size = Size(w * 0.72f, h * 0.36f))
    drawRect(palette.skin, topLeft = Offset(w * 0.43f, h * 0.62f), size = Size(w * 0.14f, h * 0.19f))
    if (id == "crimson") {
        drawOval(Color(0xFF6F3B14), topLeft = Offset(w * 0.10f, h * 0.09f), size = Size(w * 0.80f, h * 0.24f))
        drawOval(Color(0xFFF3C34E), topLeft = Offset(w * 0.12f, h * 0.08f), size = Size(w * 0.76f, h * 0.20f))
        drawOval(Color(0xFFF0B83A), topLeft = Offset(w * 0.27f, h * 0.01f), size = Size(w * 0.46f, h * 0.28f))
    }
    if (id == "sunset") {
        drawPath(Path().apply {
            moveTo(w * 0.20f, h * 0.29f)
            lineTo(w * 0.13f, h * 0.10f)
            lineTo(w * 0.31f, h * 0.19f)
            close()
        }, Color(0xFF17151D))
        drawPath(Path().apply {
            moveTo(w * 0.80f, h * 0.29f)
            lineTo(w * 0.87f, h * 0.10f)
            lineTo(w * 0.69f, h * 0.19f)
            close()
        }, Color(0xFF17151D))
    }
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

    when (id) {
        "crimson" -> {
            drawRoundRect(
                Color(0xFFD92D35),
                topLeft = Offset(w * 0.28f, h * 0.18f),
                size = Size(w * 0.44f, h * 0.075f),
                cornerRadius = CornerRadius(w * 0.02f)
            )
        }
        "violet" -> {
            drawRoundRect(
                Color(0xFF777D8C),
                topLeft = Offset(w * 0.27f, h * 0.29f),
                size = Size(w * 0.46f, h * 0.10f),
                cornerRadius = CornerRadius(w * 0.018f)
            )
            drawCircle(Color(0xFF252A34), radius = w * 0.035f, center = Offset(w * 0.50f, h * 0.34f))
            drawLine(Color(0xFFB8BEC9), Offset(w * 0.47f, h * 0.34f), Offset(w * 0.53f, h * 0.34f), strokeWidth = w * 0.010f)
        }
        "sunset" -> {
            drawPath(Path().apply {
                moveTo(w * 0.20f, h * 0.30f)
                lineTo(w * 0.14f, h * 0.14f)
                lineTo(w * 0.30f, h * 0.21f)
                close()
            }, Color(0xFF111018))
            drawPath(Path().apply {
                moveTo(w * 0.80f, h * 0.30f)
                lineTo(w * 0.86f, h * 0.14f)
                lineTo(w * 0.70f, h * 0.21f)
                close()
            }, Color(0xFF111018))
        }
        "gold" -> {
            listOf(0.29f, 0.43f, 0.57f, 0.71f).forEachIndexed { index, x ->
                drawPath(Path().apply {
                    moveTo(w * (x - 0.07f), h * 0.24f)
                    lineTo(w * x, h * if (index % 2 == 0) 0.05f else 0.09f)
                    lineTo(w * (x + 0.07f), h * 0.25f)
                    close()
                }, Color(0xFFE8492F))
            }
        }
    }

    val eyeY = h * 0.51f
    listOf(w * 0.38f, w * 0.62f).forEach { eyeX ->
        drawOval(Color.White, topLeft = Offset(eyeX - w * 0.055f, eyeY - h * 0.045f), size = Size(w * 0.11f, h * 0.09f))
        drawCircle(palette.iris, radius = w * 0.030f, center = Offset(eyeX, eyeY))
        drawCircle(Color(0xFF151515), radius = w * 0.015f, center = Offset(eyeX, eyeY))
        drawCircle(Color.White, radius = w * 0.006f, center = Offset(eyeX - w * 0.008f, eyeY - h * 0.008f))
    }
    drawLine(palette.hair.copy(alpha = 0.75f), Offset(w * 0.33f, h * 0.43f), Offset(w * 0.43f, h * 0.42f), strokeWidth = w * 0.018f)
    drawLine(palette.hair.copy(alpha = 0.75f), Offset(w * 0.57f, h * 0.42f), Offset(w * 0.67f, h * 0.43f), strokeWidth = w * 0.018f)
    when (id) {
        "crimson" -> {
            drawLine(Color(0xFF8C3D3D), Offset(w * 0.35f, h * 0.55f), Offset(w * 0.32f, h * 0.61f), strokeWidth = w * 0.012f)
            drawLine(Color(0xFF8C3D3D), Offset(w * 0.32f, h * 0.61f), Offset(w * 0.36f, h * 0.62f), strokeWidth = w * 0.012f)
        }
        "ocean" -> {
            listOf(0.31f, 0.35f, 0.39f, 0.61f, 0.65f, 0.69f).forEach { x ->
                drawCircle(Color(0xFF8D5B4B), radius = w * 0.008f, center = Offset(w * x, h * 0.58f))
            }
        }
        "forest" -> {
            drawRoundRect(
                Color(0xFF11131B),
                topLeft = Offset(w * 0.27f, h * 0.44f),
                size = Size(w * 0.46f, h * 0.14f),
                cornerRadius = CornerRadius(w * 0.035f)
            )
            drawLine(Color.White.copy(alpha = 0.22f), Offset(w * 0.31f, h * 0.47f), Offset(w * 0.67f, h * 0.47f), strokeWidth = w * 0.009f)
        }
        "gold" -> {
            drawLine(Color(0xFF3A2318), Offset(w * 0.31f, h * 0.42f), Offset(w * 0.43f, h * 0.46f), strokeWidth = w * 0.028f)
            drawLine(Color(0xFF3A2318), Offset(w * 0.57f, h * 0.46f), Offset(w * 0.69f, h * 0.42f), strokeWidth = w * 0.028f)
        }
    }
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
    "ocean" -> AvatarPalette(listOf(Color(0xFF0E5B4D), Color(0xFF4CC9A4)), Color(0xFFF0C3A0), Color(0xFF164A39), Color(0xFF163B35), Color(0xFF2E8B72))
    "violet" -> AvatarPalette(listOf(Color(0xFF302747), Color(0xFF6A557F)), Color(0xFFE7B998), Color(0xFF15121C), Color(0xFF17151D), Color(0xFFC62828))
    "sunset" -> AvatarPalette(listOf(Color(0xFFA73C69), Color(0xFFFF8FB1)), Color(0xFFFFD1B3), Color(0xFFF1789A), Color(0xFF26395F), Color(0xFF43A047))
    "forest" -> AvatarPalette(listOf(Color(0xFF2472A5), Color(0xFF82D9FF)), Color(0xFFF0C5A5), Color(0xFFF1F4FA), Color(0xFF12141C), Color(0xFF42A5F5))
    "gold" -> AvatarPalette(listOf(Color(0xFF8C4218), Color(0xFFFFC857)), Color(0xFFF3C5A2), Color(0xFFFFD23F), Color(0xFF4A2519), Color(0xFFE88C22))
    else -> AvatarPalette(listOf(Color(0xFF7B1522), Color(0xFFE94F5F)), Color(0xFFE9B690), Color(0xFF27191B), Color(0xFF24334D), Color(0xFFB62D45))
}

private fun profileAvatarLabel(id: String): String = when (id) {
    "ocean" -> "Deku"
    "violet" -> "Itachi"
    "sunset" -> "Anya"
    "forest" -> "Gojo"
    "gold" -> "Rengoku"
    else -> "Luffy"
}

private fun profileAvatarImageUrl(id: String): String = when (id) {
    "ocean" -> "https://s4.anilist.co/file/anilistcdn/character/large/b89028-8w1I9o1ISHMg.png"
    "violet" -> "https://s4.anilist.co/file/anilistcdn/character/large/b14-9Kb1E5oel1ke.png"
    "sunset" -> "https://s4.anilist.co/file/anilistcdn/character/large/b138100-4Li0tWRCa5bQ.png"
    "forest" -> "https://s4.anilist.co/file/anilistcdn/character/large/b127691-9zqh1xpIubn7.png"
    "gold" -> "https://s4.anilist.co/file/anilistcdn/character/large/b129133-VlTPowwt68rJ.png"
    else -> "https://s4.anilist.co/file/anilistcdn/character/large/b40-MNypXsxSRb1R.png"
}
