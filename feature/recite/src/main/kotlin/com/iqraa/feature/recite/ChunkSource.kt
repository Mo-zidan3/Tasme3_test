package com.iqraa.feature.recite

import com.iqraa.core.audio.AudioCapture

/**
 * The capture seam (V-048): the REAL mic ([MicChunkSource]) or fixture PCM in tests —
 * [ReciteViewModel]'s §10.4 loop is IDENTICAL for both. This is what lets the instrumented
 * fixture test prove word-state changes through the app's own ViewModel path with real
 * audio, which the emulator mic (silence) cannot.
 */
interface ChunkSource {
    val state: AudioCapture.State

    fun start(): Boolean

    fun readChunk(): FloatArray

    fun stop()
}

/** Real mic: the §8.3 AudioRecord capture. */
class MicChunkSource(
    private val mic: AudioCapture = AudioCapture(),
) : ChunkSource {
    override val state: AudioCapture.State
        get() = mic.state

    override fun start(): Boolean = mic.start()

    override fun readChunk(): FloatArray = mic.readChunk()

    override fun stop() = mic.stop()
}
