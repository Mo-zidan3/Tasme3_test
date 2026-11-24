package com.iqraa.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * §8.3 / T7: REAL AudioRecord capture — 16 kHz mono PCM16 → float [-1, 1].
 * VOICE_RECOGNITION source (no AEC/NS surprise processing), foreground-only by design (§10.4:
 * the UI keeps the screen on; no service, no notification). Mic-taken (another app holds the
 * mic / permission revoked mid-session) surfaces as [State.MIC_UNAVAILABLE] — the caller maps
 * that to the §11 pause banner, never a crash.
 */
class AudioCapture(
    private val sampleRate: Int = SAMPLE_RATE,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
) {
    enum class State { IDLE, RECORDING, STOPPED, MIC_UNAVAILABLE }

    private var record: AudioRecord? = null
    private var stateValue: State = State.IDLE
    private val chunk = ShortArray(CHUNK_SAMPLES)

    val state: State get() = stateValue

    /** Opens the mic. Returns false (state = MIC_UNAVAILABLE) when the mic cannot open. */
    fun start(): Boolean {
        if (stateValue == State.RECORDING) return true
        close()
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            stateValue = State.MIC_UNAVAILABLE
            return false
        }
        return try {
            val r =
                AudioRecord(
                    audioSource,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, CHUNK_SAMPLES * BYTES_PER_SAMPLE * BUFFER_CHUNKS),
                )
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                r.release()
                stateValue = State.MIC_UNAVAILABLE
                return false
            }
            r.startRecording()
            record = r
            stateValue = State.RECORDING
            true
        } catch (e: SecurityException) {
            // permission revoked mid-session (§11)
            Log.w(TAG, "mic permission revoked", e)
            stateValue = State.MIC_UNAVAILABLE
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "mic unavailable", e)
            stateValue = State.MIC_UNAVAILABLE
            false
        }
    }

    /**
     * Blocking read of one chunk ([CHUNK_SAMPLES] @ 16 kHz = 100 ms) converted to float.
     * Empty array + state != RECORDING means the mic was taken — stop feeding the pipeline
     * and surface the pause banner.
     */
    fun readChunk(): FloatArray {
        val r = record ?: return FloatArray(0)
        if (stateValue != State.RECORDING) return FloatArray(0)
        val n =
            try {
                r.read(chunk, 0, chunk.size)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "mic read failed", e)
                stateValue = State.MIC_UNAVAILABLE
                return FloatArray(0)
            }
        if (n <= 0) {
            // ERROR_INVALID_OPERATION / ERROR_BAD_VALUE: mic taken by another app
            stateValue = State.MIC_UNAVAILABLE
            return FloatArray(0)
        }
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = chunk[i] / MAX_PCM16
        return out
    }

    /** Releases the mic (§10.4: on pause/stop/screen-off — release EARLY, never hold it). */
    fun stop() {
        close()
        if (stateValue != State.MIC_UNAVAILABLE) stateValue = State.STOPPED
    }

    private fun close() {
        record?.let { r ->
            try {
                if (r.state == AudioRecord.STATE_INITIALIZED) r.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "stop while not recording", e)
            }
            r.release()
        }
        record = null
    }

    private companion object {
        const val TAG = "IqraaAudioCapture"
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 1_600
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_CHUNKS = 8
        const val MAX_PCM16 = 32_768.0f
    }
}
