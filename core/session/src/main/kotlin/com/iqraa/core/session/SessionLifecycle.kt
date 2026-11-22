package com.iqraa.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §10.4 session lifecycle machine — normative UX states + transitions.
 * Pure Kotlin (host-testable); the ViewModel/Activity maps Android events onto [event].
 */
sealed interface LifecycleState {
    /** Capture active; screen kept on (FLAG_KEEP_SCREEN_ON at the call site). */
    data object Active : LifecycleState

    /** §10.4: screen-off / app-switch / focus loss / call — paused, mic released. */
    data class Paused(
        val reason: PauseReason,
        val bannerAr: String,
    ) : LifecycleState

    /** §10.4: resume restarts capture after a 1 s countdown (value = remaining seconds). */
    data class ResumeCountdown(
        val remainingSeconds: Int,
    ) : LifecycleState

    /** §10.4 process death is NOT a state — it ends the session (checkpoints already saved). */
    data object Ended : LifecycleState
}

enum class PauseReason(val bannerAr: String) {
    SCREEN_OFF("الجلسة متوقفة"),
    APP_SWITCH("الجلسة متوقفة"),
    AUDIO_FOCUS_LOSS("الجلسة متوقفة"),
    MIC_TAKEN("تعذّر الوصول إلى المايك"),
    PHONE_CALL("الجلسة متوقفة"),
    PERMISSION_REVOKED("permission-revoked"), // → F7 rationale screen, distinct flow
}

/**
 * The §10.4 machine. Events come from the Activity lifecycle / audio-focus callbacks;
 * transitions are total (no illegal-state crash paths — wrong events are no-ops).
 */
class SessionLifecycle(
    initial: LifecycleState = LifecycleState.Ended,
    private val countdownSeconds: Int = 1,
) { // §10.4 machine
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<LifecycleState> = _state.asStateFlow()

    fun event(e: SessionEvent) {
        when (val s = _state.value) {
            is LifecycleState.Active -> when (e) {
                is SessionEvent.PAUSE -> _state.value = pausedFor(e) ?: s
                SessionEvent.END -> _state.value = LifecycleState.Ended
                else -> Unit
            }
            is LifecycleState.Paused -> when (e) {
                SessionEvent.RESUME -> _state.value =
                    LifecycleState.ResumeCountdown(countdownSeconds)
                SessionEvent.END -> _state.value = LifecycleState.Ended
                else -> Unit
            }
            is LifecycleState.ResumeCountdown -> when (e) {
                is SessionEvent.TICK -> {
                    val next = e.seconds ?: (s.remainingSeconds - 1)
                    _state.value =
                        if (next <= 0) LifecycleState.Active
                        else LifecycleState.ResumeCountdown(next)
                }
                is SessionEvent.PAUSE -> _state.value = pausedFor(e) ?: s
                SessionEvent.END -> _state.value = LifecycleState.Ended
                else -> Unit
            }
            LifecycleState.Ended -> Unit // terminal; a NEW session object starts fresh
        }
    }

    private fun pausedFor(e: SessionEvent): LifecycleState.Paused? = when (e) {
        is SessionEvent.PAUSE -> LifecycleState.Paused(e.reason, e.reason.bannerAr)
        else -> null
    }
}

/** Events fed by Android surfaces (Activity callbacks, audio-focus listener). */
sealed interface SessionEvent {
    data class PAUSE(val reason: PauseReason) : SessionEvent
    data object RESUME : SessionEvent
    data class TICK(val seconds: Int?) : SessionEvent
    data object END : SessionEvent
}
