package com.example.core.align

import com.example.core.align.model.AsrWord
import com.example.core.align.model.Observation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackerContractTest {

    private fun obs(start: Long, end: Long, isFinal: Boolean = false, rev: String = "r1") = Observation(
        words = emptyList(), windowStartMs = start, windowEndMs = end, isFinal = isFinal, modelRev = rev,
    )

    @Test
    fun `identity is the normative 4-tuple`() {
        val a = obs(0, 1000)
        val b = obs(0, 1000)
        assertEquals(a.identity(), b.identity())
        val c = obs(0, 1000, isFinal = true)
        assertFalse(a.identity() == c.identity())
    }

    @Test
    fun `disjoint windows are independent`() {
        assertTrue(independent(obs(0, 2000), obs(3000, 5000)))
    }

    @Test
    fun `low overlap under 50 percent of shorter window is independent`() {
        // windows [0..4000] and [1900..4000]: overlap 2100, shorter 2100 → 2*2100 >= 2100 → not independent
        assertFalse(independent(obs(0, 4000), obs(1900, 4000)))
        // windows [0..4000] and [2200..4000]: overlap 1800, shorter 1800 → not independent
        assertFalse(independent(obs(0, 4000), obs(2200, 4000)))
        // windows [0..4000] and [2400..4000]: overlap 1600 = the whole shorter window (100%) → NOT independent
        assertFalse(independent(obs(0, 4000), obs(2400, 4000)))
    }

    @Test
    fun `final observation with differing window is independent`() {
        // identical window, one final: windows do not differ → NOT independent (idempotency rule)
        assertFalse(independent(obs(0, 4000), obs(0, 4000, isFinal = true)))
        // slightly different window + final → independent
        assertTrue(independent(obs(0, 4000), obs(100, 4000, isFinal = true)))
    }

    @Test
    fun `asrword confidence defaults to null`() {
        val w = AsrWord(text = "abc", startMs = 0, endMs = 100)
        assertEquals(null, w.confidence)
    }

    @Test
    fun `tracker config defaults match spec 9_1`() {
        val c = TrackerConfig()
        assertEquals(3, c.backWords)
        assertEquals(15, c.lookaheadWords)
        assertEquals(0.9, c.gapMissed)
        assertEquals(0.7, c.gapExtra)
        assertEquals(2, c.kEdgeTokens)
        assertEquals(300L, c.boundaryMs)
        assertEquals(0.90, c.tauCorrect)
        assertEquals(0.50, c.tauWrong)
        assertEquals(TrackerConfig.Preset.CONSERVATIVE, c.preset)
    }
}
