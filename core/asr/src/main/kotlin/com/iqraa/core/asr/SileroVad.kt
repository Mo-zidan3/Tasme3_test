package com.iqraa.core.asr

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * §8.4 / T7: silero VAD adapter for the pipeline. sherpa's `Vad.compute` requires EXACTLY
 * 512-sample frames (feeding a 1600-sample capture chunk fails with "Got invalid dimensions
 * for input: x … Expected: 512" — measured on the CI emulator, run 45334500218, both API
 * levels). This adapter frames any chunk size into 512-sample windows (carrying the
 * remainder to the next chunk — proper streaming) and majority-aggregates the per-frame
 * speech probability into one decision per chunk.
 */
class SileroVad(
    private val vad: Vad,
    private val threshold: Float = DEFAULT_THRESHOLD,
) : AutoCloseable {
    constructor(assets: AssetManager, modelPath: String) : this(
        Vad(assets, VadModelConfig().apply { sileroVadModelConfig.model = modelPath }),
    )

    private val buffer = ArrayDeque<Float>(FRAME_SAMPLES * CHUNKS_BUFFERED)

    /** One VAD decision for an arbitrary-sized PCM chunk (true = speech majority). */
    fun isSpeech(chunk: FloatArray): Boolean {
        for (s in chunk) buffer.addLast(s)
        var frames = 0
        var speechFrames = 0
        while (buffer.size >= FRAME_SAMPLES) {
            val frame = FloatArray(FRAME_SAMPLES) { buffer.removeFirst() }
            if (vad.compute(frame) > threshold) speechFrames++
            frames++
        }
        return frames > 0 && speechFrames * 2 > frames
    }

    override fun close() {
        vad.release()
    }

    private companion object {
        /** silero window size (32 ms @ 16 kHz) — sherpa's compute contract. */
        const val FRAME_SAMPLES = 512

        /** keep ~2 capture chunks (100 ms each) worth of carry-over samples */
        const val CHUNKS_BUFFERED = 2

        /** silero speech-probability cut (§8.4; same value as the eval harness). */
        const val DEFAULT_THRESHOLD = 0.5f
    }
}
