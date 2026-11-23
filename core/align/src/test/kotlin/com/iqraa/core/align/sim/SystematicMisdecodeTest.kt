package com.iqraa.core.align.sim

import com.iqraa.core.align.ExpectedWord
import com.iqraa.core.align.RecitationTrackerImpl
import com.iqraa.core.align.TrackerConfig
import com.iqraa.core.align.WordState
import com.iqraa.core.align.model.AsrWord
import com.iqraa.core.align.model.Observation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V-041/A2 (T2): stream-level systematic-misdecode through the REAL SegmenterSimulator.
 * The recognizer mishears ONE word (index 8) as the SAME wrong word in EVERY overlapping
 * window; only partials are fed. Under the old observation-level independence
 * (`any { independent(...) }`) a partial overlapping word 8 was accepted as an independent
 * vote because it was independent of SOME earlier window (e.g. the first partial), so
 * WRONG fired from mutually-overlapping partials. Per-word SET independence keeps the
 * counted list per word: a vote counts only when independent of EVERY counted window.
 */
class SystematicMisdecodeTest {
    private companion object {
        const val PITCH_MS = 400L
        const val WORD_MS = 350L
        const val MISDECODE_IDX = 8
        const val MISDECODE_AT_MS = 3200L

        /** independent window bounds: word-8 interior + overlap-independent of partial@4000 */
        const val INDEP_WINDOW_START = 2900L
        const val INDEP_WINDOW_END = 6000L

        /** padded end (4000+300) of the partial@4000 — the insertion anchor point */
        const val PARTIAL_AT_4000_END = 4300L
    }

    // 18 real Quranic words (surah 112 model-space vocabulary)
    private val words =
        listOf(
            "بسم",
            "الله",
            "الرحمن",
            "الرحيم",
            "قل",
            "هو",
            "الله",
            "احد",
            "الله",
            "الصمد",
            "لم",
            "يلد",
            "ولم",
            "يولد",
            "ولم",
            "يكن",
            "له",
            "كفوا",
        )

    /** index 8 («الله») persistently misdecoded to «الصمد» (sim 0.4 < τ_wrong 0.5). */
    private val misdecode = "الصمد"

    private fun exp() = words.mapIndexed { i, t -> ExpectedWord(i, 112, 1, t) }

    private fun stream(
        strict: Boolean,
        partialsOnly: Boolean = true,
    ): RecitationTrackerImpl {
        val sim = SegmenterSimulator(SegmenterConfig(vadPadMs = 300))
        val timed =
            words.mapIndexed { i, w ->
                TimedWord(if (i == MISDECODE_IDX) misdecode else w, i * PITCH_MS, i * PITCH_MS + WORD_MS)
            }
        val obs = sim.simulate(timed).let { if (partialsOnly) it.filter { o -> !o.isFinal } else it }
        val t = RecitationTrackerImpl()
        t.reset(exp(), TrackerConfig(strictOverlapIndependence = strict))
        for (o in obs) t.onObservation(o)
        return t
    }

    @Test
    fun `T2a systematic misdecode - word 8 NEVER WRONG in BOTH flag modes, count stays below 2`() {
        for (strict in listOf(false, true)) {
            val t = stream(strict)
            val s8 = t.snapshot().states[8]
            assertTrue(
                s8 == WordState.CLOSE || s8 == WordState.CORRECT,
                "strict=$strict: systematic misdecode on overlapping partials must stay CLOSE/CORRECT, got $s8",
            )
            assertEquals(
                WordState.CLOSE,
                s8,
                "strict=$strict: sim 0.4 < tauWrong with no independent second vote → CLOSE",
            )
            assertTrue(
                t.interiorIndependentCount(8) < 2,
                "strict=$strict: independent interior count must stay < 2 " +
                    "(was ${t.interiorIndependentCount(8)}; the old any{} rule reached 2 here)",
            )
        }
    }

    @Test
    fun `T2b a genuinely independent observation still reaches WRONG in BOTH flag modes`() {
        for (strict in listOf(false, true)) {
            val sim = SegmenterSimulator(SegmenterConfig(vadPadMs = 300))
            val timed =
                words.mapIndexed { i, w ->
                    TimedWord(if (i == MISDECODE_IDX) misdecode else w, i * PITCH_MS, i * PITCH_MS + WORD_MS)
                }
            val obs = sim.simulate(timed).filter { o -> !o.isFinal }
            val t = RecitationTrackerImpl()
            t.reset(exp(), TrackerConfig(strictOverlapIndependence = strict))
            // Insert the independent window right after the partial@4000 (pointer=10, so the
            // partial back-window [7..] still reaches word 8). Window [2900,6000]: word 8's
            // misdecode sits interior (3200 ≥ 2900+300, 3550 ≤ 6000−300) and it is
            // overlap-independent of the ONLY counted window for word 8 (partial@4000 =
            // [1200,4300]): overlap 1400×2 < shorter 3100 — independent under BOTH flags.
            val independentWindow =
                Observation(
                    words =
                        (MISDECODE_IDX - 1..MISDECODE_IDX + 4).map { i ->
                            AsrWord(if (i == MISDECODE_IDX) misdecode else words[i], i * PITCH_MS, i * PITCH_MS + WORD_MS)
                        },
                    windowStartMs = INDEP_WINDOW_START,
                    windowEndMs = INDEP_WINDOW_END,
                    isFinal = false,
                    modelRev = "synthetic",
                )
            var inserted = false
            for (o in obs) {
                t.onObservation(o)
                if (!inserted && o.windowEndMs == PARTIAL_AT_4000_END) {
                    t.onObservation(independentWindow)
                    inserted = true
                }
            }
            assertTrue(inserted, "the partial@4000 anchor point must exist in the stream")
            assertEquals(
                WordState.WRONG,
                t.snapshot().states[8],
                "strict=$strict: a genuinely independent interior vote must complete the 2-vote rule",
            )
            assertEquals(2, t.interiorIndependentCount(8), "strict=$strict: exactly 2 counted windows")
        }
    }

    @Test
    fun `T2c recognizer deletion of one word from one window stays CORRECT, never MISSED-WRONG`() {
        // word 8's audio DELETED from ONE mid-stream window (the first partial that contains
        // it); every other window decodes it normally. Current behavior must hold: the word
        // is matched by the other windows (CORRECT), and the single absence vote from the
        // deletion window can never push it to MISSED (needs ≥2 independent) or WRONG
        // (absence never feeds WRONG).
        val sim = SegmenterSimulator(SegmenterConfig(vadPadMs = 300))
        val timed = words.mapIndexed { i, w -> TimedWord(w, i * PITCH_MS, i * PITCH_MS + WORD_MS) }
        val obs = sim.simulate(timed).filter { o -> !o.isFinal }
        val t = RecitationTrackerImpl()
        t.reset(exp(), TrackerConfig())
        var deletedFromOne = false
        for (o in obs) {
            if (!deletedFromOne && o.words.any { it.startMs == MISDECODE_AT_MS }) {
                t.onObservation(o.copy(words = o.words.filter { it.startMs != MISDECODE_AT_MS }))
                deletedFromOne = true
            } else {
                t.onObservation(o)
            }
        }
        assertTrue(deletedFromOne, "the deletion window must have been fed")
        assertEquals(
            WordState.CORRECT,
            t.snapshot().states[8],
            "a word deleted from ONE window but decoded in others must be CORRECT (never MISSED/WRONG)",
        )
    }
}
