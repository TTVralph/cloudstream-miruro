package com.ttvralph.miruroapp

import com.ttvralph.miruroapp.data.SkipInterval
import com.ttvralph.miruroapp.data.SkipKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkipPromptStateTest {
    private val intro = SkipInterval(
        kind = SkipKind.INTRO,
        startMs = 60_000L,
        endMs = 90_000L
    )

    @Test
    fun skippedIntervalBecomesAvailableAgainAfterRewinding() {
        var dismissed: SkipInterval? = intro

        dismissed = dismissed.hotfixDismissedAt(90_250L)
        assertNull(dismissed)

        val prompt = listOf(intro)
            .hotfixActiveAt(75_000L)
            ?.takeUnless { it == dismissed }
        assertEquals(intro, prompt)
    }

    @Test
    fun dismissedPromptStaysHiddenUntilPlayheadLeavesItsInterval() {
        val dismissed = intro.hotfixDismissedAt(75_000L)

        assertEquals(intro, dismissed)
    }
}
