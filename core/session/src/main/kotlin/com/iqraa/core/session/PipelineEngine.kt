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
    capacityMs: Long = DEFAULT_CAPACITY_MS,
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
            val (startMs, endMs, isFinal) =
                when (e) {
                    is SegmenterEvent.Partial -> Triple(e.windowStartMs, e.windowEndMs, false)
                    is SegmenterEvent.Final -> Triple(e.windowStartMs, e.windowEndMs, true)
                }
            updates.add(session.onWindow(slice(startMs, endMs), startMs, endMs, isFinal))
        }
        return updates
    }

    /** Session clock (ms) — test hook. */
    fun cursorMsPublic(): Long = cursorMs

    private companion object {
        const val MS_PER_SECOND = 1000L
        const val DEFAULT_CAPACITY_MS = 12_000L
    }
}
