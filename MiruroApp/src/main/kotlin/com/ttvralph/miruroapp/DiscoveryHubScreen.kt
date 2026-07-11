package com.ttvralph.miruroapp

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.DiscoveryMode
import com.ttvralph.miruroapp.data.DiscoveryPick
import com.ttvralph.miruroapp.data.TitleReaction
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage

@Composable
fun DiscoveryHubScreen(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    discovery: DiscoveryFeatureViewModel,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit,
    onOpenSearch: () -> Unit
) {
    val homeState by viewModel.homeRows.collectAsState()
    val progress by viewModel.watchProgress.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val reactions by features.reactions.collectAsState()
    val tracking by features.trackingStatuses.collectAsState()
    val metadata by features.metadata.collectAsState()
    val profile by features.profileState.collectAsState()
    val pickState by discovery.pick.collectAsState()

    val catalogue = remember(homeState) {
        (homeState as? UiState.Success)?.data.orEmpty()
            .flatMap { it.items }
            .distinctBy { it.id }
    }
    val disliked = remember(reactions) {
        reactions.filterValues { it == TitleReaction.DISLIKE }.keys
    }
    val exploreNow = remember(catalogue, disliked) {
        catalogue.filterNot { it.id in disliked }.take(20)
    }
    val unfinished = remember(progress) {
        progress.filterNot { it.watched }
            .groupBy { Triple(it.animeId, it.seasonNumber, it.episodeNumber) }
            .mapNotNull { (_, values) -> values.maxByOrNull { it.updatedAtMs } }
            .sortedByDescending { it.updatedAtMs }
    }

    LaunchedEffect(favorites, unfinished.map { it.animeId }.toSet()) {
        features.loadMetadata(favorites + unfinished.map { it.animeId })
    }

    fun select(mode: DiscoveryMode) {
        if (mode == DiscoveryMode.CONTINUE_SOMETHING) {
            val resume = unfinished.firstOrNull()
            val item = resume?.let { viewModel.cachedItem(it.animeId) ?: metadata[it.animeId] }
            discovery.setLocalPick(
                DiscoveryPick(
                    mode = mode,
                    anime = item ?: resume?.let {
                        AnimeItem(it.animeId, "Continue watching", null, null, AnimeType.UNKNOWN)
                    },
                    reason = if (resume == null) {
                        "There are no unfinished episodes in this profile yet."
                    } else {
                        "Resume Season ${resume.seasonNumber}, Episode ${resume.episodeNumber} at ${(resume.percent * 100).toInt()}%."
                    },
                    resumeProgress = resume
                )
            )
            return
        }

        val broadExclusions = progress.map { it.animeId }.toSet() + favorites + tracking.keys
        val exclusions = when (mode) {
            DiscoveryMode.START_SOMETHING_NEW -> broadExclusions + disliked
            else -> disliked
        }
        discovery.pick(mode, exclusions)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MiruroColors.Background),
        contentPadding = PaddingValues(bottom = 70.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Discover",
                        color = Color.White,
                        fontSize = (if (settings.largeUiText) 36 else 31).sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Play Something, browse ideas, or use detailed filters.",
                        color = MiruroColors.Subtle,
                        fontSize = (if (settings.largeUiText) 16 else 13).sp
                    )
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Advanced Search", Modifier.width(210.dp), onOpenSearch)
            }
        }

        item {
            DiscoveryPickHero(
                state = pickState,
                settings = settings,
                profileName = profile.activeProfile.name,
                onChoose = ::select,
                onOpenDetails = onOpenDetails,
                onPlayProgress = onPlayProgress,
                onPickAgain = { current -> select(current.mode) }
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            DiscoverySectionHeading("Play Something", settings, "LOCAL-FIRST PICKS")
            Spacer(Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(DiscoveryMode.entries, key = { it.name }) { mode ->
                    DiscoveryModeCard(mode, settings, onClick = { select(mode) })
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        if (unfinished.isNotEmpty()) {
            item {
                DiscoverySectionHeading("Finish Something", settings, "${unfinished.size} UNFINISHED")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(unfinished.take(15), key = { it.key }) { entry ->
                        val item = viewModel.cachedItem(entry.animeId) ?: metadata[entry.animeId]
                            ?: AnimeItem(entry.animeId, "Anime #${entry.animeId}", null, null, AnimeType.UNKNOWN)
                        DiscoveryMediaCard(
                            item = item,
                            settings = settings,
                            width = 214.dp,
                            height = 138.dp,
                            subtitle = "S${entry.seasonNumber} E${entry.episodeNumber} • ${(entry.percent * 100).toInt()}%",
                            badge = "RESUME",
                            onClick = { onPlayProgress(entry) }
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (exploreNow.isNotEmpty()) {
            item {
                DiscoverySectionHeading("Explore Now", settings, "TRENDING & POPULAR")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(exploreNow, key = { it.id }) { anime ->
                        DiscoveryMediaCard(
                            item = anime,
                            settings = settings,
                            subtitle = listOfNotNull(anime.year?.toString(), anime.score?.let { "$it%" }).joinToString(" • "),
                            onClick = { onOpenDetails(anime.id) }
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        } else {
            item {
                when (homeState) {
                    is UiState.Loading -> LoadingState("Loading discovery ideas…")
                    is UiState.Error -> ErrorState("Could not load discovery ideas.") { viewModel.loadHome() }
                    else -> StateMessage("Discovery ideas will appear after the Home catalogue loads.")
                }
            }
        }
    }
}

@Composable
private fun DiscoveryModeCard(
    mode: DiscoveryMode,
    settings: com.ttvralph.miruroapp.data.AppSettings,
    onClick: () -> Unit
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(260.dp).height(if (settings.largeUiText) 126.dp else 112.dp),
        shape = RoundedCornerShape(12.dp),
        unfocusedBackground = if (settings.highContrastUi) Color.Black else Color.White.copy(alpha = 0.055f),
        focusedBackground = Color.White
    ) { focused ->
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                mode.label,
                color = if (focused) Color.Black else Color.White,
                fontSize = (if (settings.largeUiText) 19 else 17).sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(5.dp))
            Text(
                mode.description,
                color = if (focused) Color.DarkGray else Color.White.copy(alpha = 0.67f),
                fontSize = (if (settings.largeUiText) 13 else 11).sp,
                lineHeight = (if (settings.largeUiText) 17 else 15).sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiscoveryPickHero(
    state: UiState<DiscoveryPick>?,
    settings: com.ttvralph.miruroapp.data.AppSettings,
    profileName: String,
    onChoose: (DiscoveryMode) -> Unit,
    onOpenDetails: (Int) -> Unit,
    onPlayProgress: (WatchProgress) -> Unit,
    onPickAgain: (DiscoveryPick) -> Unit
) {
    val pick = (state as? UiState.Success)?.data
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (settings.largeUiText) 360.dp else 330.dp)
            .background(Color(0xFF111111), RoundedCornerShape(14.dp))
    ) {
        pick?.anime?.let { anime ->
            AsyncImage(
                model = anime.bannerUrl ?: anime.posterUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.Black, Color.Black.copy(alpha = 0.94f), Color.Black.copy(alpha = 0.52f))
                    )
                )
            )
        }

        Column(
            Modifier
                .align(Alignment.CenterStart)
                .width(720.dp)
                .padding(28.dp)
        ) {
            when (state) {
                is UiState.Loading -> LoadingState("Choosing something for $profileName…")
                is UiState.Error -> {
                    Text("Play Something", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, color = MiruroColors.Subtle, fontSize = 15.sp)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("Try Surprise Me", Modifier.width(210.dp)) { onChoose(DiscoveryMode.SURPRISE_ME) }
                }
                is UiState.Success -> {
                    val value = state.data
                    Text(
                        value.mode.label.uppercase(),
                        color = MiruroColors.AccentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        value.anime?.title ?: "Nothing to resume yet",
                        color = Color.White,
                        fontSize = (if (settings.largeUiText) 36 else 31).sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        value.reason,
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = (if (settings.largeUiText) 16 else 14).sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            value.resumeProgress != null -> {
                                PrimaryButton("Resume", Modifier.width(160.dp)) {
                                    onPlayProgress(value.resumeProgress)
                                }
                            }
                            value.anime != null -> {
                                PrimaryButton("Open Details", Modifier.width(190.dp)) {
                                    onOpenDetails(value.anime.id)
                                }
                            }
                        }
                        SecondaryButton("Pick Again", Modifier.width(170.dp)) { onPickAgain(value) }
                    }
                }
                null -> {
                    Text(
                        "Not sure what to watch?",
                        color = Color.White,
                        fontSize = (if (settings.largeUiText) 36 else 31).sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AniStream can choose from your unfinished shows or the current catalogue without storing new catalogue data on disk.",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = (if (settings.largeUiText) 16 else 14).sp,
                        lineHeight = (if (settings.largeUiText) 22 else 19).sp
                    )
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Surprise Me", Modifier.width(180.dp)) { onChoose(DiscoveryMode.SURPRISE_ME) }
                }
            }
        }
    }
}
