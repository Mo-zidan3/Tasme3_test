package com.example.core.align.sim

import com.example.core.align.RecitationTrackerImpl
import com.example.core.align.TrackerConfig
import com.example.core.align.WordState
import com.example.core.align.ExpectedWord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * P2: the simulator must emit the app-shaped stream, and it settles the trailing-EXTRA question.
 * Vocabulary = REAL Quranic words (surah 112, model space) — the earlier w0..w18 synthetic words
 * had sim=0.5 between EVERY pair (pathological at the tauWrong boundary); Quranic words behave
 * like production.
 */
class SegmenterSimulatorTest {

    // surah 112 normalized (eval/expected-words.json, V-020)
    private val quranWords = listOf(
        "بسم", "الله", "الرحمن", "الرحيم", "قل", "هو", "الله", "احد", "الله",
        "الصمد", "لم", "يلد", "ولم", "يولد", "ولم", "يكن", "له", "كفوا", "احد",
    )

    private fun exp() = quranWords.mapIndexed { i, t -> ExpectedWord(i, 112, 1, t) }

    @Test
    fun `cadence and windows follow the law`() {
        val sim = SegmenterSimulator(SegmenterConfig(vadPadMs = 300))
        val words = ArrayList<TimedWord>()
        var t = 0L
        for (i in quranWords.indices) {
            words.add(TimedWord(quranWords[i], t, t + 350))
            t += if (i == 9) 400 + 900L /* long pause → pause-final boundary */ else 400
        }
        val obs = sim.simulate(words)
        val partials = obs.filter { !it.isFinal }
        assertTrue(partials.isNotEmpty())
        // consecutive partial windowEnds differ by exactly partialEveryMs WITHIN a segment
        // (partials reset at segment boundaries — app behavior)
        val ends = partials.map { it.windowEndMs }.sorted()
        val gaps = ends.zipWithNext { a, b -> b - a }.filter { it > 0 }
        val inSegment = gaps.filter { it <= 2500L }
        assertTrue(inSegment.all { it == 1000L }, "within-segment partial cadence must be 1000 ms; got $inSegment")
        // partial windows are ≤ partialWindowMs + 2×vadPad (early partials truncate at segment start)
        assertTrue(partials.all { it.windowEndMs - it.windowStartMs <= 2500L + 600L })
        // every word is covered exactly once by finals
        val allFinalWords = obs.filter { it.isFinal }.flatMap { it.words }
        assertEquals(quranWords.size, allFinalWords.size, "final observations cover every word exactly once")
    }

    @Test
    fun `TRAILING-EXTRA question settled - segment-final words earn CORRECT on realistic streams`() {
        // V-025: the earlier report's trailing-EXTRA was an ARTIFACT of the whole-clip single
        // final, compounded by the aligner aligning finals against pointer−backWords only.
        // With the lo=0 fix for finals + partials + VAD padding, ALL words reach CORRECT.
        val sim = SegmenterSimulator(SegmenterConfig(vadPadMs = 300))
        val words = quranWords.mapIndexed { i, w -> TimedWord(w, i * 400L, i * 400L + 350) }
        val obs = sim.simulate(words)
        val tracker = RecitationTrackerImpl()
        tracker.reset(exp(), TrackerConfig())
        for (o in obs) tracker.onObservation(o)
        val states = tracker.snapshot().states
        val correct = states.count { it.value == WordState.CORRECT }
        assertEquals(quranWords.size, correct, "all words CORRECT on the app-shaped stream (was 16/19 with the whole-clip artifact)")
    }

    @Test
    fun `MISSED fires on the app-shaped stream when a word is skipped`() {
        val sim = SegmenterSimulator(SegmenterConfig(vadPadMs = 300))
        // skip يلد (idx 11) but KEEP its time slot silent — partials on both sides bracket it
        val words = quranWords.mapIndexed { i, w -> TimedWord(w, i * 400L, i * 400L + 350) }
            .filterIndexed { i, _ -> i != 11 }
        val obs = sim.simulate(words)
        val tracker = RecitationTrackerImpl()
        tracker.reset(exp(), TrackerConfig())
        for (o in obs) tracker.onObservation(o)
        val states = tracker.snapshot().states
        assertEquals(WordState.MISSED, states[11], "MISSED must fire on the app-shaped stream (never fired on whole-clip)")
    }

    @Test
    fun `WRONG fires on the app-shaped stream for a persistent substitution (Balanced preset)`() {
        val sim = SegmenterSimulator(SegmenterConfig(vadPadMs = 300))
        // Persistently substitute يكن (idx 15) with كين (sim 0.333 — a plausible ASR slip
        // on a 3-letter word; 1-letter slips on 2- and 3-letter words sit at/above tauWrong
        // and correctly produce CLOSE, not WRONG).
        // V-025 finding, corrected by measurement: the FINAL is itself an INDEPENDENT interior
        // observation (final + windows differ, §9.2), so a substitution interior in one partial
        // AND the final reaches ≥2 independent interior votes → WRONG fires under BOTH presets
        // within a single segment. (The earlier رحيم case stayed CLOSE only because a MERGE
        // pushed its bestSim to exactly tauWrong=0.5 — a threshold boundary, not a rule.)
        // 1-letter slips on 2–3-letter words (sim ≥ 0.5) correctly produce CLOSE, not WRONG.
        val words = quranWords.mapIndexed { i, w ->
            val text = if (i == 15) "كين" else w
            TimedWord(text, i * 400L, i * 400L + 350)
        }
        val obs = sim.simulate(words)
        val tracker = RecitationTrackerImpl()
        tracker.reset(exp(), TrackerConfig(preset = TrackerConfig.Preset.BALANCED))
        for (o in obs) tracker.onObservation(o)
        assertEquals(WordState.WRONG, tracker.snapshot().states[15], "WRONG (Balanced, single FINAL interior) must fire on the app-shaped stream")
        // Conservative fires too (partial + final = 2 independent interior votes):
        val tracker2 = RecitationTrackerImpl()
        tracker2.reset(exp(), TrackerConfig())
        for (o in obs) tracker2.onObservation(o)
        assertEquals(WordState.WRONG, tracker2.snapshot().states[15], "WRONG (Conservative, ≥2 independent interior) must fire on the app-shaped stream")
    }
}
