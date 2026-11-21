package com.example.core.session

import com.example.core.asr.AsrResult
import com.example.core.asr.AsrWord
import com.example.core.asr.RecitationRecognizer
import com.example.core.align.ExpectedWord
import com.example.core.align.model.Observation
import com.example.core.align.RecitationTracker
import com.example.core.align.TrackerUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 2 host-side orchestration (§6.3): decode requests in → observations → tracker updates.
 *
 * The recognizer is behind [RecitationRecognizer] (fake-able in tests). After each FINAL
 * observation a [checkpoint] snapshot is emitted (§11: partial summary survives process death;
 * no audio/transcripts persisted).
 */
class ReciteSession(
    private val recognizer: RecitationRecognizer,
    private val tracker: RecitationTracker,
) {
    /** Latest tracker snapshot for the UI (§6.3 StateFlow contract — host-side). */
    private val _updates = MutableStateFlow<TrackerUpdate?>(null)
    val updates: StateFlow<TrackerUpdate?> get() = _updates

    /** Checkpoint after each FINAL observation (§11). */
    data class Checkpoint(
        val finalCount: Int,
        val lastWindowEndMs: Long,
        val update: TrackerUpdate,
    )

    private val _checkpoints = ArrayList<Checkpoint>()
    val checkpoints: List<Checkpoint> get() = _checkpoints

    private var finalCount = 0

    /** Decode one window (the audio layer supplies the request cadence per §8.4). */
    suspend fun onWindow(pcm: FloatArray, windowStartMs: Long, windowEndMs: Long, isFinal: Boolean): TrackerUpdate {
        val result: AsrResult = recognizer.decode(pcm, segmentStartMs = windowStartMs)
        // bridge core:asr words → core:align words (same fields; confidence never used, v2.0 #4)
        val words = result.words.map {
            com.example.core.align.model.AsrWord(text = it.text, startMs = it.startMs, endMs = it.endMs)
        }
        val obs = Observation(
            words = words,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            isFinal = isFinal,
            modelRev = recognizer.info.id,
        )
        val update = tracker.onObservation(obs)
        _updates.value = update
        if (isFinal) {
            finalCount++
            _checkpoints.add(Checkpoint(finalCount, windowEndMs, update))
        }
        return update
    }
}

/**
 * Calibration (§8.5): 3 decode probes of the SAME probe clip, discard the first (warm-up), take
 * the MEDIAN compute time → RTF estimate; chooses the model; drives the degraded ladder L0–L3.
 */
class Calibration(
    private val probeMs: Long = 3000,
) {
    data class Result(
        val rtf: Double,
        val probesMs: List<Long>,
        val level: DegradedLevel,
        val modelChoice: ModelChoice,
    )

    /** Probe timings in ms (3 probes of probeMs audio each). */
    fun calibrate(computeTimesMs: List<Long>): Result {
        require(computeTimesMs.size >= 3) { "need 3 probes" }
        val valid = computeTimesMs.drop(1) // discard first (warm-up)
        val median = valid.sorted().let { it[it.size / 2] }
        val rtf = median.toDouble() / probeMs
        val level = when {
            rtf <= 0.5 -> DegradedLevel.L0
            rtf <= 0.9 -> DegradedLevel.L1
            rtf <= 1.4 -> DegradedLevel.L2
            else -> DegradedLevel.L3
        }
        val modelChoice = when {
            rtf <= 0.9 -> ModelChoice.SMALL
            else -> ModelChoice.TINY
        }
        return Result(rtf = rtf, probesMs = computeTimesMs, level = level, modelChoice = modelChoice)
    }
}

/** §8.6 degraded ladder. Triggers are MEASURED (RTF from calibration), never guessed. */
enum class DegradedLevel(val label: String, val badge: Boolean) {
    L0("كامل", badge = false),
    L1("مُتأخر قليلاً", badge = false),
    L2("الوضع الخفيف", badge = true),
    L3("الوضع الخفيف", badge = true),
}

enum class ModelChoice(val modelId: String) {
    SMALL("quran-asr-small"),
    TINY("quran-asr-tiny"),
}
