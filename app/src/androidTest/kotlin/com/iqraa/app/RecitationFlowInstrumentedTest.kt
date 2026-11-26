package com.iqraa.app

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iqraa.core.audio.AudioCapture
import com.iqraa.core.data.SessionRepository
import com.iqraa.core.data.settings.SettingsRepository
import com.iqraa.feature.recite.ChunkSource
import com.iqraa.feature.recite.ChunkSourceProvider
import com.iqraa.feature.recite.MicChunkSource
import com.iqraa.feature.recite.ReciteFlowViewModel
import com.iqraa.feature.recite.RecitePipelineFactory
import com.iqraa.feature.recite.ReciteUiState
import com.iqraa.feature.recite.RecognizerProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * FIX §5 (issue #11): "words change state while reciting" proven at the app-assembly level
 * with REAL audio — the committed 54:10 fixture (V-038, PROVENANCE-recorded permission)
 * drives the REAL Hilt graph (RecognizerProvider → RecitePipelineFactory →
 * ReciteFlowViewModel) through a fixture ChunkSource. The emulator mic yields only
 * silence, so live-mic state changes are Mo's H1–H4 hardware steps (PHONE_TEST.md) — this
 * test is the CI-honest equivalent: same ViewModel path, same pipeline, real decoder.
 * Also proves the §9.6 summary and the §15 history write (the row the summary screen
 * loads back).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecitationFlowInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var recognizerProvider: RecognizerProvider

    @Inject
    lateinit var pipelineFactory: RecitePipelineFactory

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var sessions: SessionRepository

    /** Fixture PCM replaces the mic for this class's sessions. */
    @BindValue
    @JvmField
    val chunkSources: TestChunkSources = TestChunkSources()

    /**
     * Named class (V-048: run 45777086097) — `fixture` must live on the DECLARED type,
     * not an anonymous object: the binding's static type is the ChunkSourceProvider
     * interface, so `chunkSources.fixture = …` did not resolve on it. Same shape as the
     * interface's mic default: hand out the fixture once set, the mic otherwise.
     */
    class TestChunkSources : ChunkSourceProvider {
        var fixture: FixtureChunkSource? = null

        override fun newSource(): ChunkSource = fixture ?: MicChunkSource()
    }

    @Test
    fun recitation_fixtureThroughRealAssembly_wordsChangeState_andHistoryIsWritten() {
        hiltRule.inject()
        runBlocking { sessions.deleteAll() }

        val fixtureFile = repoAsset("eval/fixtures/fixture.wav")
        assertNotNull("committed 54:10 fixture must ride the androidTest assets", fixtureFile)
        val fixture = FixtureChunkSource(readMono16kPcm(fixtureFile!!), trailingSilenceMs = 1_500)
        chunkSources.fixture = fixture

        val vm =
            ReciteFlowViewModel(
                recognizerProvider = recognizerProvider,
                pipelineFactory = pipelineFactory,
                settings = settings,
                sessions = sessions,
                chunkSources = chunkSources,
                savedStateHandle =
                    SavedStateHandle(
                        mapOf(
                            "surah" to SURAH,
                            "from" to AYAH,
                            "to" to AYAH,
                            "mode" to "follow",
                        ),
                    ),
            )

        // 1. preparation: model load + expected words → the live Session state
        runBlocking {
            withTimeout(PREPARE_TIMEOUT_MS) {
                while (vm.state.value !is ReciteUiState.Session) {
                    assertTrue(
                        "preparation failed: ${vm.state.value}",
                        vm.state.value !is ReciteUiState.ModelError,
                    )
                    delay(POLL_MS)
                }
            }
        }
        val session = vm.state.value as ReciteUiState.Session
        assertTrue(
            "54:10 must resolve to 5 expected words (got ${session.words.size})",
            session.words.size == WORDS_54_10,
        )

        // 2. run the fixture through the REAL pipeline — words change state (CORRECT/CLOSE)
        vm.start()
        runBlocking {
            withTimeout(RUN_TIMEOUT_MS) {
                while (!fixture.exhausted) delay(POLL_MS)
                delay(SETTLE_MS) // trailing silence flushes the pause-final decode
            }
            vm.endSession()
            withTimeout(END_TIMEOUT_MS) {
                while (vm.state.value !is ReciteUiState.Ended) delay(POLL_MS)
            }
        }

        // 3. the §9.6 summary: the clean studio fixture is all CORRECT/CLOSE, none missed
        val ended = vm.state.value as ReciteUiState.Ended
        assertEquals(
            "all 5 words must be CORRECT or CLOSE (summary=${ended.summary.headline})",
            WORDS_54_10,
            ended.summary.correct + ended.summary.close,
        )
        assertEquals(0, ended.summary.missed)
        assertEquals(0, ended.summary.wrong)
        assertTrue("the §9.6 headline must exist", ended.summary.headline.contains("صحيحة"))

        // 4. the §15 history write: the row exists with the §9.6 counts
        val row = runBlocking { sessions.sessionById(ended.sessionId) }
        assertNotNull("the session row must be persisted (history)", row)
        assertEquals(WORDS_54_10, row!!.countsCorrect + row.countsClose)
        assertEquals(SURAH, row.surah)
    }

    /**
     * Test-seam PCM source (the app's mic equivalent): hands out the fixture chunks in
     * order, then trailing silence (so the segmenter emits the pause-final), then empty —
     * which ends the capture loop exactly like a mic that yields no more data.
     */
    private class FixtureChunkSource(
        pcm: FloatArray,
        trailingSilenceMs: Long,
    ) : ChunkSource {
        private val chunks =
            buildList {
                var i = 0
                while (i < pcm.size) {
                    val end = minOf(i + CHUNK_SAMPLES, pcm.size)
                    add(pcm.copyOfRange(i, end))
                    i = end
                }
                repeat(((trailingSilenceMs * SAMPLE_RATE_MS) / CHUNK_SAMPLES).toInt()) {
                    add(FloatArray(CHUNK_SAMPLES))
                }
            }
        private var index = 0
        private var recording = false

        val exhausted: Boolean get() = index >= chunks.size

        override var state: AudioCapture.State = AudioCapture.State.IDLE
            private set

        override fun start(): Boolean {
            recording = true
            state = AudioCapture.State.RECORDING
            return true
        }

        override fun readChunk(): FloatArray {
            if (!recording || index >= chunks.size) return FloatArray(0)
            return chunks[index++]
        }

        override fun stop() {
            recording = false
            state = AudioCapture.State.STOPPED
        }

        private companion object {
            const val CHUNK_SAMPLES = 1_600
            const val SAMPLE_RATE_MS = 16L // samples per ms at 16 kHz
        }
    }

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
        var idx = WAV_HEADER_MIN
        while (idx + CHUNK_HEADER_BYTES <= bytes.size) {
            val id = String(bytes, idx, CHUNK_ID_BYTES, Charsets.US_ASCII)
            val len = leInt(bytes, idx + CHUNK_ID_BYTES)
            if (id == "data") {
                val start = idx + CHUNK_HEADER_BYTES
                val count = minOf(len, bytes.size - start) / BYTES_PER_SAMPLE
                val out = FloatArray(count)
                for (i in 0 until count) {
                    val s = (bytes[start + i * 2].toInt() and 0xFF) or (bytes[start + i * 2 + 1].toInt() shl 8)
                    out[i] = s.toShort().toInt() / MAX_PCM16
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
        const val SURAH = 54
        const val AYAH = 10
        const val WORDS_54_10 = 5
        const val PREPARE_TIMEOUT_MS = 120_000L
        const val RUN_TIMEOUT_MS = 120_000L
        const val END_TIMEOUT_MS = 30_000L
        const val SETTLE_MS = 3_000L
        const val POLL_MS = 200L
        const val WAV_HEADER_MIN = 12
        const val CHUNK_HEADER_BYTES = 8
        const val CHUNK_ID_BYTES = 4
        const val SHIFT_8 = 8
        const val SHIFT_16 = 16
        const val SHIFT_24 = 24
        const val BYTES_PER_SAMPLE = 2
        const val MAX_PCM16 = 32_768.0f
    }
}
