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
