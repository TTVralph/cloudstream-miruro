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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeSeason
import com.ttvralph.miruroapp.ui.BodyText
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PosterCard
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SectionTitle
import com.ttvralph.miruroapp.ui.StateMessage

@Composable
fun HomeScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    val state by viewModel.homeRows.collectAsState()
    when (val s = state) {
        is UiState.Loading -> LoadingState("Loading home rows…")
        is UiState.Error -> ErrorState(s.message) { viewModel.loadHome() }
        is UiState.Success -> {
            val rows = s.data
            TvLazyColumn(modifier = Modifier.fillMaxSize()) {
                rows.firstOrNull()?.items?.firstOrNull()?.let { hero ->
                    item { HeroBanner(hero, onOpenDetails) }
                }
                items(rows, key = { it.title }) { row ->
                    PosterRow(row.title, row.items, onOpenDetails)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun HeroBanner(item: AnimeItem, onOpenDetails: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MiruroColors.Panel)
    ) {
        AsyncImage(
            model = item.bannerUrl ?: item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.42f,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp),
        ) {
            Text(item.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(item.year?.toString(), item.type.name, "Trending").joinToString(" • "),
                color = MiruroColors.Accent,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            BodyText("Discover anime, search AniList metadata, manage your watchlist, and jump into episode playback.")
            Spacer(Modifier.height(14.dp))
            PrimaryButton("View Details", modifier = Modifier.width(200.dp)) { onOpenDetails(item.id) }
        }
    }
}

@Composable
private fun PosterRow(title: String, items: List<AnimeItem>, onClick: (Int) -> Unit) {
    Column {
        SectionTitle(title)
        TvLazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
        ) {
            items(items, key = { it.id }) { anime ->
                PosterCard(anime) { onClick(anime.id) }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val state by viewModel.searchResults.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search anime by title", color = MiruroColors.Subtle) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MiruroColors.Accent,
                unfocusedBorderColor = MiruroColors.Border,
                cursorColor = MiruroColors.Accent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp)
        )
        when (val s = state) {
            null -> StateMessage(if (query.isBlank()) "Enter a title to search." else "Press search to run this query again.")
            is UiState.Loading -> LoadingState("Searching…")
            is UiState.Error -> ErrorState(s.message) { viewModel.search(query) }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    StateMessage("No results found.")
                } else {
                    TvLazyColumn(modifier = Modifier.fillMaxSize()) {
                        item { PosterRow("Results", s.data, onOpenDetails) }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(viewModel: MiruroViewModel, onOpenDetails: (Int) -> Unit) {
    val ids by viewModel.favoriteIds.collectAsState()
    if (ids.isEmpty()) {
        StateMessage("No favorites yet.")
    } else {
        TvLazyColumn(modifier = Modifier.fillMaxSize()) {
            items(ids.toList(), key = { it }) { id ->
                FocusableSurface(onClick = { onOpenDetails(id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(64.dp)) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                        Text("Anime #$id", color = Color.White, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    StateMessage(
        "AniTrack UI has been adapted for Miruro: AniList discovery, search, details, watchlist, and Media3 " +
            "playback are presented with Jetpack Compose for TV. Stream sources are resolved from Miruro on " +
            "demand when you press Play."
    )
}

@Composable
fun DetailsScreen(
    viewModel: MiruroViewModel,
    animeId: Int,
    onOpenEpisode: (Int, Int) -> Unit,
    onPlayEpisode: (Int, Int) -> Unit
) {
    LaunchedEffect(animeId) { viewModel.loadDetails(animeId) }
    val state by viewModel.details.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingState("Loading details…")
        is UiState.Error -> ErrorState(s.message) { viewModel.loadDetails(animeId) }
        is UiState.Success -> {
            val details = s.data
            val favorite = details.id in favorites
            TvLazyColumn(modifier = Modifier.fillMaxSize()) {
                item { DetailsHeader(details, favorite) { viewModel.toggleFavorite(details.id) } }
                if (details.seasons.isEmpty()) {
                    item { StateMessage("No episodes available.") }
                } else {
                    details.seasons.forEach { season ->
                        item { SeasonHeader(season) }
                        items(season.episodes, key = { "${season.seasonNumber}-${it.episodeNumber}" }) { ep ->
                            EpisodeRow(ep) {
                                if (ep.sourceCandidates.isNotEmpty()) onPlayEpisode(season.seasonNumber, ep.episodeNumber)
                                else onOpenEpisode(season.seasonNumber, ep.episodeNumber)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsHeader(d: AnimeDetails, favorite: Boolean, onToggleFavorite: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        AsyncImage(
            model = d.posterUrl,
            contentDescription = d.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(210.dp)
                .height(305.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MiruroColors.Card)
        )
        Column(modifier = Modifier.padding(start = 24.dp)) {
            Text(d.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(d.year?.toString(), d.status, d.rating, d.genres.takeIf { it.isNotEmpty() }?.joinToString())
                    .joinToString(" • "),
                color = MiruroColors.Accent,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(10.dp))
            BodyText(d.description ?: "No synopsis available.", modifier = Modifier.width(520.dp))
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (favorite) "Remove Favorite" else "Add Favorite", modifier = Modifier.width(230.dp)) {
                onToggleFavorite()
            }
        }
    }
}

@Composable
private fun SeasonHeader(s: AnimeSeason) {
    SectionTitle("Season ${s.seasonNumber}: ${s.title}")
}

@Composable
private fun EpisodeRow(ep: AnimeEpisode, onClick: () -> Unit) {
    val status = listOfNotNull(
        ep.runtimeMinutes?.let { "${it}m" },
        ep.releaseDate,
        if (ep.sourceCandidates.isNotEmpty()) "Playable" else "Details"
    ).joinToString(" • ")
    FocusableSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).height(64.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${ep.episodeNumber}. ${ep.title ?: "Episode ${ep.episodeNumber}"}",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(status, color = MiruroColors.Subtle, fontSize = 13.sp)
        }
    }
}

@Composable
fun EpisodeDetailsScreen(episode: AnimeEpisode?, onPlay: () -> Unit) {
    if (episode == null) {
        StateMessage("Episode not found.")
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        episode.thumbnailUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(400.dp).height(230.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(16.dp))
        }
        SectionTitle("Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}")
        listOf(
            "Title" to (episode.title ?: "Episode ${episode.episodeNumber}"),
            "Runtime" to (episode.runtimeMinutes?.let { "${it}m" } ?: "Unknown"),
            "Release date" to (episode.releaseDate ?: "Unknown"),
            "Audio type" to episode.audioType.name,
            "Playback" to if (episode.sourceCandidates.isNotEmpty()) "Playable source available" else "No playable source is available for this episode."
        ).forEach { (label, value) -> BodyText("$label: $value") }
        if (episode.sourceCandidates.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Play", modifier = Modifier.width(180.dp), onClick = onPlay)
        }
    }
}
