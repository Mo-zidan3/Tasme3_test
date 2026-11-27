package com.iqraa.tasmee

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * V-048 (issue #11): the onboarding-once gate — `startDestination` is COMPUTED from the
 * DataStore flag OR the RECORD_AUDIO grant (§10.1), never hardcoded "onboarding" again.
 * While resolving, AppRoot renders nothing (no flash of the wrong first screen).
 */
sealed interface StartState {
    data object Resolving : StartState

    data class Resolved(
        val startOnboarding: Boolean,
    ) : StartState
}

/** The onboarding-flag seam — unit-testable with fakes (app/src/test). */
interface OnboardingGate {
    suspend fun wasCompleted(): Boolean

    suspend fun complete()
}

/** The permission seam — unit-testable with fakes. */
fun interface PermissionChecker {
    fun isRecordAudioGranted(): Boolean
}

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val onboardingGate: OnboardingGate,
        private val permissionChecker: PermissionChecker,
    ) : ViewModel() {
        private val _start = MutableStateFlow<StartState>(StartState.Resolving)
        val start: StateFlow<StartState> = _start.asStateFlow()

        init {
            viewModelScope.launch {
                // skip onboarding when the flag is set OR the permission is already granted;
                // a settings failure must NEVER leave _start at Resolving forever (§11) —
                // fall back to the permission-only decision so AppRoot always renders.
                _start.value = resolveStart(onboardingGate, permissionChecker)
            }
        }

        @Suppress("TooGenericExceptionCaught") // run 45977626337: ANY settings failure must fall back, never hang
        private suspend fun resolveStart(
            onboardingGate: OnboardingGate,
            permissionChecker: PermissionChecker,
        ): StartState.Resolved =
            try {
                val completed = onboardingGate.wasCompleted()
                val granted = permissionChecker.isRecordAudioGranted()
                StartState.Resolved(startOnboarding = !completed && !granted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "settings read failed — permission-only start decision", e)
                StartState.Resolved(startOnboarding = !permissionChecker.isRecordAudioGranted())
            }

        /** Called on BOTH onboarding exits (granted and continued-without) — shown once, ever. */
        fun markOnboardingDone() {
            viewModelScope.launch { onboardingGate.complete() }
        }

        private companion object {
            const val TAG = "IqraaMainViewModel"
        }
    }
