package com.example.core.audio

/**
 * §8.4 scheduling law (normative, v2.0 delta #5):
 *   effective partial cadence = max(partialEveryMs, RTF × W)
 * where W = the window being decoded (partialWindowMs for partials), RTF = measured
 * decode-time / audio-time. Inference is NON-INTERRUPTIBLE:
 *   - queue depth 1 (a new request replaces a queued, not-started one);
 *   - results whose windowEndMs is stale (an equal or newer windowEndMs was already emitted)
 *     are DROPPED.
 */
data class SchedulerConfig(
    val partialEveryMs: Long = 1000,
    val partialWindowMs: Long = 2500,
    val maxSegmentMs: Long = 8000,
)

/** One decode request as produced by the segmenter. */
data class DecodeRequest(
    val windowStartMs: Long,
    val windowEndMs: Long,
    val isFinal: Boolean,
)

/** One decode completion. */
data class DecodeResult(
    val windowStartMs: Long,
    val windowEndMs: Long,
    val isFinal: Boolean,
    val decodeDurationMs: Long,
)

/**
 * Pure host-testable scheduler. `rtf` is the measured real-time factor of the NEXT decode
 * (fake-able in tests). The clock is injected as [nowMs] per call.
 */
class Scheduler(private val config: SchedulerConfig) {

    private var lastDecidedAtMs: Long = Long.MIN_VALUE
    private var lastEmittedWindowEndMs: Long = Long.MIN_VALUE
    private val pending = ArrayDeque<DecodeRequest>() // queue depth 1

    /** Effective partial cadence for the current RTF (the law). */
    fun effectivePartialEveryMs(rtf: Double): Long {
        val decodeMs = Math.round(rtf * config.partialWindowMs)
        return maxOf(config.partialEveryMs, decodeMs)
    }

    /**
     * Feed the next request as the segmenter produces it; the scheduler keeps at most ONE
     * queued request (a newer replaces an older queued one — non-interruptible inference).
     */
    fun enqueue(request: DecodeRequest) {
        if (pending.isNotEmpty()) {
            val old = pending.removeFirst()
            // keep the NEWER request (by windowEnd); a FINAL never starves to a non-final
            if (old.isFinal && !request.isFinal) {
                pending.addLast(old) // queue stays depth 1 with the final
                return
            }
        }
        pending.addLast(request)
    }

    /** The queued request, or null. */
    fun peek(): DecodeRequest? = pending.firstOrNull()

    /** Pop the queued request when the inference thread is free (called by the pipeline). */
    fun take(): DecodeRequest? = pending.removeFirstOrNull()

    /**
     * A completed decode: drop the result if stale (an equal-or-newer windowEndMs was already
     * emitted), otherwise accept and mark progress.
     */
    fun onComplete(result: DecodeResult): Boolean {
        if (result.windowEndMs <= lastEmittedWindowEndMs && !result.isFinal) {
            return false // stale partial — dropped by windowEndMs (§6.2)
        }
        lastEmittedWindowEndMs = maxOf(lastEmittedWindowEndMs, result.windowEndMs)
        return true
    }

    /**
     * True when a partial may START decoding now (law: effective cadence elapsed since the last
     * decision). Records the decision time only when returning true (queries must be side-effect
     * free — the pipeline polls).
     */
    fun shouldDecodePartialAt(nowMs: Long, rtf: Double): Boolean {
        val cadence = effectivePartialEveryMs(rtf)
        val elapsed = if (lastDecidedAtMs == Long.MIN_VALUE) Long.MAX_VALUE else nowMs - lastDecidedAtMs
        if (elapsed >= cadence) {
            lastDecidedAtMs = nowMs
            return true
        }
        return false
    }
}
