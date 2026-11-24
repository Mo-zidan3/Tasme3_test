package com.iqraa.feature.recite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.iqraa.core.align.TrackerUpdate
import com.iqraa.core.audio.AudioCapture
import com.iqraa.core.session.PauseReason
import com.iqraa.core.session.PipelineEngine
import com.iqraa.core.session.ReciteSession
import com.iqraa.core.session.SessionEvent
import com.iqraa.core.session.SessionLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * T7: the recite screen's ViewModel — the REAL pipeline (AudioCapture → PipelineEngine →
 * ReciteSession → tracker) behind the §10.4 [SessionLifecycle] machine. Rotation survives
 * (ViewModel); screen-off / app-switch / focus-loss / mic-taken pause capture and RELEASE
 * the mic immediately (§10.4); resume restarts capture after the 1 s countdown.
 */
class ReciteViewModel(
    private val session: ReciteSession,
    private val engine: PipelineEngine,
    private val capture: AudioCapture,
    private val lifecycle: SessionLifecycle = SessionLifecycle(),
) : ViewModel() {
    data class UiState(
        val lifecycle: com.iqraa.core.session.LifecycleState,
        val update: TrackerUpdate?,
        val micError: Boolean,
    )

    private val _state =
        MutableStateFlow(
            UiState(lifecycle.state.value, session.updates.value, micError = false),
        )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var captureJob: Job? = null

    init {
        viewModelScope.launch {
            lifecycle.state.collect { ls ->
                _state.value = _state.value.copy(lifecycle = ls)
            }
        }
        viewModelScope.launch {
            session.updates.collect { u ->
                _state.value = _state.value.copy(update = u)
            }
        }
    }

    /** Starts (or resumes) capture per §10.4 — Active after the resume countdown. */
    fun start() {
        if (lifecycle.state.value is com.iqraa.core.session.LifecycleState.Active) return
        if (lifecycle.state.value is com.iqraa.core.session.LifecycleState.Paused) {
            lifecycle.event(SessionEvent.RESUME)
            viewModelScope.launch { countdownThenStart() }
        } else {
            beginSession()
        }
    }

    private suspend fun countdownThenStart() {
        // 1 s countdown (§10.4): tick down then re-open the mic
        repeat(COUNTDOWN_SECONDS) { remaining ->
            lifecycle.event(SessionEvent.TICK(seconds = COUNTDOWN_SECONDS - remaining))
            delay(RESUME_COUNTDOWN_MS)
        }
        lifecycle.event(SessionEvent.TICK(seconds = 0))
        beginSession()
    }

    private fun beginSession() {
        if (!capture.start()) {
            _state.value = _state.value.copy(micError = true)
            lifecycle.event(SessionEvent.PAUSE(PauseReason.MIC_TAKEN))
            return
        }
        _state.value = _state.value.copy(micError = false)
        captureJob?.cancel()
        captureJob =
            viewModelScope.launch {
                while (
                    lifecycle.state.value is com.iqraa.core.session.LifecycleState.Active &&
                    capture.state == AudioCapture.State.RECORDING
                ) {
                    val chunk = capture.readChunk()
                    if (chunk.isEmpty()) {
                        if (capture.state == AudioCapture.State.MIC_UNAVAILABLE) {
                            // §11: mic taken mid-session — pause, release, banner; never crash
                            pause(PauseReason.MIC_TAKEN)
                            _state.value = _state.value.copy(micError = true)
                        }
                        break
                    }
                    engine.onChunk(chunk)
                }
            }
    }

    /** §10.4 pause: capture stops, mic released IMMEDIATELY, state preserved. */
    fun pause(reason: PauseReason) {
        lifecycle.event(SessionEvent.PAUSE(reason))
        captureJob?.cancel()
        captureJob = null
        capture.stop()
    }

    /** §10.6 resync: re-anchor the tracker to the picked ayah, then resume capture. */
    fun resync(ayahIndex: Int) {
        session.reanchor(ayahIndex)
        start()
    }

    fun stop() {
        lifecycle.event(SessionEvent.END)
        captureJob?.cancel()
        captureJob = null
        capture.stop()
    }

    override fun onCleared() {
        capture.stop()
        super.onCleared()
    }

    /** Factory for manual construction (full Hilt graph lands with B2 DI wiring). */
    class Factory(
        private val session: ReciteSession,
        private val engine: PipelineEngine,
        private val capture: AudioCapture,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReciteViewModel(session, engine, capture) as T
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 1
        const val RESUME_COUNTDOWN_MS = 1_000L
    }
}
