package com.iqraa.tasmee

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * V-048 unit tests (issue #11): the computed startDestination — onboarding shows ONLY when
 * the flag is unset AND the permission is not granted. This is the logic the hardcoded
 * `"onboarding"` startDestination got wrong (shown every launch, even with the grant).
 *
 * Run 45977626337: a settings failure must never leave _start at Resolving forever — the
 * catch falls back to the permission-only decision so AppRoot always renders (§11).
 */
@ExtendWith(MainDispatcherRuleExtension::class)
class MainViewModelTest {
    private class FakeGate(
        var completed: Boolean = false,
    ) : OnboardingGate {
        var completeCalls = 0

        override suspend fun wasCompleted(): Boolean = completed

        override suspend fun complete() {
            completeCalls++
            completed = true
        }
    }

    /** A gate whose settings read ALWAYS throws (run 45977626337 failure shape). */
    private class ThrowingGate : OnboardingGate {
        override suspend fun wasCompleted(): Boolean = throw IllegalStateException("multiple DataStores active (test)")

        override suspend fun complete() = Unit
    }

    @Test
    fun `flag unset and permission unset - onboarding is the start`() {
        val gate = FakeGate(completed = false)
        val vm = MainViewModel(gate, PermissionChecker { false })
        val resolved = vm.start.value
        assertTrue(resolved is StartState.Resolved && resolved.startOnboarding)
    }

    @Test
    fun `permission granted - onboarding is skipped`() {
        val gate = FakeGate(completed = false)
        val vm = MainViewModel(gate, PermissionChecker { true })
        val resolved = vm.start.value
        assertTrue(resolved is StartState.Resolved && !resolved.startOnboarding)
    }

    @Test
    fun `flag set - onboarding is skipped even without permission`() {
        val gate = FakeGate(completed = true)
        val vm = MainViewModel(gate, PermissionChecker { false })
        val resolved = vm.start.value
        assertTrue(resolved is StartState.Resolved && !resolved.startOnboarding)
    }

    /** Run 45977626337: a throwing settings read must still resolve — granted → reader. */
    @Test
    fun `settings failure with grant - resolves to the reader`() {
        val vm = MainViewModel(ThrowingGate(), PermissionChecker { true })
        val resolved = vm.start.value
        assertTrue(resolved is StartState.Resolved && !resolved.startOnboarding)
    }

    /** Run 45977626337: a throwing settings read must still resolve — denied → onboarding. */
    @Test
    fun `settings failure without grant - resolves to onboarding`() {
        val vm = MainViewModel(ThrowingGate(), PermissionChecker { false })
        val resolved = vm.start.value
        assertTrue(resolved is StartState.Resolved && resolved.startOnboarding)
    }

    @Test
    fun `markOnboardingDone completes the gate - shown once ever`() {
        val gate = FakeGate(completed = false)
        val vm = MainViewModel(gate, PermissionChecker { false })
        vm.markOnboardingDone()
        assertEquals(1, gate.completeCalls)
        // a new launch (fresh ViewModel over the same persisted flag) starts at the reader
        val relaunched = MainViewModel(gate, PermissionChecker { false })
        val resolved = relaunched.start.value
        assertTrue(resolved is StartState.Resolved && !resolved.startOnboarding)
    }
}
