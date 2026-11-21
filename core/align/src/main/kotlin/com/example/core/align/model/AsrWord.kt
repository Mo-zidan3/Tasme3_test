package com.example.core.align.model

/** §6.2 AsrWord — normalized model-space text; session-global ms timings; confidence always null in MVP. */
data class AsrWord(
    val text: String,
    val startMs: Long?,
    val endMs: Long?,
    val confidence: Float? = null,
)

/** §6.2 Observation — one decoded speech window. */
data class Observation(
    val words: List<AsrWord>,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val isFinal: Boolean,
    val modelRev: String,
)
