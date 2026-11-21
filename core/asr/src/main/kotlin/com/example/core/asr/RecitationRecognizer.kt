package com.example.core.asr

/**
 * §6.2 normative contract. The speech model is a swappable file behind this
 * interface (product principle 4).
 */
interface RecitationRecognizer : AutoCloseable {
    val info: ModelInfo
    suspend fun load()
    /** One speech segment: 16 kHz mono float PCM in [-1,1]. */
    suspend fun decode(pcm: FloatArray, segmentStartMs: Long): AsrResult
}

data class ModelInfo(
    val id: String,
    val revision: String,
    val sha256: String,
    val space: String = "uthmani-raw",
)

data class AsrResult(
    val text: String,
    val words: List<AsrWord>,
    val audioMs: Long,
    val computeMs: Long,
)

data class AsrWord(
    val text: String,
    val startMs: Long?,
    val endMs: Long?,
    val confidence: Float? = null,  // always null with sherpa-onnx (Q1-CLOSED)
)

class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
