package com.example.core.session

import com.example.core.asr.AsrResult
import com.example.core.asr.AsrWord
import com.example.core.asr.ModelInfo
import com.example.core.asr.RecitationRecognizer
import com.example.core.align.ExpectedWord
import com.example.core.align.RecitationTrackerImpl
import com.example.core.align.TrackerConfig
import com.example.core.align.TrackerUpdate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Phase 2 host-side session orchestration with a FAKE recognizer (P3). */
class ReciteSessionTest {

    private class FakeRecognizer(
        private val wordsPerWindow: Map<Long, List<AsrWord>>, // windowStartMs → words
    ) : RecitationRecognizer {
        override val info = ModelInfo("fake", "rev", "sha")
        override suspend fun load() {}
        var decodeCalls = 0
        override suspend fun decode(pcm: FloatArray, segmentStartMs: Long): AsrResult {
            decodeCalls++
            val words = wordsPerWindow[segmentStartMs] ?: emptyList()
            return AsrResult(
                text = words.joinToString(" ") { it.text },
                words = words,
                audioMs = pcm.size * 1000L / 16000,
                computeMs = 50,
            )
        }
        override fun close() {}
    }

    private fun word(text: String, start: Long, end: Long) = AsrWord(text, start, end)

    @Test
    fun `fake recognizer drives the tracker and emits updates`() = runTest {
        val expected = listOf(
            ExpectedWord(0, 112, 1, "قل"),
            ExpectedWord(1, 112, 1, "هو"),
            ExpectedWord(2, 112, 1, "الله"),
            ExpectedWord(3, 112, 1, "احد"),
        )
        val fake = FakeRecognizer(
            mapOf(
                0L to listOf(word("قل", 300, 900), word("هو", 900, 1500), word("الله", 1500, 2100), word("احد", 2100, 2700)),
            ),
        )
        val tracker = RecitationTrackerImpl()
        tracker.reset(expected, TrackerConfig())
        val session = ReciteSession(fake, tracker)

        val update = session.onWindow(ByteArray(16000 * 3).let { FloatArray(it.size) }, 0, 3000, isFinal = true)
        assertEquals(1, fake.decodeCalls)
        // pause-final + interior words → CORRECT everywhere per V-025 semantics
        val correct = update.states.count { it.value == com.example.core.align.WordState.CORRECT }
        assertTrue(correct >= 2, "fake recognizer words must become CORRECT; got ${update.states}")
    }

    @Test
    fun `checkpoint emitted after each FINAL only`() = runTest {
        val expected = (0 until 4).map { ExpectedWord(it, 112, 1, "w$it") }
        val fake = FakeRecognizer(
            mapOf(
                0L to listOf(word("w0", 300, 600), word("w1", 600, 900)),
                4000L to listOf(word("w2", 4300, 4600), word("w3", 4600, 4900)),
            ),
        )
        val tracker = RecitationTrackerImpl()
        tracker.reset(expected, TrackerConfig())
        val session = ReciteSession(fake, tracker)

        session.onWindow(FloatArray(16000), 0, 1000, isFinal = false)
        assertTrue(session.checkpoints.isEmpty(), "no checkpoint before a FINAL")
        session.onWindow(FloatArray(16000 * 2), 0, 2000, isFinal = true)
        assertEquals(1, session.checkpoints.size)
        session.onWindow(FloatArray(16000 * 2), 4000, 6000, isFinal = true)
        assertEquals(2, session.checkpoints.size)
        // checkpoints carry the §11 partial summary (update snapshot)
        assertEquals(1, session.checkpoints[0].finalCount)
        assertEquals(2, session.checkpoints[1].finalCount)
    }

    @Test
    fun `updates StateFlow exposes the latest snapshot`() = runTest {
        val expected = listOf(ExpectedWord(0, 112, 1, "قل"))
        val fake = FakeRecognizer(mapOf(0L to listOf(word("قل", 300, 900))))
        val tracker = RecitationTrackerImpl()
        tracker.reset(expected, TrackerConfig())
        val session = ReciteSession(fake, tracker)
        assertEquals(null, session.updates.value)
        session.onWindow(FloatArray(16000), 0, 1000, isFinal = true)
        assertTrue(session.updates.value != null, "StateFlow must expose the latest TrackerUpdate")
    }
}

class CalibrationTest {

    private val cal = Calibration(probeMs = 3000)

    @Test
    fun `median of probes 2 and 3 (first discarded) sets RTF`() {
        // probes of 3000 ms audio: 100 (warm-up outlier), 1500, 1400 ms → median(1500,1400)=1500? no:
        // valid = [1500, 1400]; sorted [1400,1500]; median = element[1] = 1500 → RTF 0.5
        val r = cal.calibrate(listOf(100, 1500, 1400))
        assertEquals(0.5, r.rtf, 1e-9)
    }

    @Test
    fun `ladder levels by measured RTF`() {
        assertEquals(DegradedLevel.L0, cal.calibrate(listOf(100, 1500, 1500)).level)  // RTF 0.5
        assertEquals(DegradedLevel.L1, cal.calibrate(listOf(100, 2400, 2700)).level)  // RTF 0.9
        assertEquals(DegradedLevel.L2, cal.calibrate(listOf(100, 3500, 4200)).level)  // RTF 1.4
        assertEquals(DegradedLevel.L3, cal.calibrate(listOf(100, 6000, 5800)).level)   // RTF ~2
    }

    @Test
    fun `badge only from L2 (light mode per 8-6 and 10-5)`() {
        assertTrue(!DegradedLevel.L0.badge && !DegradedLevel.L1.badge)
        assertTrue(DegradedLevel.L2.badge && DegradedLevel.L3.badge)
        assertEquals("الوضع الخفيف", DegradedLevel.L2.label)
    }

    @Test
    fun `model choice small until RTF 0-9 then tiny`() {
        assertEquals(ModelChoice.SMALL, cal.calibrate(listOf(100, 1500, 1400)).modelChoice) // 0.5
        assertEquals(ModelChoice.SMALL, cal.calibrate(listOf(100, 2400, 2700)).modelChoice) // 0.9
        assertEquals(ModelChoice.TINY, cal.calibrate(listOf(100, 3500, 4200)).modelChoice) // 1.4
    }

    @Test
    fun `requires three probes`() {
        val ex = runCatching { cal.calibrate(listOf(1, 2)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }
}
