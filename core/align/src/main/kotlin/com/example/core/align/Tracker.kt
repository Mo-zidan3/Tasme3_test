package com.example.core.align

import com.example.core.align.model.AsrWord
import com.example.core.align.model.Observation

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
    val text: String,      // normalized model-space letters
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
    val preset: Preset = Preset.CONSERVATIVE,
) {
    enum class Preset { CONSERVATIVE, BALANCED }
}

/** Snapshot returned after each observation/hint/reanchor (§6.2). */
data class TrackerUpdate(
    val states: Map<Int, WordState>,
    val pointer: Int,
    val extras: List<AsrWord>,
)

/** §6.2 interface — implementation lands in Phase 1 (§16). */
interface RecitationTracker {
    fun reset(expected: List<ExpectedWord>, config: TrackerConfig)
    fun onObservation(obs: Observation): TrackerUpdate
    fun hint(): TrackerUpdate
    fun reanchor(ayahIndex: Int): TrackerUpdate
}

/** §9.2 observation identity — (windowStartMs, windowEndMs, isFinal, modelRev). */
fun Observation.identity(): String = "$windowStartMs:$windowEndMs:$isFinal:$modelRev"

/** §9.2 independence: overlap < 50% of the shorter window, OR one final and the windows differ. */
fun independent(a: Observation, b: Observation): Boolean {
    val overlapStart = maxOf(a.windowStartMs, b.windowStartMs)
    val overlapEnd = minOf(a.windowEndMs, b.windowEndMs)
    val overlap = (overlapEnd - overlapStart).coerceAtLeast(0)
    val shorter = minOf(a.windowEndMs - a.windowStartMs, b.windowEndMs - b.windowStartMs)
    if (overlap * 2 < shorter) return true
    // "windows differ" = (start,end) bounds differ — isFinal is part of identity, not of the window.
    val sameBounds = a.windowStartMs == b.windowStartMs && a.windowEndMs == b.windowEndMs
    return (a.isFinal || b.isFinal) && !sameBounds
}
