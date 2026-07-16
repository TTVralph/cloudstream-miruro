package com.ttvralph.miruroapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MediaIdentityTest {
    @Test
    fun selectedAniListEntryRemainsTheOnlyPlaybackIdentity() {
        val season = exactPlaybackSeason(
            id = 195678,
            title = "JUJUTSU KAISEN Season 3: The Culling Game Part 1",
            year = 2026,
            synopsis = "The Culling Game begins.",
            episodeCount = 12,
            runtimeMinutes = 24
        )

        assertEquals(195678, season.id)
        assertEquals(1, season.seasonNumber)
        assertEquals("JUJUTSU KAISEN Season 3: The Culling Game Part 1", season.title)
        assertEquals(12, season.episodeCount)
        assertFalse(season.episodesLoaded)
    }
}
