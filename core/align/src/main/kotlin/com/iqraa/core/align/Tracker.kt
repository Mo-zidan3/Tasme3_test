package com.iqraa.core.align

import com.iqraa.core.align.model.AsrWord
import com.iqraa.core.align.model.Observation

/** §6.2 normative contract — word-level states derived by the tracker. */
enum class WordState {
    PENDING,
    CORRECT,
    CLOSE,
    WRONG,
    MISSED,
    HINTED,
    EXTRA,
}

/** Expected word to track (from core:quran normalized table, Phase 1). */
data class ExpectedWord(
    val index: Int,
    val surah: Int,
    val ayah: Int,
    /** normalized model-space letters */
    val text: String,
    /** §7.6: optional-prefix words (basmala) — absorbable when recited, never MISSED,
     *  excluded from Y (tasmee' summary). */
    val optionalPrefix: Boolean = false,
)

/** §9.1 initial values — all tunables live in TrackerConfig (F4: no magic numbers). */
data class TrackerConfig(
    val backWords: Int = 3,
    val lookaheadWords: Int = 15,
    val gapMissed: Double = 0.9,
    val gapExtra: Double = 0.7,
    val kEdgeTokens: Int = 2,
    val boundaryMs: Long = 300,
    val tauCorrect: Double = 0.90,
    val tauWrong: Double = 0.50,
    val reanchorSim: Double = 0.90,
    val lostAfterMs: Long = 6000,
    val minReanchorRun: Int = 3,
    /** §4 (V-031): when true, independence ignores the isFinal clause — a FINAL over the same
     *  audio as a partial is NOT independent (strict overlap rule). Default FALSE (spec rule:
     *  final + windows differ ⇒ independent; V-025 measured that a partial + the final = 2
     *  votes, which fires WRONG within one segment). Numbers for Mo before the §13 gate. */
    val strictOverlapIndependence: Boolean = false,
    val preset: Preset = Preset.CONSERVATIVE,
) {
    enum class Preset { CONSERVATIVE, BALANCED }
}

/**
 * Token→wall-clock timing contract (§6.2), verified on host (V-015):
 * sherpa-onnx timestamps are SECONDS; token frame shift is 40 ms
 * (10 ms hop × subsampling_factor 4, model metadata + measured deltas).
 *
 * word.startMs = segmentStartMs + firstTokenTs * 1000
 * word.endMs   = segmentStartMs + lastTokenTs * 1000 + FRAME_SHIFT_MS
 */
const val FRAME_SHIFT_MS: Long = 40

/** Snapshot returned after each observation/hint/reanchor (§6.2). */
data class TrackerUpdate(
    val states: Map<Int, WordState>,
    val pointer: Int,
    val extras: List<AsrWord>,
)

/** §6.2 interface — implementation lands in Phase 1 (§16). */
interface RecitationTracker {
    fun reset(
        expected: List<ExpectedWord>,
        config: TrackerConfig,
    )

    fun onObservation(obs: Observation): TrackerUpdate

    fun hint(): TrackerUpdate

    fun reanchor(ayahIndex: Int): TrackerUpdate

    /** V-048 (issue #11): the §9.6 summary — «صحيحة: X من Y» (X = CORRECT only; Y =
     *  expected − HINTED − optional basmala). On the interface so session-level consumers
     *  (ReciteSession) can surface it without depending on the concrete tracker. */
    fun summary(): TasmeeSummary
}

/** §9.2 observation identity — (windowStartMs, windowEndMs, isFinal, modelRev). */
fun Observation.identity(): String = "$windowStartMs:$windowEndMs:$isFinal:$modelRev"

/** §9.2 independence: overlap < 50% of the shorter window, OR one final and the windows differ. */
fun independent(
    a: Observation,
    b: Observation,
    strictOverlap: Boolean = false,
): Boolean {
    val overlapStart = maxOf(a.windowStartMs, b.windowStartMs)
    val overlapEnd = minOf(a.windowEndMs, b.windowEndMs)
    val overlap = (overlapEnd - overlapStart).coerceAtLeast(0)
    val shorter = minOf(a.windowEndMs - a.windowStartMs, b.windowEndMs - b.windowStartMs)
    if (overlap * 2 < shorter) return true
    if (strictOverlap) return false // strict mode: heavily-overlapping windows are NEVER independent
    // "windows differ" = (start,end) bounds differ — isFinal is part of identity, not of the window.
    val sameBounds = a.windowStartMs == b.windowStartMs && a.windowEndMs == b.windowEndMs
    return (a.isFinal || b.isFinal) && !sameBounds
}
