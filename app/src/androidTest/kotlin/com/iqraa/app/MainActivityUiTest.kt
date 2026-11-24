package com.iqraa.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.iqraa.core.designsystem.FeedbackEncoding
import com.iqraa.feature.recite.ReciteScreen
import org.junit.Rule
import org.junit.Test

/**
 * T9 (B3): activity-level UI tests in the CI emulator — the F7 grant flow, rotation
 * survival, and the 2.0× font-scale accessibility floor. The DENIED-permanently card and
 * true process-death restore need interactive/hardware steps: covered by the MANUAL SCRIPT
 * in docs/TESTING.md (Mo executes those on a real device — honestly marked, not faked).
 */
class MainActivityUiTest {
    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val micPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @Test
    fun onboarding_grantFlow_reachesReader() {
        // F7 renders first (§10.1): rationale + riwaya + limitations (en defaults on CI)
        activityRule.onNodeWithText("Allow microphone and continue").assertExists()
        activityRule
            .onNodeWithText("This app supports the Hafs recitation (from Asim) only.")
            .assertExists()
        activityRule.onNodeWithText("Allow microphone and continue").performClick()
        // permission is pre-granted via the rule → onGranted → reader (app name header)
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Iqraa").assertExists()
    }

    @Test
    fun rotation_onboardingSurvivesWithContentIntact() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.setOrientationLeft()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Allow microphone and continue").assertExists()
        device.setOrientationNatural()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Allow microphone and continue").assertExists()
    }

    @Test
    fun fontScale2_rendersWords() {
        // §10.4 accessibility floor: 2.0× font scale must still render the Quran text.
        // Applied at the CompositionLocal level (the system setting is covered by the
        // manual script's `settings put system font_scale 2.0` step).
        activityRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit
                        .Density(density = 1f, fontScale = 2f),
            ) {
                ReciteScreen(
                    words = listOf("قل", "هو", "الله", "احد"),
                    states = listOf<FeedbackEncoding?>(FeedbackEncoding.CORRECT, null, null, null),
                    isTasmeeMode = true,
                    pausedBanner = null,
                    onResume = {},
                    onHint = {},
                )
            }
        }
        activityRule.onNodeWithText("قل✓", substring = true).assertExists()
    }
}
