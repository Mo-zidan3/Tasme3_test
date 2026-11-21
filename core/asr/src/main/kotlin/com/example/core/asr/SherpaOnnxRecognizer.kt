package com.example.core.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig

/**
 * sherpa-onnx implementation of RecitationRecognizer (§8.2).
 *
 * Config per §8.2: zipformer CTC int8, greedy_search, no LM/lexicon/hints,
 * numThreads=2 start, provider=cpu; models loaded from assets via
 * OfflineRecognizer(assetManager, config) — no copy to filesDir.
 *
 * NOTE: Phase 2 implements decode()/word assembly (tokens+timestamps per
 * §6.2 contract). This Phase 0 scaffold only proves the vendored API
 * compiles and the model config types match the pinned release (v2.0.2).
 */
class SherpaOnnxRecognizer(
    private val context: Context,
    override val info: ModelInfo,
) : RecitationRecognizer {

    private var recognizer: OfflineRecognizer? = null

    suspend fun load2() {
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
    }

    override suspend fun load() {
        // Phase 2: guarded load with ModelLoadException mapping (§11)
        load2()
    }

    override suspend fun decode(pcm: FloatArray, segmentStartMs: Long): AsrResult {
        // Phase 2: createStream/acceptWaveform/decode/getResult + word assembly
        throw NotImplementedError("decode lands in Phase 2 (§16)")
    }

    override fun close() {
        recognizer?.release()
    }
}
