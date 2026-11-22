package com.iqraa.core.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig

/**
 * sherpa-onnx implementation of RecitationRecognizer (§8.2, Phase 2 host-side).
 *
 * Config per §8.2: zipformer CTC int8, greedy_search, no LM/lexicon/hints,
 * numThreads=2, provider=cpu; models loaded from assets via OfflineRecognizer(assetManager,
 * config) — no copy to filesDir.
 *
 * Timing contract (V-015): sherpa timestamps are SECONDS; token frame shift is 40 ms.
 * Words are assembled between ▁ separators (§6.2):
 *   word.startMs = segmentStartMs + firstTokenTs × 1000
 *   word.endMs   = segmentStartMs + lastTokenTs × 1000 + FRAME_SHIFT_MS
 * When timestamps are unusable (empty/absent), the NULL-TIMING fallback emits words without
 * ms and the tracker falls back to token-index interiority (kEdgeTokens=2, §9.2).
 */
class SherpaOnnxRecognizer(
    private val context: Context,
    override val info: ModelInfo,
) : RecitationRecognizer {

    private var recognizer: OfflineRecognizer? = null

    companion object {
        /** V-015: seconds, 10 ms hop × subsampling 4 = 40 ms token frame shift. */
        const val FRAME_SHIFT_MS: Long = 40

        /** Static pure entry for host tests of the assembly rule (no recognizer state). */
        fun assembleStatic(
            tokens: Array<String>,
            timestamps: FloatArray,
            segmentStartMs: Long,
        ): List<AsrWord> = SherpaOnnxAssembler.assemble(tokens, timestamps, segmentStartMs, FRAME_SHIFT_MS)
    }

    override suspend fun load() {
        try {
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    zipformerCtc = OfflineZipformerCtcModelConfig(
                        model = "models/${info.id}/model.int8.onnx",
                    ),
                    tokens = "models/${info.id}/tokens.txt",
                    numThreads = 2,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            )
            recognizer = OfflineRecognizer(context.assets, config)
        } catch (t: Throwable) {
            throw ModelLoadException("failed to load ${info.id}@${info.revision}", t)
        }
    }

    override suspend fun decode(pcm: FloatArray, segmentStartMs: Long): AsrResult {
        val r = recognizer ?: throw IllegalStateException("decode before load()")
        val t0 = System.currentTimeMillis()
        val stream = r.createStream()
        stream.acceptWaveform(pcm, 16000)
        r.decode(stream)
        val result = r.getResult(stream)
        stream.release()
        val computeMs = System.currentTimeMillis() - t0
        val audioMs = pcm.size * 1000L / 16000

        val words = assembleWords(result, segmentStartMs)
        val text = words.joinToString(" ") { it.text }
        return AsrResult(text = text, words = words, audioMs = audioMs, computeMs = computeMs)
    }

    /** §6.2 word assembly between ▁ separators; null-timing fallback when timestamps are unusable. */
    internal fun assembleWords(
        tokens: Array<String>,
        timestamps: FloatArray,
        segmentStartMs: Long,
    ): List<AsrWord> = SherpaOnnxAssembler.assemble(tokens, timestamps, segmentStartMs, FRAME_SHIFT_MS)

    private fun assembleWords(result: com.k2fsa.sherpa.onnx.OfflineRecognizerResult, segmentStartMs: Long): List<AsrWord> =
        assembleWords(result.tokens, result.timestamps, segmentStartMs)

    override fun close() {
        recognizer?.release()
        recognizer = null
    }
}

/** Pure word assembly (host-testable, no Android types). */
internal object SherpaOnnxAssembler {
    fun assemble(
        tokens: Array<String>,
        timestamps: FloatArray,
        segmentStartMs: Long,
        frameShiftMs: Long,
    ): List<AsrWord> {
        val hasTiming = timestamps.isNotEmpty() && timestamps.size == tokens.size
        val words = ArrayList<AsrWord>()
        val letters = StringBuilder()
        var firstTs: Float? = null
        var lastTs: Float? = null
        fun flush() {
            if (letters.isNotEmpty() && firstTs != null && lastTs != null && hasTiming) {
                words.add(
                    AsrWord(
                        text = letters.toString(),
                        startMs = segmentStartMs + (firstTs!! * 1000).toLong(),
                        endMs = segmentStartMs + (lastTs!! * 1000).toLong() + frameShiftMs,
                    ),
                )
            } else if (letters.isNotEmpty()) {
                // null-timing fallback (§9.2): token-index interiority downstream
                words.add(AsrWord(text = letters.toString(), startMs = null, endMs = null))
            }
            letters.setLength(0)
            firstTs = null
            lastTs = null
        }
        for (i in tokens.indices) {
            val tok = tokens[i]
            if (tok == "▁") {
                flush()
            } else {
                if (firstTs == null && hasTiming) firstTs = timestamps[i]
                if (hasTiming) lastTs = timestamps[i]
                letters.append(tok)
            }
        }
        flush()
        return words
    }
}
