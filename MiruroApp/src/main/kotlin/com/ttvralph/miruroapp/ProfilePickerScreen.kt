package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.LocalProfile
import com.ttvralph.miruroapp.data.ProfileState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.SecondaryButton

@Composable
fun ProfilePickerScreen(
    state: ProfileState,
    onSelect: (LocalProfile) -> Unit,
    onCreate: () -> Unit
) {
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
            items(state.profiles, key = { it.id }) { profile ->
                ProfileTile(profile, profile.id == state.activeId) { onSelect(profile) }
            }
        }
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Add profile", Modifier.width(190.dp), onCreate)
    }
}

@Composable
private fun ProfileTile(profile: LocalProfile, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.size(150.dp),
            shape = RoundedCornerShape(18.dp),
            unfocusedBackground = if (active) MiruroColors.Accent.copy(alpha = 0.34f) else MiruroColors.Card,
            focusedBackground = Color.White
        ) { focused ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    profile.name.take(1).uppercase(),
                    color = if (focused) Color.Black else Color.White,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black
                )
            }
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
