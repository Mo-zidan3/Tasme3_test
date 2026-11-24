package com.iqraa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iqraa.core.asr.ModelInfo
import com.iqraa.core.asr.SherpaOnnxRecognizer
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream

/**
 * T10 (V-045): the R8-MINIFIED build decodes on device. Runs ONLY this class on the
 * releaseSmoke variant (CI passes -Pandroid.testInstrumentationRunnerArguments.class=…):
 * a minimal decode of the committed licensed 54:10 fixture through the REAL recognizer.
 * If R8 strips a JNI entry point or breaks a shape the sherpa JNI bridge needs, load() or
 * decode() fails here — the keep rules get FIXED, minify is never disabled.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeInstrumentedTest {
    @Test
    fun releaseSmoke_minifiedBuild_decodes_fixture(): Unit =
        kotlinx.coroutines.runBlocking {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val fixture = repoAsset("eval/fixtures/fixture.wav")
            assumeTrue(
                "SKIPPED (loudly): fixture absent — CI copies the committed 54:10 clip into androidTest assets",
                fixture != null,
            )
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
                val pcm = readMono16kPcm(fixture!!)
                val result = recognizer.decode(pcm, segmentStartMs = 0)
                assertTrue(
                    "the MINIFIED build must decode ≥1 word on the clean fixture (got '${result.text.take(40)}')",
                    result.words.isNotEmpty(),
                )
                android.util.Log.i("IqraaReleaseSmoke", "decoded ${result.words.size} words (R8 build)")
            } finally {
                recognizer.close()
            }
        }

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
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    private companion object {
        const val WAV_HEADER_MIN = 12
        const val CHUNK_HEADER_BYTES = 8
        const val CHUNK_ID_BYTES = 4
        const val BYTES_PER_SAMPLE = 2
        const val MAX_PCM16 = 32_768.0f
    }
}
