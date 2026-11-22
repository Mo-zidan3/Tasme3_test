package com.iqraa.core.audio

/**
 * §6.2 segmenter: turns VAD speech segments into decode requests.
 *  - a trailing partial window (`partialWindowMs`) is emitted every `partialEveryMs` while
 *    speech continues (the scheduler may stretch cadence by RTF×W — that is Scheduler's job);
 *  - the segment ENDS at a pause ≥ `pauseFinalMs` of silence → a FINAL covering the whole
 *    segment;
 *  - or at `maxSegmentMs` → force-cut FINAL with `overlapMs` carried into the next segment.
 */
data class SegmenterParams(
    val partialWindowMs: Long = 2500,
    val partialEveryMs: Long = 1000,
    val pauseFinalMs: Long = 500,
    val maxSegmentMs: Long = 8000,
    val overlapMs: Long = 1000,
    val vadPadMs: Long = 0,
)

sealed interface SegmenterEvent {
    data class Partial(val windowStartMs: Long, val windowEndMs: Long) : SegmenterEvent
    data class Final(val windowStartMs: Long, val windowEndMs: Long, val forcedCut: Boolean) : SegmenterEvent
}

/** Pure host-testable segmenter fed by VAD frames. */
class Segmenter(private val params: SegmenterParams = SegmenterParams()) {

    private var segmentStartMs: Long = -1
    private var lastSpeechEndMs: Long = -1
    private var lastPartialAtMs: Long = -1
    private var inSpeech = false
    private val out = ArrayList<SegmenterEvent>()

    /**
     * Feed one VAD decision covering [startMs, endMs) (isSpeech).
     * Produces Partial/Final events on the session timeline.
     */
    fun onVad(startMs: Long, endMs: Long, isSpeech: Boolean): List<SegmenterEvent> {
        out.clear()
        if (isSpeech) {
            if (!inSpeech) {
                inSpeech = true
                // speech onset: carry overlap from a force-cut
                segmentStartMs = if (segmentStartMs < 0) startMs else maxOf(segmentStartMs, startMs - params.overlapMs)
                lastPartialAtMs = segmentStartMs
            }
            lastSpeechEndMs = endMs
            // partials at fixed cadence while speech runs
            var t = lastPartialAtMs + params.partialEveryMs
            while (t <= endMs) {
                out.add(SegmenterEvent.Partial(maxOf(segmentStartMs, t - params.partialWindowMs), t))
                lastPartialAtMs = t
                t += params.partialEveryMs
            }
            // force-cut when the segment exceeds maxSegmentMs
            if (endMs - segmentStartMs >= params.maxSegmentMs) {
                out.add(SegmenterEvent.Final(segmentStartMs, endMs, forcedCut = true))
                segmentStartMs = endMs - params.overlapMs
                lastPartialAtMs = segmentStartMs
            }
        } else if (inSpeech) {
            // silence after speech: pause-final once pauseFinalMs of silence has ELAPSED
            // (the silence frame itself covers [lastSpeechEndMs, endMs])
            if (endMs - lastSpeechEndMs >= params.pauseFinalMs) {
                out.add(SegmenterEvent.Final(segmentStartMs, lastSpeechEndMs, forcedCut = false))
                inSpeech = false
                segmentStartMs = -1
                lastPartialAtMs = -1
            }
        }
        return out.toList()
    }
}
