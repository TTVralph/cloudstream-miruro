package com.ttvralph.miruroapp

import com.ttvralph.miruroapp.data.AnimeSeason
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.EpisodeSourceCandidate
import com.ttvralph.miruroapp.data.PlaybackSource
import com.ttvralph.miruroapp.data.PlaybackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackReliabilityTest {
    @Test
    fun autoplayCountdownUsesDeadlineInsteadOfChainedTicks() {
        assertEquals(10, hotfixAutoplaySecondsRemaining(deadlineMs = 10_000L, nowMs = 0L))
        assertEquals(9, hotfixAutoplaySecondsRemaining(deadlineMs = 10_000L, nowMs = 1_001L))
        assertEquals(1, hotfixAutoplaySecondsRemaining(deadlineMs = 10_000L, nowMs = 9_999L))
        assertEquals(0, hotfixAutoplaySecondsRemaining(deadlineMs = 10_000L, nowMs = 10_000L))
    }

    @Test
    fun nextUnloadedSeasonChoosesTheNearestLaterSeason() {
        val seasons = listOf(
            season(number = 1, loaded = true),
            season(number = 3, loaded = false),
            season(number = 2, loaded = false)
        )

        assertEquals(2, nextUnloadedSeasonNumber(seasons, currentSeason = 1))
    }

    @Test
    fun loadedAndEarlierSeasonsAreNotRequestedAgain() {
        val seasons = listOf(
            season(number = 1, loaded = true),
            season(number = 2, loaded = true)
        )

        assertNull(nextUnloadedSeasonNumber(seasons, currentSeason = 1))
    }

    @Test
    fun nextEpisodeDoesNotJumpOverAnUnloadedSeason() {
        val seasons = listOf(
            season(number = 1, loaded = true, episodes = listOf(episode(1, 12))),
            season(number = 2, loaded = false),
            season(number = 3, loaded = true, episodes = listOf(episode(3, 1)))
        )

        assertNull(
            nextPlayableEpisode(
                seasons,
                currentSeason = 1,
                currentEpisode = 12,
                requestedAudio = AudioType.SUB,
                preferredAudio = AudioType.SUB
            )
        )
    }

    @Test
    fun nextEpisodeUsesTheNearestLoadedSeasonAndRequestedAudio() {
        val dub = episode(2, 1, AudioType.DUB)
        val sub = episode(2, 1, AudioType.SUB)
        val result = nextPlayableEpisode(
            seasons = listOf(
                season(number = 1, loaded = true, episodes = listOf(episode(1, 12))),
                season(number = 2, loaded = true, episodes = listOf(sub, dub))
            ),
            currentSeason = 1,
            currentEpisode = 12,
            requestedAudio = AudioType.DUB,
            preferredAudio = AudioType.SUB
        )

        assertEquals(dub, result)
    }

    @Test
    fun onlyTransportAndParsingFailuresTriggerProviderFallback() {
        assertEquals(true, hotfixShouldFallbackSource(2_001))
        assertEquals(true, hotfixShouldFallbackSource(3_002))
        assertEquals(false, hotfixShouldFallbackSource(4_003))
        assertEquals(false, hotfixShouldFallbackSource(5_001))
    }

    @Test
    fun exactSourceIdentityWinsWhenResuming() {
        val sources = listOf(
            source(id = "zoro:episode:server-a:HLS", label = "ZORO 1080p"),
            source(id = "zoro:episode:server-b:HLS", label = "ZORO 1080p")
        )

        assertEquals(
            1,
            hotfixResumeSourceIndex(
                sources = sources,
                sourceId = "zoro:episode:server-b:HLS",
                sourceProvider = "zoro",
                sourceLabel = "ZORO 1080p"
            )
        )
    }

    private fun season(
        number: Int,
        loaded: Boolean,
        episodes: List<AnimeEpisode> = emptyList()
    ) = AnimeSeason(
        id = number,
        seasonNumber = number,
        title = "Season $number",
        year = 2026,
        episodes = episodes,
        episodesLoaded = loaded
    )

    private fun episode(
        season: Int,
        number: Int,
        audio: AudioType = AudioType.SUB
    ) = AnimeEpisode(
        seasonNumber = season,
        episodeNumber = number,
        title = "Episode $number",
        audioType = audio,
        sourceCandidates = listOf(
            EpisodeSourceCandidate("provider", "$season-$number-${audio.name}", audio.name.lowercase())
        ),
        anilistId = 1
    )

    private fun source(id: String, label: String) = PlaybackSource(
        url = "https://example.test/$id.m3u8",
        label = label,
        type = PlaybackType.HLS,
        providerId = "zoro",
        sourceId = id
    )
}
