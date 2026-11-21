package com.example.tools.eval

import com.example.core.align.ExpectedWord
import com.example.core.align.model.Observation
import com.example.core.align.RecitationTrackerImpl
import com.example.core.align.TrackerConfig
import com.example.core.align.model.AsrWord
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
    println(report)
    val out = File("eval/reports/synthetic-report.md")
    out.parentFile.mkdirs()
    out.writeText(report.toString())
    println("written: ${out.path}")
}
