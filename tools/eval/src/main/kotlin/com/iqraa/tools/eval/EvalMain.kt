package com.iqraa.tools.eval

import com.iqraa.core.align.ExpectedWord
import com.iqraa.core.align.model.Observation
import com.iqraa.core.align.RecitationTrackerImpl
import com.iqraa.core.align.TrackerConfig
import com.iqraa.core.align.WordState
import com.iqraa.core.align.model.AsrWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * tools:eval — §13.2 harness entry. Consumes hypotheses JSON produced by eval/run_asr.py
 * (or synthesized), replays them through the SAME core:align code as the app, and emits a
 * metrics report. Label: results on synthetic hypotheses are "not measured on real audio".
 */

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("usage: eval <hypotheses.json> <expected-words.json>")
        println("  hypotheses.json: {clips:[{clipId, observations:[{words:[[text,startMs,endMs]],windowStartMs,windowEndMs,isFinal}]}]}")
        println("  expected-words.json: {surah, fromAyah, toAyah, words:[...]} (model-space, via N())")
        return
    }
    val json = Json { prettyPrint = true }
    val hypoRoot = json.parseToJsonElement(File(args[0]).readText()) as? JsonObject ?: error("hypotheses root must be an object")
    val expRoot = json.parseToJsonElement(File(args[1]).readText()) as? JsonObject
        ?: error("expected-words root must be an object")
    val clips = hypoRoot["clips"] as? JsonArray ?: error("missing clips[]")
    val expWords = (expRoot["words"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?: error("missing words[]")

    val surah = (expRoot["surah"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
    val fromAyah = (expRoot["fromAyah"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
    val expected = expWords.mapIndexed { i, w -> ExpectedWord(i, surah, fromAyah, w) }

    val report = StringBuilder()
    report.appendLine("# tools:eval report — synthetic hypotheses (NOT measured on real audio)")
    report.appendLine("expected words: ${expected.size}; preset: ${TrackerConfig().preset}; model space: uthmani-raw")
    var totalCorrect = 0
    var totalClose = 0
    var totalWrong = 0
    var totalMissed = 0
    var totalHinted = 0

    for (clipEl in clips) {
        val clip = clipEl as? JsonObject ?: continue
        val clipId = (clip["clipId"] as? JsonPrimitive)?.content ?: "?"
        val tracker = RecitationTrackerImpl()
        tracker.reset(expected, TrackerConfig())
        val obsArr = clip["observations"] as? JsonArray ?: continue
        for (obsEl in obsArr) {
            val obsO = obsEl as? JsonObject ?: continue
            val words = (obsO["words"] as? JsonArray ?: continue).mapNotNull { wel ->
                val w = wel as? JsonArray
                AsrWord(
                    text = (w?.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null,
                    startMs = (w?.getOrNull(1) as? JsonPrimitive)?.content?.toLongOrNull(),
                    endMs = (w?.getOrNull(2) as? JsonPrimitive)?.content?.toLongOrNull(),
                )
            }
            val o = Observation(
                words = words,
                windowStartMs = (obsO["windowStartMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0,
                windowEndMs = (obsO["windowEndMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0,
                isFinal = (obsO["isFinal"] as? JsonPrimitive)?.content == "true",
                modelRev = (obsO["modelRev"] as? JsonPrimitive)?.content ?: "synthetic",
            )
            tracker.onObservation(o)
        }
        val s = tracker.summary()
        totalCorrect += s.correct
        totalClose += s.close
        totalWrong += s.wrong
        totalMissed += s.missed
        totalHinted += s.hinted
        report.appendLine(
            "clip $clipId: correct=${s.correct} close=${s.close} wrong=${s.wrong} missed=${s.missed} hinted=${s.hinted} extra=${s.extra} | ${s.headline}",
        )
    }
    report.appendLine("TOTAL: correct=$totalCorrect close=$totalClose wrong=$totalWrong missed=$totalMissed hinted=$totalHinted")

    // v2.0 metrics on synthetic streams (SIMULATION — labeled):
    //  - tracking hit-rate: % of correctly-recited words reaching CORRECT/CLOSE
    //  - word-highlight latency: simulated audio-end-of-word → UI state (final/partial that
    //    made it non-PENDING), bounded by the observation stream itself
    report.appendLine()
    report.appendLine("## v2.0 metrics (SIMULATION — not measured on real audio)")
    var hitNumerator = 0
    var hitDenominator = 0
    val latencies = ArrayList<Long>()
    for (clipEl in clips) {
        val clip = clipEl as? JsonObject ?: continue
        val clipId = (clip["clipId"] as? JsonPrimitive)?.content ?: "?"
        val tracker = RecitationTrackerImpl()
        tracker.reset(expected, TrackerConfig())
        val obsArr = clip["observations"] as? JsonArray ?: continue
        var lastStates: Map<Int, WordState> = emptyMap()
        val wordEndTimes = HashMap<Int, Long>() // expected index -> word endMs
        for (obsEl in obsArr) {
            val obsO = obsEl as? JsonObject ?: continue
            val obsEnd = (obsO["windowEndMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0
            val words = (obsO["words"] as? JsonArray ?: continue).mapNotNull { wel ->
                val w = wel as? JsonArray
                AsrWord(
                    text = (w?.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null,
                    startMs = (w?.getOrNull(1) as? JsonPrimitive)?.content?.toLongOrNull(),
                    endMs = (w?.getOrNull(2) as? JsonPrimitive)?.content?.toLongOrNull(),
                )
            }
            val o = Observation(
                words = words,
                windowStartMs = (obsO["windowStartMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0,
                windowEndMs = obsEnd,
                isFinal = (obsO["isFinal"] as? JsonPrimitive)?.content == "true",
                modelRev = (obsO["modelRev"] as? JsonPrimitive)?.content ?: "synthetic",
            )
            val before = tracker.snapshot().states
            tracker.onObservation(o)
            lastStates = tracker.snapshot().states
            // simulated latency: first observation that took the word out of PENDING.
            // Duplicate-text words (الله ×3, ولم ×2, احد ×2 in surah 112): use the occurrence
            // INSIDE this flipping observation (the last one with endMs ≤ windowEnd) — NOT the
            // first occurrence globally, which fabricated multi-second latencies (V-031).
            for ((idx, st) in lastStates) {
                if (st != WordState.PENDING && before[idx] == WordState.PENDING) {
                    val text = expected.getOrNull(idx)?.text ?: continue
                    val end = words.filter { it.text == text }
                        .filter { (it.endMs ?: Long.MAX_VALUE) <= obsEnd }
                        .maxByOrNull { it.endMs ?: 0L }?.endMs ?: continue
                    latencies.add(obsEnd - (end ?: 0L))
                }
            }
            for (w in words) {
                val idx = expected.indexOfFirst { it.text == w.text && w.endMs != null && !wordEndTimes.containsKey(expected.indexOfFirst { it.text == w.text }) }
                if (idx >= 0 && w.endMs != null) wordEndTimes.putIfAbsent(idx, w.endMs!!)
            }
        }
        // hit-rate: correctly-recited words (expected, non-hinted, not substituted in this clip)
        // reaching CORRECT/CLOSE by the end
        val s = tracker.summary()
        run {
            val correctRecited = s.correct + s.close
            hitNumerator += correctRecited
            hitDenominator += s.y
        }
        report.appendLine("clip $clipId hit-rate: ${"%.1f".format(100.0 * (s.correct + s.close) / maxOf(1, s.y))}%")
    }
    val hitRate = if (hitDenominator > 0) 100.0 * hitNumerator / hitDenominator else 0.0
    report.appendLine("TRACKING HIT-RATE (simulated): ${"%.1f".format(hitRate)}% ($hitNumerator/$hitDenominator)")
    if (latencies.isNotEmpty()) {
        val sorted = latencies.sorted()
        val p50 = sorted[sorted.size / 2]
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
        report.appendLine("WORD-HIGHLIGHT LATENCY (simulated): p50=${p50}ms p95=${p95}ms [SLO 1500ms] — SIMULATION of the observation stream only; on-device latency (§13.3) is NOT measured here")
    }
    println(report)
    val out = File("eval/reports/synthetic-report.md")
    out.parentFile.mkdirs()
    out.writeText(report.toString())
    println("written: ${out.path}")
}
