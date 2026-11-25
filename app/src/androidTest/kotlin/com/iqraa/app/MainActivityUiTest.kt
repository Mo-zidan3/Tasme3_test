package com.iqraa.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.iqraa.tasmee.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T9 (B3) / V-048 update: activity-level UI tests in the CI emulator. V-048 changed the
 * behavior under test — onboarding now shows ONCE (skipped when the permission is granted
 * or the flag is set, issue #11), so the grant flow asserts the READER CONTENT (the real
 * surah list — a stronger assertion than the old hardcoded «اقرأ» title), and rotation
 * asserts the START screen keeps its content intact.
 * The DENIED-permanently card and true process-death restore need interactive/hardware
 * steps: covered by the MANUAL SCRIPT in docs/TESTING.md (Mo executes those on a real
 * device — honestly marked, not faked).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val micPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @Test
    fun onboarding_grantFlow_reachesReader() {
        // F7 renders first on a fresh install without the grant; with the permission
        // pre-granted (the rule) the fixed app skips straight to the reader — this prologue
        // serves both. The reader must show REAL CONTENT (the Phase-3 defect was an empty
        // list behind the hardcoded «اقرأ» title — issue #11).
        val nodes = activityRule.onAllNodesWithText("Allow microphone and continue").fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
            activityRule.onNodeWithText("Allow microphone and continue").performClick()
        }
        activityRule.waitUntil(30_000) {
            activityRule.onAllNodesWithText("1. الفاتحة").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText("1. الفاتحة").assertExists()
        activityRule.onNodeWithText("2. البقرة").assertExists()
    }

    @Test
    fun rotation_startScreenSurvivesWithContentIntact() {
        // V-048: with the grant, the START screen is the reader (onboarding-once) — its
        // content must survive rotation. (The original test asserted the onboarding card
        // survives; the fixed behavior intentionally skips it when granted — issue #11.)
        activityRule.waitUntil(30_000) {
            activityRule.onAllNodesWithText("1. الفاتحة").fetchSemanticsNodes().isNotEmpty()
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.setOrientationLeft()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("1. الفاتحة").assertExists()
        device.setOrientationNatural()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("1. الفاتحة").assertExists()
    }

    // fontScale2_rendersWords moved to ReciteUiTest (a component rule): a SECOND setContent
    // on an activity rule throws "has already set content" (run 45340839779, API 27+35).
}
