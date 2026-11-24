package com.iqraa.core.session

import com.iqraa.core.audio.Segmenter
import com.iqraa.core.audio.SegmenterEvent

/**
 * T7: the REAL pipeline engine — mic/infile PCM chunks in → VAD decisions → [Segmenter]
 * events → window slices off the session ring → [ReciteSession.onWindow].
 * Host-testable: [vad] and [session] are injectable fakes on the JVM; on device the
 * ViewModel feeds real [com.iqraa.core.audio.AudioCapture] chunks, the silero VAD and the
 * real recognizer-backed session.
 *
 * The engine keeps a session-global PCM ring ([capacityMs]); windows are sliced by sample
 * bounds and are EMPTY when their audio already rotated out (the caller still forwards them —
 * the tracker treats empty decodes as silence windows, §9.4 lost timer).
 */
class PipelineEngine(
    private val sampleRate: Int,
    private val vad: (samples: FloatArray) -> Boolean,
    private val segmenter: Segmenter = Segmenter(),
    private val session: ReciteSession,
    /** §8.4 caps the session ring at 30 s; T7 sized it 12 s — the LONGEST window the
     *  segmenter can request (maxSegment 8 s + overlap 1 s) plus VAD padding and cadence
     *  jitter margin. Configurable (D-020 ADR line: 12 s default, spec ceiling 30 s). */
    capacityMs: Long = DEFAULT_CAPACITY_MS,
    /** V-046 candidate (a) (Mo-approved): a pause-FINAL whose SPEECH spans less than this
     *  is decoded with EXTRA leading/trailing context drawn from the session ring — the
     *  same window shape partials already use (default = the partial window width).
     *  V-046 root cause: pause-heavy VAD fragmentation fed the CTC isolated 0.7–4.3 s
     *  bursts and isolated tiny-window decodes hallucinate; whole-clip decode of the same
     *  audio is near-perfect, so per-window CONTEXT is the missing piece. The
     *  OBSERVATION's word-evidence bounds stay the honest segmenter bounds — context
     *  words fall outside them and §9.2 interiority discounts them; no §9 threshold,
     *  preset, or default changes (I4). Forced-cut finals are never widened (they only
     *  fire at ≥ maxSegmentMs, never tiny). 0 disables widening. */
    private val minDecodeWindowMs: Long = DEFAULT_MIN_DECODE_WINDOW_MS,
) {
    private val capacity = (capacityMs * sampleRate / MS_PER_SECOND).toInt()
    private val ring = FloatArray(capacity)
    private var ringStart = 0 // absolute sample index of ring[0]
    private var ringCount = 0
    private var sessionSamples = 0L // absolute samples fed
    private var cursorMs = 0L

    /** Absolute sample index → ms on the session timeline. */
    private fun sampleToMs(idx: Long): Long = idx * MS_PER_SECOND / sampleRate

    private fun append(samples: FloatArray) {
        for (s in samples) {
            ring[(ringStart + ringCount) % capacity] = s
            if (ringCount == capacity) ringStart = (ringStart + 1) % capacity else ringCount++
            sessionSamples++
        }
    }

    /** Slices [startMs, endMs) off the ring; empty when the range rotated out. */
    private fun slice(
        startMs: Long,
        endMs: Long,
    ): FloatArray {
        val from = startMs * sampleRate / MS_PER_SECOND
        val to = endMs * sampleRate / MS_PER_SECOND
        if (from < 0 || to > sessionSamples || to <= from) return FloatArray(0)
        val oldest = sessionSamples - ringCount
        if (from < oldest) return FloatArray(0)
        val out = FloatArray((to - from).toInt())
        var i = 0
        for (abs in from until to) {
            out[i++] = ring[((abs % capacity)).toInt()]
        }
        return out
    }

    /**
     * Feed one PCM chunk (any size; the caller's cadence defines VAD decision granularity).
     * Returns the tracker updates produced by the windows this chunk triggered (may be empty).
     */
    suspend fun onChunk(samples: FloatArray): List<com.iqraa.core.align.TrackerUpdate> {
        if (samples.isEmpty()) return emptyList()
        val chunkStartMs = cursorMs
        append(samples)
        cursorMs = sampleToMs(sessionSamples)
        val isSpeech = vad(samples)
        val events = segmenter.onVad(chunkStartMs, cursorMs, isSpeech)
        val updates = ArrayList<com.iqraa.core.align.TrackerUpdate>(events.size)
        for (e in events) {
            when (e) {
                is SegmenterEvent.Partial -> {
                    updates.add(
                        session.onWindow(slice(e.windowStartMs, e.windowEndMs), e.windowStartMs, e.windowEndMs, isFinal = false),
                    )
                }

                is SegmenterEvent.Final -> {
                    // V-046 candidate (a): a TINY pause-final decodes a WIDER slice off the
                    // ring (leading + trailing context, the partial shape) — the Observation
                    // bounds passed on stay the HONEST segmenter bounds.
                    val decode =
                        if (!e.forcedCut && e.windowEndMs - e.windowStartMs < minDecodeWindowMs) {
                            widenDecodeWindow(e.windowStartMs, e.windowEndMs)
                        } else {
                            e.windowStartMs to e.windowEndMs
                        }
                    updates.add(
                        session.onWindow(
                            slice(decode.first, decode.second),
                            e.windowStartMs,
                            e.windowEndMs,
                            isFinal = true,
                            decodeStartMs = decode.first,
                        ),
                    )
                }
            }
        }
        return updates
    }

    /**
     * V-046: widen a tiny pause-final's DECODE slice with session-ring context. The deficit
     * splits trailing-first (the pause's own silence — the CTC right context), then leading
     * (the previous segment's tail), and is clamped by what the ring/session actually holds:
     * the widened window is always within [oldest ring sample, cursor], so [slice] never
     * degrades to empty because of widening. Word evidence bounds are NOT affected.
     */
    private fun widenDecodeWindow(
        startMs: Long,
        endMs: Long,
    ): Pair<Long, Long> {
        val deficit = minDecodeWindowMs - (endMs - startMs)
        if (deficit <= 0) return startMs to endMs
        val oldestMs = sampleToMs(sessionSamples - ringCount)
        val trailMs = (minOf(deficit / 2, cursorMs - endMs)).coerceAtLeast(0)
        val leadMs = minOf(deficit - trailMs, (startMs - oldestMs).coerceAtLeast(0))
        return (startMs - leadMs) to (endMs + trailMs)
    }

    /** Session clock (ms) — test hook. */
    fun cursorMsPublic(): Long = cursorMs

    private companion object {
        const val MS_PER_SECOND = 1000L
        const val DEFAULT_CAPACITY_MS = 12_000L

        /** D-021: the partial window shape (SegmenterParams.partialWindowMs = 2500) — tiny
         *  finals decode over the SAME width partials do, no wider. */
        const val DEFAULT_MIN_DECODE_WINDOW_MS = 2_500L
    }
}
