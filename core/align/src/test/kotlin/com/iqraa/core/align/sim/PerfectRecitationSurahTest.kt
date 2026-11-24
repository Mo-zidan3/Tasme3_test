package com.iqraa.core.align.sim

import com.iqraa.core.align.ExpectedWord
import com.iqraa.core.align.RecitationTrackerImpl
import com.iqraa.core.align.TrackerConfig
import com.iqraa.core.align.WordState
import com.iqraa.core.quran.ExpectedWords
import com.iqraa.core.quran.QuranText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V-041/A1 (T3): property-style perfect-recognizer sweep over REAL surahs from the
 * committed Tanzil asset, through the REAL SegmenterSimulator (app-shaped partial+final
 * stream). Surah 55 carries 31 refrains and surah 77 carries 10 after N() — the repeated
 * ayahs that exposed the refrain false alarms. After the last segment, no word may be
 * MISSED/WRONG/CLOSE; both basmala variants (recited / omitted) must hold —
 * OPTIONAL_PREFIX words are never penalized.
 * ALL numbers here are SYNTHETIC (simulator, not real audio) — the §13 phone-mic gate
 * stays NOT met; this is a tracker-invariant test, not an accuracy measurement.
 */
class PerfectRecitationSurahTest {
    private companion object {
        const val PITCH_MS = 400L
        const val WORD_MS = 350L
        const val BASMALA_WORDS = 4
    }

    private val sim = SegmenterSimulator(SegmenterConfig(vadPadMs = 300))

    private fun expectedFor(surah: Int): List<ExpectedWord> {
        val ayahs = QuranText.load().filter { it.surah == surah }
        assertTrue(ayahs.isNotEmpty(), "surah $surah must exist in the committed asset")
        val entries = ExpectedWords.forRange(ayahs, surah, 1, ayahs.maxOf { it.ayah })
        return entries.mapIndexed { i, e -> ExpectedWord(i, e.surah, e.ayah, e.text, e.optionalPrefix) }
    }

    private fun run(
        surah: Int,
        reciteOptionalBasmala: Boolean,
    ): RecitationTrackerImpl {
        val expected = expectedFor(surah)
        val recited =
            if (reciteOptionalBasmala) {
                expected
            } else {
                expected.filter { !it.optionalPrefix }
            }
        val timed = recited.mapIndexed { i, w -> TimedWord(w.text, i * PITCH_MS, i * PITCH_MS + WORD_MS) }
        val obs = sim.simulate(timed)
        val t = RecitationTrackerImpl()
        t.reset(expected, TrackerConfig())
        for (o in obs) t.onObservation(o)
        return t
    }

    private fun assertAllClean(
        t: RecitationTrackerImpl,
        surah: Int,
        expectedCount: Int,
        optionalCount: Int,
        variant: String,
    ) {
        val states = t.snapshot().states
        val bad = states.filterValues { it == WordState.MISSED || it == WordState.WRONG || it == WordState.CLOSE }
        assertTrue(
            bad.isEmpty(),
            "surah $surah [$variant]: perfect recitation must leave NO MISSED/WRONG/CLOSE; bad=$bad",
        )
        val correct = states.count { it.value == WordState.CORRECT }
        assertEquals(
            expectedCount - optionalCount,
            correct,
            "surah $surah [$variant]: every recited word CORRECT (optional basmala words stay PENDING when omitted)",
        )
        assertEquals(expectedCount, t.pointerPublic(), "surah $surah [$variant]: pointer consumes the surah")
    }

    @Test
    fun `T3 surah 55 - 31 refrains - basmala recited`() {
        val exp = expectedFor(55)
        val optional = exp.count { it.optionalPrefix }
        assertEquals(BASMALA_WORDS, optional, "surah 55 carries the 4-word optional basmala prefix")
        assertAllClean(run(55, reciteOptionalBasmala = true), 55, exp.size, 0, "basmala recited")
    }

    @Test
    fun `T3 surah 55 - 31 refrains - basmala omitted, never penalized`() {
        val exp = expectedFor(55)
        assertAllClean(run(55, reciteOptionalBasmala = false), 55, exp.size, BASMALA_WORDS, "basmala omitted")
    }

    @Test
    fun `T3 surah 77 - 10 refrains - both basmala variants`() {
        val exp = expectedFor(77)
        val optional = exp.count { it.optionalPrefix }
        assertEquals(BASMALA_WORDS, optional, "surah 77 carries the 4-word optional basmala prefix")
        assertAllClean(run(77, reciteOptionalBasmala = true), 77, exp.size, 0, "basmala recited")
        assertAllClean(run(77, reciteOptionalBasmala = false), 77, exp.size, optional, "basmala omitted")
    }

    @Test
    fun `T3 surah 112 and surah 1 - both basmala variants (surah 1 basmala is MANDATORY)`() {
        val exp112 = expectedFor(112)
        assertAllClean(run(112, reciteOptionalBasmala = true), 112, exp112.size, 0, "basmala recited")
        assertAllClean(run(112, reciteOptionalBasmala = false), 112, exp112.size, BASMALA_WORDS, "basmala omitted")

        // surah 1's basmala is ayah 1:1 itself (mandatory, NOT optional) — only the recited variant applies
        val exp1 = expectedFor(1)
        assertEquals(0, exp1.count { it.optionalPrefix }, "surah 1 basmala is mandatory, not optional")
        assertAllClean(run(1, reciteOptionalBasmala = true), 1, exp1.size, 0, "mandatory basmala recited")
    }
}
