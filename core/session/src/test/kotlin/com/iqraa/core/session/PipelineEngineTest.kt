package com.iqraa.core.session

import com.iqraa.core.align.ExpectedWord
import com.iqraa.core.align.RecitationTrackerImpl
import com.iqraa.core.align.TrackerConfig
import com.iqraa.core.asr.AsrResult
import com.iqraa.core.asr.AsrWord
import com.iqraa.core.asr.ModelInfo
import com.iqraa.core.asr.RecitationRecognizer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T7 host-side engine test: PCM chunks → injectable VAD → REAL Segmenter → session.
 * The recognizer stays FAKE here (device-only sherpa paths are covered by
 * MicAndPipelineInstrumentedTest on the CI emulator).
 */
class PipelineEngineTest {
    /** Returns one fixed word per decode, at the window's start (model-space timings). */
    private class OneWordRecognizer(
        private val text: String,
    ) : RecitationRecognizer {
        override val info = ModelInfo("fake", "rev", "sha")

        override suspend fun load() {}

        override suspend fun decode(
            pcm: FloatArray,
            segmentStartMs: Long,
        ): AsrResult {
            val w = AsrWord(text, startMs = segmentStartMs, endMs = segmentStartMs + WORD_MS)
            return AsrResult(text, listOf(w), audioMs = pcm.size * MS_PER_S / SAMPLE_RATE, computeMs = 50)
        }

        override fun close() {}
    }

    private fun engineWith(
        vad: (FloatArray) -> Boolean,
        expected: List<ExpectedWord>,
    ): Pair<PipelineEngine, ReciteSession> {
        val tracker = RecitationTrackerImpl().apply { reset(expected, TrackerConfig()) }
        val session = ReciteSession(OneWordRecognizer("قل"), tracker)
        return PipelineEngine(SAMPLE_RATE, vad, session = session) to session
    }

    @Test
    fun `speech then silence produces partials and a pause-final checkpoint`(): Unit =
        runTest {
            val exp = listOf(ExpectedWord(0, 112, 1, "قل"))
            val (engine, session) = engineWith(vad = { samples -> samples.any { it > ENERGY } }, exp)
            // 3 s of speech (non-silent chunks), then 1 s of silence (zeros)
            repeat(SPEECH_CHUNKS) { engine.onChunk(FloatArray(CHUNK) { SPEECH_AMP }) }
            repeat(SILENCE_CHUNKS) { engine.onChunk(FloatArray(CHUNK)) }
            assertEquals(4_000L, engine.cursorMsPublic(), "session clock = fed audio")
            assertTrue(
                session.checkpoints.isNotEmpty(),
                "the pause-final must have fired (checkpoints=${session.checkpoints.size})",
            )
            assertTrue(
                session.updates.value != null,
                "partials must precede the final (decode-driven updates flowed)",
            )
        }

    @Test
    fun `silence-only audio never opens a segment`(): Unit =
        runTest {
            val exp = listOf(ExpectedWord(0, 112, 1, "قل"))
            val (engine, session) = engineWith(vad = { samples -> samples.any { it > ENERGY } }, exp)
            repeat(SILENCE_CHUNKS) { engine.onChunk(FloatArray(CHUNK)) }
            assertEquals(
                0,
                session.checkpoints.size,
                "no checkpoint without speech (checkpoints=${session.checkpoints.size})",
            )
        }

    /** Records every decode call (sample count + segment start) for V-046 assertions. */
    private class RecordingRecognizer : RecitationRecognizer {
        override val info = ModelInfo("fake", "rev", "sha")
        val calls = ArrayList<Pair<Int, Long>>() // (pcm samples, segmentStartMs)

        override suspend fun load() {}

        override suspend fun decode(
            pcm: FloatArray,
            segmentStartMs: Long,
        ): AsrResult {
            calls.add(pcm.size to segmentStartMs)
            return AsrResult("", emptyList(), audioMs = 0, computeMs = 50)
        }

        override fun close() {}
    }

    private fun engineWithRecorder(
        vad: (FloatArray) -> Boolean,
        minDecodeWindowMs: Long = 2_500L,
    ): Triple<PipelineEngine, RecordingRecognizer, ReciteSession> {
        val tracker = RecitationTrackerImpl().apply { reset(listOf(ExpectedWord(0, 112, 1, "قل")), TrackerConfig()) }
        val recognizer = RecordingRecognizer()
        val session = ReciteSession(recognizer, tracker)
        val engine = PipelineEngine(SAMPLE_RATE, vad, session = session, minDecodeWindowMs = minDecodeWindowMs)
        return Triple(engine, recognizer, session)
    }

    @Test
    fun `tiny pause-final decodes a widened slice, observation bounds stay honest`(): Unit =
        runTest {
            val (engine, recognizer, session) = engineWithRecorder(vad = { samples -> samples.any { it > ENERGY } })
            // 3 s of leading silence (ring context), 800 ms speech, 700 ms silence → pause-final
            repeat(30) { engine.onChunk(FloatArray(CHUNK)) }
            repeat(8) { engine.onChunk(FloatArray(CHUNK) { SPEECH_AMP }) }
            repeat(7) { engine.onChunk(FloatArray(CHUNK)) }
            // final fires at 4300: speech [3000, 3800] is 800 ms < 2500 → decode widened to
            // [2000, 4500] (lead 1000 + trail 700 = the 2500 ms partial shape, ring-clamped)
            assertEquals(listOf(40_000 to 2_000L), recognizer.calls, "one WIDENED final decode (2500 ms from 2000 ms)")
            assertEquals(3_800L, session.checkpoints.single().lastWindowEndMs, "honest observation bounds = speech end")
        }

    @Test
    fun `tiny final at session start clamps to available ring audio`(): Unit =
        runTest {
            val (engine, recognizer, session) = engineWithRecorder(vad = { samples -> samples.any { it > ENERGY } })
            // 900 ms of speech from t=0 (no leading context, no cadence partial at 1000 ms
            // yet), then the pause
            repeat(9) { engine.onChunk(FloatArray(CHUNK) { SPEECH_AMP }) }
            repeat(6) { engine.onChunk(FloatArray(CHUNK)) }
            // speech [0, 900]: deficit 1600 → trail 600 (pause elapsed), lead 0 (session start)
            assertEquals(listOf(24_000 to 0L), recognizer.calls, "widened only by the trailing pause (1500 ms)")
            assertEquals(900L, session.checkpoints.single().lastWindowEndMs, "honest bounds stay the 900 ms speech end")
        }

    @Test
    fun `wide final decodes the honest window unchanged`(): Unit =
        runTest {
            val (engine, recognizer, session) = engineWithRecorder(vad = { samples -> samples.any { it > ENERGY } })
            // 3 s of speech (≥ minDecodeWindowMs) then the pause → final NOT widened
            repeat(30) { engine.onChunk(FloatArray(CHUNK) { SPEECH_AMP }) }
            repeat(6) { engine.onChunk(FloatArray(CHUNK)) }
            val (finalSamples, finalStart) = recognizer.calls.last()
            assertEquals(48_000 to 0L, finalSamples to finalStart, "3 s final decodes the honest window")
            assertEquals(3_000L, session.checkpoints.single().lastWindowEndMs, "honest bounds = 3000 ms speech end")
            assertTrue(recognizer.calls.size > 1, "partials preceded the final (${recognizer.calls.size} decodes)")
        }

    @Test
    fun `minDecodeWindowMs zero disables widening (explicit opt-out)`(): Unit =
        runTest {
            val (engine, recognizer, session) =
                engineWithRecorder(vad = { samples -> samples.any { it > ENERGY } }, minDecodeWindowMs = 0)
            repeat(30) { engine.onChunk(FloatArray(CHUNK)) }
            repeat(8) { engine.onChunk(FloatArray(CHUNK) { SPEECH_AMP }) }
            repeat(7) { engine.onChunk(FloatArray(CHUNK)) }
            assertEquals(listOf(12_800 to 3_000L), recognizer.calls, "no widening: decode = the honest 800 ms speech window")
            assertEquals(3_800L, session.checkpoints.single().lastWindowEndMs, "honest bounds unchanged")
        }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK = 1_600
        const val ENERGY = 0.1f
        const val SPEECH_AMP = 0.5f
        const val SPEECH_CHUNKS = 30
        const val SILENCE_CHUNKS = 10
        const val WORD_MS = 350L
        const val MS_PER_S = 1_000L
    }
}
