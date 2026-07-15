package com.ttvralph.miruroapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchProgressTest {
    @Test
    fun roundTripProgressSerialization() {
        val progress = WatchProgress(
            animeId = 123,
            seasonNumber = 1,
            episodeNumber = 7,
            audioType = AudioType.SUB,
            positionMs = 900L,
            durationMs = 1_000L,
            updatedAtMs = 42L,
            sourceProvider = "zoro",
            sourceLabel = "ZORO 1080p",
            sourceId = "zoro:episode-7:1080p:HLS"
        )

        val decoded = WatchProgress.decode(progress.encoded())

        assertEquals(progress, decoded)
        assertEquals("123:1:7:SUB", progress.key)
        assertTrue(progress.watched)
    }

    @Test
    fun invalidProgressPayloadsAreIgnored() {
        assertNull(WatchProgress.decode("not|enough|parts"))
        assertNull(WatchProgress.decode("123|1|2|RAW|100|200|300"))
    }

    @Test
    fun watchedThresholdRequiresNinetyPercent() {
        assertFalse(WatchProgress(1, 1, 1, AudioType.DUB, 899L, 1_000L, 1L).watched)
        assertTrue(WatchProgress(1, 1, 1, AudioType.DUB, 900L, 1_000L, 1L).watched)
    }

    @Test
    fun legacyProgressWithoutSourceIdentityStillDecodes() {
        val decoded = WatchProgress.decode("123|1|7|SUB|900|1000|42|zoro|ZORO 1080p")

        assertEquals("zoro", decoded?.sourceProvider)
        assertEquals("ZORO 1080p", decoded?.sourceLabel)
        assertNull(decoded?.sourceId)
    }
}
