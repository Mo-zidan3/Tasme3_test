package com.iqraa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.iqraa.core.align.ExpectedWord
import com.iqraa.core.align.RecitationTrackerImpl
import com.iqraa.core.align.TrackerConfig
import com.iqraa.core.align.WordState
import com.iqraa.core.asr.ModelInfo
import com.iqraa.core.asr.SherpaOnnxRecognizer
import com.iqraa.core.audio.AudioCapture
import com.iqraa.core.audio.Segmenter
import com.iqraa.core.quran.ExpectedWords
import com.iqraa.core.quran.QuranText
import com.iqraa.core.session.PipelineEngine
import com.iqraa.core.session.ReciteSession
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream

/**
 * T7 (B1): the REAL pipeline on the CI emulator —
 *  - micCapture_smoke: real AudioRecord opens, records, reads PCM (silence is fine);
 *  - pipeline_endToEnd_fixture: Mo's committed 54:10 fixture through the REAL silero VAD →
 *    REAL Segmenter → REAL SherpaOnnxRecognizer → REAL tracker; CORRECT/CLOSE states and the
 *    §9.6 summary. EMULATOR numbers, not a phone (Q7 stays OPEN).
 */
@RunWith(AndroidJUnit4::class)
class MicAndPipelineInstrumentedTest {
    @get:Rule
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @Test
    fun micCapture_smoke_stateRecording_andReadsPcm() {
        val cap = AudioCapture()
        val opened = cap.start()
        assertTrue(
            "mic must open on the emulator with RECORD_AUDIO granted (state=${cap.state})",
            opened,
        )
        assertEquals(AudioCapture.State.RECORDING, cap.state)
        val chunk = cap.readChunk()
        assertTrue("must read >0 samples (silence is fine; got ${chunk.size})", chunk.isNotEmpty())
        assertTrue(
            "PCM16→float must stay in [-1,1]",
            chunk.all { it in -1.0f..1.0f },
        )
        cap.stop()
        assertEquals(AudioCapture.State.STOPPED, cap.state)
    }

    @Test
    fun pipeline_endToEnd_fixture_realVadSegmenterRecognizerTracker(): Unit = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = repoAsset("eval/fixtures/fixture.wav")
        org.junit.Assume.assumeTrue(
            "SKIPPED (loudly): fixture absent — CI copies the committed 54:10 clip into androidTest assets",
            fixture != null,
        )

        // expected words: al-Qamar 54:10 from the committed Tanzil asset
        val ayahs = QuranText.load().filter { it.surah == SURAH_QAMAR && it.ayah == AYAH_10 }
        val entries = ExpectedWords.forRange(ayahs, SURAH_QAMAR, AYAH_10, AYAH_10)
        assertTrue("54:10 must resolve to words", entries.isNotEmpty())
        val expected =
            entries.mapIndexed { i, e ->
                ExpectedWord(i, e.surah, e.ayah, e.text, e.optionalPrefix)
            }

        val recognizer =
            SherpaOnnxRecognizer(
                context = ctx,
                info =
                    ModelInfo(
                        id = "quran-asr-small",
                        revision = "0c2744b9d70aaf45ba574c30c5d318c575258e66",
                        sha256 = "88f9df290d57b8890275d79187b77515aeeb12626a42bc76cbd0f8bfcaff00f0",
                    ),
            )
        recognizer.load()
        try {
            val tracker = RecitationTrackerImpl().apply { reset(expected, TrackerConfig()) }
            val session = ReciteSession(recognizer, tracker)
            val vad =
                Vad(
                    assetManager = ctx.assets,
                    config =
                        VadModelConfig().apply {
                            sampleRate = SAMPLE_RATE
                            sileroVadModelConfig.model = VAD_ASSET
                        },
                )
            val engine =
                PipelineEngine(
                    sampleRate = SAMPLE_RATE,
                    vad = { samples -> vad.compute(samples) > VAD_SPEECH_THRESHOLD },
                    segmenter = Segmenter(),
                    session = session,
                )

            val pcm = readMono16kPcm(fixture!!)
            // feed in 100 ms chunks (the AudioCapture cadence), then 1 s of trailing silence
            // so the segmenter emits the pause-final over the whole clip
            var i = 0
            while (i < pcm.size) {
                val end = minOf(i + CHUNK_SAMPLES, pcm.size)
                engine.onChunk(pcm.copyOfRange(i, end))
                i = end
            }
            repeat(TRAILING_SILENCE_CHUNKS) { engine.onChunk(FloatArray(CHUNK_SAMPLES)) }

            val states = tracker.snapshot().states
            val stateString =
                expected.indices.joinToString("") { idx ->
                    when (states[idx]) {
                        WordState.CORRECT -> "C"
                        WordState.CLOSE -> "L"
                        WordState.WRONG -> "W"
                        WordState.MISSED -> "M"
                        WordState.HINTED -> "H"
                        else -> "."
                    }
                }
            println("[E2E-54-10] states=$stateString summary=${tracker.summary().headline}")
            android.util.Log.i("IqraaE2E", "states=$stateString summary=${tracker.summary().headline}")

            assertEquals(
                "54:10 fixture words must all reach CORRECT/CLOSE (states=$stateString)",
                expected.size,
                states.count { it.value == WordState.CORRECT || it.value == WordState.CLOSE },
            )
            assertEquals(
                "no MISSED on the clean studio fixture (states=$stateString)",
                0,
                tracker.summary().missed,
            )
            assertEquals(
                "no WRONG on the clean studio fixture (states=$stateString)",
                0,
                tracker.summary().wrong,
            )
            assertTrue(
                "the §9.6 headline must exist (got ${tracker.summary().headline})",
                tracker.summary().headline.contains("صحيحة"),
            )
        } finally {
            recognizer.close()
        }
    }
    // NOTE: the silero Vad instance is per-test and released by its own finalize(); keep the
    // test self-contained (V-042 evidence: EMULATOR numbers, not a phone).

    /** The committed fixture rides the androidTest APK assets (V-037c channel). */
    private fun repoAsset(rel: String): File? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val stream: InputStream =
            try {
                instrumentation.context.assets.open(rel)
            } catch (e: java.io.FileNotFoundException) {
                return null
            }
        val tmp = File.createTempFile("fixture", ".wav", instrumentation.targetContext.cacheDir)
        tmp.outputStream().use { out -> stream.use { it.copyTo(out) } }
        return tmp
    }

    private fun readMono16kPcm(wav: File): FloatArray {
        val bytes = wav.readBytes()
        // RIFF header walk: find the data chunk
        var idx = WAV_HEADER_MIN
        while (idx + CHUNK_HEADER_BYTES <= bytes.size) {
            val id = String(bytes, idx, CHUNK_ID_BYTES, Charsets.US_ASCII)
            val len = leInt(bytes, idx + CHUNK_ID_BYTES)
            if (id == "data") {
                val start = idx + CHUNK_HEADER_BYTES
                val count = minOf(len, bytes.size - start) / BYTES_PER_SAMPLE
                val out = FloatArray(count)
                for (i in 0 until count) {
                    val s = ((bytes[start + i * 2].toInt() and 0xFF) or (bytes[start + i * 2 + 1].toInt() shl 8))
                    out[i] = (s.toShort()).toInt() / MAX_PCM16
                }
                return out
            }
            idx += CHUNK_HEADER_BYTES + len + (len and 1)
        }
        throw IllegalStateException("no data chunk in ${wav.name}")
    }

    private fun leInt(
        b: ByteArray,
        off: Int,
    ): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl SHIFT_8) or
            ((b[off + 2].toInt() and 0xFF) shl SHIFT_16) or ((b[off + 3].toInt() and 0xFF) shl SHIFT_24)

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 1_600
        const val TRAILING_SILENCE_CHUNKS = 10
        const val WAV_HEADER_MIN = 12
        const val CHUNK_HEADER_BYTES = 8
        const val CHUNK_ID_BYTES = 4
        const val SHIFT_8 = 8
        const val SHIFT_16 = 16
        const val SHIFT_24 = 24
        const val BYTES_PER_SAMPLE = 2
        const val MAX_PCM16 = 32_768.0f
        const val SURAH_QAMAR = 54
        const val AYAH_10 = 10
        const val VAD_ASSET = "models/silero_vad.onnx"
        const val VAD_SPEECH_THRESHOLD = 0.5f
    }
}
