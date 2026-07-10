package com.ttvralph.miruroapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.ui.GenreChip
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage
import java.util.Locale

@Composable
fun AutomaticEpisodeDetailsScreen(
    episode: AnimeEpisode?,
    viewModel: MiruroViewModel,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    if (episode == null) {
        StateMessage("Episode not found.")
        return
    }

    val progress by viewModel.watchProgress.collectAsState()
    val saved = progress.firstOrNull {
        it.animeId == episode.anilistId &&
            it.seasonNumber == episode.seasonNumber &&
            it.episodeNumber == episode.episodeNumber &&
            it.audioType == episode.audioType
    }
    val providers = episode.sourceCandidates
        .map { it.provider.lowercase(Locale.ROOT) }
        .distinct()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(
            start = ReliableSafeX,
            end = ReliableSafeX,
            top = 28.dp,
            bottom = 42.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SecondaryButton("Back", Modifier.width(112.dp), onBack)
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiruroColors.CardHigh)
                ) {
                    episode.thumbnailUrl?.let { image ->
                        AsyncImage(
                            model = image,
                            contentDescription = episode.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(Modifier.weight(1.15f)) {
                    Text(
                        "Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}",
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        episode.title ?: "Episode ${episode.episodeNumber}",
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "AniStream will try every available provider automatically and switch sources if one fails. Resolutions and manual choices appear in Quality & Source during playback.",
                        color = MiruroColors.Subtle,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        if (providers.isNotEmpty()) {
            item {
                Text(
                    "Available providers",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(providers, key = { it }) { provider ->
                        GenreChip(provider.uppercase(Locale.ROOT))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Play episode", Modifier.width(210.dp), onPlay)
                    SecondaryButton(
                        if (saved?.watched == true) "Mark unwatched" else "Mark watched",
                        Modifier.width(210.dp)
                    ) {
                        viewModel.setEpisodeWatched(episode, saved?.watched != true)
                    }
                }
            }
        } else {
            item {
                StateMessage("No playable source is currently available for this episode.")
            }
        }
    }
}
