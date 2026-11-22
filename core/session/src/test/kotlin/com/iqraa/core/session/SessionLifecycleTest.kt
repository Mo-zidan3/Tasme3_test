package com.iqraa.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §10.4 lifecycle machine: pause paths, 1 s countdown, terminal Ended. */
class SessionLifecycleTest {
    @Test
    fun active_pauses_on_screen_off_with_banner() {
        val m = SessionLifecycle(LifecycleState.Active)
        m.event(SessionEvent.PAUSE(PauseReason.SCREEN_OFF))
        val s = m.state.value
        assertTrue(s is LifecycleState.Paused && s.bannerAr == "الجلسة متوقفة")
    }

    @Test
    fun paused_resumes_through_one_second_countdown() {
        val m = SessionLifecycle(LifecycleState.Active)
        m.event(SessionEvent.PAUSE(PauseReason.APP_SWITCH))
        m.event(SessionEvent.RESUME)
        assertEquals(LifecycleState.ResumeCountdown(1), m.state.value)
        m.event(SessionEvent.TICK(null)) // 1 → 0 → Active
        assertEquals(LifecycleState.Active, m.state.value)
    }

    @Test
    fun countdown_can_be_repaused_and_ended() {
        val m = SessionLifecycle(LifecycleState.ResumeCountdown(1))
        m.event(SessionEvent.PAUSE(PauseReason.PHONE_CALL))
        assertTrue(m.state.value is LifecycleState.Paused)
        m.event(SessionEvent.END)
        assertEquals(LifecycleState.Ended, m.state.value)
    }

    @Test
    fun ended_is_terminal() {
        val m = SessionLifecycle(LifecycleState.Ended)
        m.event(SessionEvent.RESUME)
        m.event(SessionEvent.TICK(seconds = 3))
        assertEquals(LifecycleState.Ended, m.state.value)
    }

    @Test
    fun mic_taken_pause_carries_distinct_banner() {
        val m = SessionLifecycle(LifecycleState.Active)
        m.event(SessionEvent.PAUSE(PauseReason.MIC_TAKEN))
        val s = m.state.value
        assertTrue(s is LifecycleState.Paused && s.bannerAr == "تعذّر الوصول إلى المايك")
    }
}
