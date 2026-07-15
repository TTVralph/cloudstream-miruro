package com.ttvralph.miruroapp

import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.PlaybackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodePlaybackStateTest {
    private val source = PlaybackSource(
        url = "https://example.test/episode.m3u8",
        label = "TEST 1080p",
        type = PlaybackType.HLS
    )

    @Test
    fun matchingEpisodeReceivesResolvedState() {
        val episode = episode(number = 2)
        val resolved = EpisodePlaybackState(episode.playbackKey(), UiState.Success(source))

        assertEquals(UiState.Success(source), resolved.stateFor(episode))
    }

    @Test
    fun nextEpisodeNeverReceivesPreviousEpisodeStream() {
        val previous = episode(number = 2)
        val next = episode(number = 3)
        val resolved = EpisodePlaybackState(previous.playbackKey(), UiState.Success(source))

        assertNull(resolved.stateFor(next))
    }

    @Test
    fun subAndDubVariantsHaveDifferentPlaybackKeys() {
        val sub = episode(number = 2, audio = AudioType.SUB)
        val dub = episode(number = 2, audio = AudioType.DUB)
        val resolved = EpisodePlaybackState(sub.playbackKey(), UiState.Success(source))

        assertNull(resolved.stateFor(dub))
    }

    @Test
    fun decorativeMetadataDoesNotChangePlaybackIdentity() {
        val original = episode(number = 2)
        val updated = original.copy(
            title = "A late title",
            releaseDate = "2026-07-15",
            thumbnailUrl = "https://example.test/new-image.webp"
        )

        assertEquals(original.playbackKey(), updated.playbackKey())
    }

    private fun episode(number: Int, audio: AudioType = AudioType.SUB) = AnimeEpisode(
        seasonNumber = 1,
        episodeNumber = number,
        title = "Episode $number",
        thumbnailUrl = null,
        runtimeMinutes = 24,
        releaseDate = null,
        audioType = audio,
        anilistId = 123
    )
}
