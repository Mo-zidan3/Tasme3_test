package com.example.core.align.sim

import com.example.core.align.model.AsrWord
import com.example.core.align.model.Observation

/**
 * SegmenterSimulator — ONE simulator, TWO consumers (core:audio unit tests +
 * tools:eval synthetic generation), so the observation stream the harness scores is the
 * SAME shape the app's segmenter will emit. No drift.
 *
 * Model of the app's audio pipeline (§6.2, §8.3, §8.4 — v2.0 deltas):
 *  - session-global ms timeline;
 *  - partials: trailing window of `partialWindowMs` (2500) sent every `partialEveryMs` (1000)
 *    — the SCHEDULER may push that out to max(partialEveryMs, RTF×W); the simulator emits the
 *    requested cadence and leaves RTF-stretching to the Scheduler under test;
 *  - segments end with a pause-final after `pauseFinalMs` (500) of silence;
 *  - force-cut final at `maxSegmentMs` (8000) with `overlapMs` (1000) carried into the next
 *    segment;
 *  - `isFinal` flags per §6.2;
 *  - optional VAD head/tail padding on windows.
 *
 * Words arrive as (text, startMs, endMs) on the session timeline; the simulator only cuts
 * OBSERVATION windows over them (it never fabricates word timings — the caller does, e.g. from
 * synthetic recitation timing at 350–400 ms/word).
 */
data class SegmenterConfig(
    val partialWindowMs: Long = 2500,
    val partialEveryMs: Long = 1000,
    val pauseFinalMs: Long = 500,
    val maxSegmentMs: Long = 8000,
    val overlapMs: Long = 1000,
    val vadPadMs: Long = 0,
    val modelRev: String = "synthetic",
)

data class TimedWord(val text: String, val startMs: Long, val endMs: Long) {
    constructor(text: String, start: Long) : this(text, start, start + 350)
}

class SegmenterSimulator(private val config: SegmenterConfig = SegmenterConfig()) {

    /**
     * Emits the observation stream for a recitation of [words] (already timed on the session
     * timeline), with pauses encoded as gaps between word end and the next word start:
     * a gap ≥ pauseFinalMs ends the segment (pause-final), silence between partials is kept.
     */
    fun simulate(words: List<TimedWord>): List<Observation> {
        if (words.isEmpty()) return emptyList()
        val out = ArrayList<Observation>()
        var segmentStart = words.first().startMs
        var i = 0
        while (i < words.size) {
            // segment boundaries: pause gap ≥ pauseFinalMs OR accumulated length ≥ maxSegmentMs
            var j = i
            while (j + 1 < words.size) {
                val gap = words[j + 1].startMs - words[j].endMs
                val segLen = words[j + 1].endMs - segmentStart
                if (gap >= config.pauseFinalMs || segLen >= config.maxSegmentMs) break
                j++
            }
            val segEnd = words[j].endMs
            emitSegment(words, i, j, segmentStart, segEnd, isFinal = true, out)
            // force-cut carries overlap into the next "segment start"
            val cutForced = (segEnd - segmentStart) >= config.maxSegmentMs
            segmentStart = if (cutForced) segEnd - config.overlapMs else words.getOrNull(j + 1)?.startMs ?: segEnd
            i = j + 1
        }
        return out
    }

    /**
     * For one segment [i..j]: pause-final observation covering the WHOLE segment, plus
     * partial observations every partialEveryMs over the trailing partialWindowMs.
     * Partials are emitted in timeline order BEFORE the final.
     */
    private fun emitSegment(
        words: List<TimedWord>,
        i: Int,
        j: Int,
        segmentStart: Long,
        segmentEnd: Long,
        isFinal: Boolean,
        out: MutableList<Observation>,
    ) {
        val segWords = words.subList(i, j + 1)
        // partials: t = window END. First partial fires at segmentStart + partialEveryMs.
        var t = segmentStart + config.partialEveryMs
        while (t < segmentEnd) {
            val wStart = maxOf(segmentStart, t - config.partialWindowMs)
            val inWindow = segWords.filter { it.endMs > wStart && it.startMs < t }
            out.add(
                Observation(
                    words = inWindow.map { AsrWord(it.text, it.startMs, it.endMs) },
                    windowStartMs = wStart - config.vadPadMs,
                    windowEndMs = t + config.vadPadMs,
                    isFinal = false,
                    modelRev = config.modelRev,
                ),
            )
            t += config.partialEveryMs
        }
        // final covers the whole segment
        out.add(
            Observation(
                words = segWords.map { AsrWord(it.text, it.startMs, it.endMs) },
                windowStartMs = segmentStart - config.vadPadMs,
                windowEndMs = segmentEnd + config.vadPadMs,
                isFinal = isFinal,
                modelRev = config.modelRev,
            ),
        )
    }
}
