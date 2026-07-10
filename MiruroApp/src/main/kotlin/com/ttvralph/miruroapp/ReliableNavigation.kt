package com.ttvralph.miruroapp

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeSort
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.HomeRow
import com.ttvralph.miruroapp.data.PosterGridDensity
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.Logo
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val ReliableSafeX = 52.dp
internal val ReliableNavHeight = 76.dp
internal val ReliableCardWidth = 228.dp
internal val ReliableCardHeight = 128.dp
internal val ReliableRadius = 5.dp

internal data class ReliableResumeItem(val anime: AnimeItem, val progress: WatchProgress)

internal class HomeCatalogueCache(context: Context) {
    private val preferences = context.getSharedPreferences("anistream_home_catalogue", Context.MODE_PRIVATE)
    private val mapper = jacksonObjectMapper()

    suspend fun read(): List<HomeRow> = withContext(Dispatchers.IO) {
        val json = preferences.getString(KEY_ROWS, null) ?: return@withContext emptyList()
        runCatching {
            mapper.readValue(json, object : TypeReference<List<HomeRow>>() {})
        }.getOrElse {
            preferences.edit().remove(KEY_ROWS).apply()
            emptyList()
        }
    }

    suspend fun write(rows: List<HomeRow>) = withContext(Dispatchers.IO) {
        runCatching { mapper.writeValueAsString(rows) }
            .onSuccess { preferences.edit().putString(KEY_ROWS, it).apply() }
    }

    private companion object {
        const val KEY_ROWS = "rows_v1"
    }
}

@Composable
fun ReliableTopBar(
    current: String,
    onHome: () -> Unit,
    onAnime: () -> Unit,
    onMovies: () -> Unit,
    onDiscover: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        ReliableNavText("Home", current == "Home", onHome)
        ReliableNavText("Anime", current == "Anime", onAnime)
        ReliableNavText("Movies", current == "Movies", onMovies)
        ReliableNavText("Discover", current == "Discover", onDiscover, 104.dp)
        ReliableNavText("My List", current == "My List", onMyList)
        Spacer(Modifier.weight(1f))
        ReliableNavIcon(Icons.Filled.Search, "Search", current == "Search", onSearch)
        Spacer(Modifier.width(10.dp))
        ReliableNavIcon(Icons.Filled.Settings, "Settings", current == "Settings", onSettings)
    }
}

@Composable
private fun ReliableNavText(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    width: Dp = 82.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .width(width)
            .height(46.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (focused || selected) Color.White else Color.White.copy(alpha = 0.68f),
            fontSize = 14.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
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
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(999.dp),
        unfocusedBackground = if (selected) MiruroColors.Accent.copy(alpha = 0.35f) else Color.Transparent,
        focusedBackground = Color.White
    ) { focused ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (focused) Color.Black else Color.White)
        }
    }
}
