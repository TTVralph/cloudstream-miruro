package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.ttvralph.miruroapp.data.AppSettings
import com.ttvralph.miruroapp.data.DiscoveryFranchiseEntry
import com.ttvralph.miruroapp.data.DiscoveryPerson
import com.ttvralph.miruroapp.data.DiscoveryRelation
import com.ttvralph.miruroapp.data.DiscoveryTitleInfo
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage

@Composable
fun DiscoveryTitleScreen(
    discovery: DiscoveryFeatureViewModel,
    library: MiruroViewModel,
    animeId: Int,
    onBack: () -> Unit,
    onOpenDetails: (Int) -> Unit
) {
    val state by discovery.titleInfo.collectAsState()
    val settings by library.settings.collectAsState()
    LaunchedEffect(animeId) { discovery.loadTitleInfo(animeId) }

    when (val current = state) {
        null, is UiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            LoadingState("Building anime guide…")
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.align(Alignment.Center)) {
                ErrorState(current.message) { discovery.loadTitleInfo(animeId) }
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Back", Modifier.width(150.dp), onBack)
            }
        }
        is UiState.Success -> DiscoveryTitleContent(
            info = current.data,
            settings = settings,
            onBack = onBack,
            onOpenDetails = onOpenDetails
        )
    }
}

@Composable
private fun DiscoveryTitleContent(
    info: DiscoveryTitleInfo,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenDetails: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        item {
            TitleGuideHero(info, settings, onBack)
            Spacer(Modifier.height(26.dp))
        }

        item {
            Column(Modifier.padding(horizontal = 54.dp)) {
                DiscoverySectionHeading("Anime Guide", settings, "CAST • FRANCHISE • MUSIC")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    info.format?.let { DiscoveryInfoTile("Format", it, settings, Modifier.width(150.dp)) }
                    info.status?.let { DiscoveryInfoTile("Status", it, settings, Modifier.width(160.dp)) }
                    info.episodes?.let { DiscoveryInfoTile("Episodes", it.toString(), settings, Modifier.width(140.dp)) }
                    info.durationMinutes?.let { DiscoveryInfoTile("Runtime", "$it min", settings, Modifier.width(140.dp)) }
                    info.source?.let { DiscoveryInfoTile("Source", it, settings, Modifier.width(175.dp)) }
                    info.year?.let { DiscoveryInfoTile("Year", it.toString(), settings, Modifier.width(120.dp)) }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    info.description ?: "No synopsis is available.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = (if (settings.largeUiText) 17 else 15).sp,
                    lineHeight = (if (settings.largeUiText) 24 else 21).sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                val studioText = info.studios.joinToString().ifBlank { "Unknown" }
                Text(
                    "Studio: $studioText",
                    color = MiruroColors.AccentSoft,
                    fontSize = (if (settings.largeUiText) 16 else 14).sp,
                    fontWeight = FontWeight.Bold
                )
                if (info.genres.isNotEmpty()) {
                    Text(
                        "Genres: ${info.genres.joinToString()}",
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = (if (settings.largeUiText) 15 else 13).sp
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }

        if (info.releaseOrder.size > 1) {
            item {
                GuideSectionContainer(settings) {
                    DiscoverySectionHeading("Franchise Release Order", settings, "EARLIEST TO LATEST")
                    Spacer(Modifier.height(10.dp))
                    FranchiseRow(info.releaseOrder, settings, onOpenDetails)
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (info.storyOrder.size > 1 && info.storyOrder.map { it.anime.id } != info.releaseOrder.map { it.anime.id }) {
            item {
                GuideSectionContainer(settings) {
                    DiscoverySectionHeading("Direct Story Path", settings, "PREQUEL → CURRENT → SEQUEL")
                    Spacer(Modifier.height(10.dp))
                    FranchiseRow(info.storyOrder, settings, onOpenDetails)
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (info.relations.isNotEmpty()) {
            item {
                GuideSectionContainer(settings) {
                    DiscoverySectionHeading("Related Titles", settings, "SPIN-OFFS, SIDES & ADAPTATIONS")
                    Spacer(Modifier.height(10.dp))
                    RelationRow(info.relations, settings, onOpenDetails)
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (info.characters.isNotEmpty()) {
            item {
                GuideSectionContainer(settings) {
                    DiscoverySectionHeading("Characters & Japanese Voices", settings, "SPOILER-LIGHT CAST")
                    Spacer(Modifier.height(10.dp))
                    PersonRow(info.characters, settings, showVoiceActor = true)
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (info.staff.isNotEmpty()) {
            item {
                GuideSectionContainer(settings) {
                    DiscoverySectionHeading("Key Staff", settings, "DIRECTORS, CREATORS & PRODUCTION")
                    Spacer(Modifier.height(10.dp))
                    PersonRow(info.staff, settings, showVoiceActor = false)
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (info.openingThemes.isNotEmpty() || info.endingThemes.isNotEmpty()) {
            item {
                GuideSectionContainer(settings) {
                    DiscoverySectionHeading("Opening & Ending Songs", settings, "THEMES")
                    Spacer(Modifier.height(12.dp))
                    ThemeColumns(info, settings)
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (info.synonyms.isNotEmpty()) {
            item {
                GuideSectionContainer(settings) {
                    DiscoverySectionHeading("Alternate Titles", settings)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        info.synonyms.joinToString(" • "),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = (if (settings.largeUiText) 15 else 13).sp,
                        lineHeight = (if (settings.largeUiText) 21 else 18).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleGuideHero(
    info: DiscoveryTitleInfo,
    settings: AppSettings,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(if (settings.largeUiText) 430.dp else 390.dp)) {
        AsyncImage(
            model = info.anime.bannerUrl ?: info.anime.posterUrl,
            contentDescription = info.anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black, Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.32f))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black))
            )
        )
        SecondaryButton(
            "Back",
            Modifier.align(Alignment.TopStart).padding(start = 54.dp, top = 28.dp).width(120.dp),
            onBack
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 54.dp, bottom = 46.dp).width(760.dp)
        ) {
            Text(
                "EXPLORE TITLE",
                color = MiruroColors.AccentSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(7.dp))
            Text(
                info.anime.title,
                color = Color.White,
                fontSize = (if (settings.largeUiText) 42 else 36).sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                info.year?.toString(),
                info.season,
                info.format,
                info.anime.score?.let { "$it% AniList" },
                info.country
            ).joinToString(" • ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    meta,
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = (if (settings.largeUiText) 17 else 15).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GuideSectionContainer(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 54.dp)
            .background(
                if (settings.highContrastUi) Color.Black else Color.White.copy(alpha = 0.035f),
                RoundedCornerShape(12.dp)
            )
            .then(
                if (settings.highContrastUi) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .padding(18.dp)
    ) {
        content()
    }
}

@Composable
private fun FranchiseRow(
    entries: List<DiscoveryFranchiseEntry>,
    settings: AppSettings,
    onOpenDetails: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        items(entries, key = { it.anime.id }) { entry ->
            DiscoveryMediaCard(
                item = entry.anime,
                settings = settings,
                width = 190.dp,
                height = 270.dp,
                subtitle = listOfNotNull(
                    entry.relationship,
                    entry.format,
                    entry.startDate?.let { formatFuzzyDate(it) }
                ).joinToString(" • "),
                badge = entry.relationship.takeUnless { it == "Current title" } ?: "CURRENT",
                onClick = { onOpenDetails(entry.anime.id) }
            )
        }
    }
}

@Composable
private fun RelationRow(
    relations: List<DiscoveryRelation>,
    settings: AppSettings,
    onOpenDetails: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        items(relations, key = { "${it.relationType}:${it.anime.id}" }) { relation ->
            DiscoveryMediaCard(
                item = relation.anime,
                settings = settings,
                width = 180.dp,
                height = 255.dp,
                subtitle = listOfNotNull(
                    relation.relationType,
                    relation.format,
                    relation.startDate?.let(::formatFuzzyDate)
                ).joinToString(" • "),
                badge = relation.relationType.uppercase(),
                onClick = { onOpenDetails(relation.anime.id) }
            )
        }
    }
}

@Composable
private fun PersonRow(
    people: List<DiscoveryPerson>,
    settings: AppSettings,
    showVoiceActor: Boolean
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        items(people, key = { "${it.id}:${it.role}" }) { person ->
            PersonCard(person, settings, showVoiceActor)
        }
    }
}

@Composable
private fun PersonCard(
    person: DiscoveryPerson,
    settings: AppSettings,
    showVoiceActor: Boolean
) {
    val shape = RoundedCornerShape(10.dp)
    FocusableSurface(
        onClick = {},
        modifier = Modifier.width(220.dp).height(if (settings.largeUiText) 265.dp else 240.dp),
        shape = shape,
        unfocusedBackground = if (settings.highContrastUi) Color.Black else Color(0xFF151515),
        focusedBackground = Color.White
    ) { focused ->
        Column(
            Modifier.fillMaxSize().then(
                if (settings.highContrastUi && !focused) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.68f), shape)
                } else Modifier
            )
        ) {
            AsyncImage(
                model = person.imageUrl,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(145.dp)
            )
            Column(Modifier.padding(11.dp)) {
                Text(
                    person.name,
                    color = if (focused) Color.Black else Color.White,
                    fontSize = (if (settings.largeUiText) 16 else 14).sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    person.role,
                    color = if (focused) Color.DarkGray else MiruroColors.AccentSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (showVoiceActor && !person.voiceActor.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "JP voice: ${person.voiceActor}",
                        color = if (focused) Color.DarkGray else Color.White.copy(alpha = 0.62f),
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColumns(info: DiscoveryTitleInfo, settings: AppSettings) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ThemeList("Openings", info.openingThemes, settings, Modifier.weight(1f))
        ThemeList("Endings", info.endingThemes, settings, Modifier.weight(1f))
    }
}

@Composable
private fun ThemeList(
    title: String,
    values: List<String>,
    settings: AppSettings,
    modifier: Modifier
) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(9.dp))
            .padding(13.dp)
    ) {
        Text(
            title,
            color = MiruroColors.AccentSoft,
            fontSize = (if (settings.largeUiText) 18 else 16).sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        if (values.isEmpty()) {
            StateMessage("No theme information returned.")
        } else {
            values.forEachIndexed { index, song ->
                Text(
                    "${index + 1}. $song",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = (if (settings.largeUiText) 14 else 12).sp,
                    lineHeight = (if (settings.largeUiText) 20 else 17).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }
    }
}

private fun formatFuzzyDate(value: Int): String {
    val year = value / 10_000
    val month = (value / 100) % 100
    return if (month in 1..12) "$year-${month.toString().padStart(2, '0')}" else year.toString()
}
