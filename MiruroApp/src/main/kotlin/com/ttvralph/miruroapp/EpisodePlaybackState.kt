package com.ttvralph.miruroapp

import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.PlaybackSource

/** Identifies the exact episode variant that owns a playback resolution result. */
data class EpisodePlaybackKey(
    val animeId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val audioType: AudioType
)

/** Keeps a resolved stream from being consumed by a different player destination. */
data class EpisodePlaybackState(
    val key: EpisodePlaybackKey,
    val state: UiState<PlaybackSource>
)

fun AnimeEpisode.playbackKey(): EpisodePlaybackKey = EpisodePlaybackKey(
    animeId = anilistId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    audioType = audioType
)

fun EpisodePlaybackState?.stateFor(episode: AnimeEpisode): UiState<PlaybackSource>? =
    this?.takeIf { it.key == episode.playbackKey() }?.state
