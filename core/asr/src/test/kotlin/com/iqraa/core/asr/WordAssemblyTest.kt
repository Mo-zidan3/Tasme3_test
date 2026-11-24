package com.iqraa.core.asr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** §6.2 word assembly: ▁ separators, seconds→ms, +40 ms frame shift, null-timing fallback. */
class WordAssemblyTest {
    private fun assemble(
        tokens: Array<String>,
        ts: FloatArray,
        segmentStartMs: Long,
    ) = SherpaOnnxRecognizer.assembleStatic(tokens, ts, segmentStartMs)

    @Test
    fun `words assembled between separators with session-global ms and +40ms frame shift`() {
        val tokens = arrayOf("ب", "س", "▁", "م", "▁", "ا", "ل", "ل", "ه")
        val ts = floatArrayOf(0.10f, 0.14f, 0.18f, 0.22f, 0.30f, 0.34f, 0.38f, 0.42f, 0.46f)
        val words = assemble(tokens, ts, segmentStartMs = 1000L)
        assertEquals(listOf("بس", "م", "الله"), words.map { it.text })
        // بس: first token ts 0.10 s → 1000+100; last 0.14 → 1000+140+40
        assertEquals(1100L, words[0].startMs)
        assertEquals(1180L, words[0].endMs)
        // م: 0.22 → 1220; end 1220+40+... last 0.22 → 1260? = 1000+220+40
        assertEquals(1220L, words[1].startMs)
        assertEquals(1260L, words[1].endMs)
        // الله: first 0.34 → 1340; last 0.46 → 1000+460+40 = 1500
        assertEquals(1340L, words[2].startMs)
        assertEquals(1500L, words[2].endMs)
    }

    @Test
    fun `confidence is always null with sherpa-onnx (Q1-CLOSED)`() {
        val tokens = arrayOf("ق", "ل")
        val words = assemble(tokens, floatArrayOf(0.0f, 0.04f), 0L)
        assertEquals(1, words.size)
        assertNull(words[0].confidence, "no code path may depend on confidence (v2.0 delta #4)")
    }

    @Test
    fun `null-timing fallback when timestamps are empty`() {
        val tokens = arrayOf("ب", "س", "▁", "م")
        val words = assemble(tokens, floatArrayOf(), segmentStartMs = 500L)
        assertEquals(listOf("بس", "م"), words.map { it.text })
        words.forEach {
            assertNull(it.startMs, "null-timing fallback: no ms without timestamps")
            assertNull(it.endMs)
        }
    }

    @Test
    fun `null-timing fallback when timestamps are shorter than tokens (defensive)`() {
        val tokens = arrayOf("ق", "ل")
        val words = assemble(tokens, floatArrayOf(0.0f), 0L)
        assertEquals(1, words.size)
        assertNull(words[0].startMs)
    }

    @Test
    fun `trailing word without final separator is still flushed`() {
        val tokens = arrayOf("▁", "ا", "ل", "ل", "ه")
        val ts = floatArrayOf(0.0f, 0.10f, 0.14f, 0.18f, 0.22f)
        val words = assemble(tokens, ts, 0L)
        assertEquals(listOf("الله"), words.map { it.text })
        assertEquals(100L, words[0].startMs)
        assertEquals(260L, words[0].endMs) // 0.22s + 40ms
    }
}

/** V-045: the REAL model's vocab separates words with SPACE tokens, not "▁" — assembly
 *  must split on both (one giant word otherwise; found by the E2E fixture run 4533758818). */
class SpaceSeparatorAssemblyTest {
    @Test
    fun `space-separated tokens assemble into separate words`() {
        val tokens = arrayOf("ف", "د", "ع", "ا", " ", "ر", "ب", "ه", " ", "ا", "ن", "ي")
        val timestamps = floatArrayOf(0.1f, 0.15f, 0.2f, 0.25f, 0.3f, 0.4f, 0.45f, 0.5f, 0.55f, 0.65f, 0.7f, 0.75f)
        val words =
            com.iqraa.core.asr.SherpaOnnxRecognizer
                .assembleStatic(tokens, timestamps, 0)
        org.junit.jupiter.api.Assertions
            .assertEquals(3, words.size)
        org.junit.jupiter.api.Assertions
            .assertEquals(listOf("فدعا", "ربه", "اني"), words.map { it.text })
    }
}
