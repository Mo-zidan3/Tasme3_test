package com.iqraa.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.iqraa.feature.reader.SurahListSource
import com.iqraa.tasmee.MainActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIX §2(c) (issue #11): the §11 error state INJECTED through the real MainActivity — the
 * surah source always fails; the reader must show the error card + retry (never an empty
 * success list, never a crash). This is the half of the error gate that could not exist on
 * pre-fix main (no injection seam) — it lands together with the seam.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReaderErrorStateInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    /** §11 injection: the loader ALWAYS throws. */
    @BindValue
    @JvmField
    val failingSource: SurahListSource =
        SurahListSource {
            throw IllegalStateException("injected loader failure (test)")
        }

    @Test
    fun readerScreen_errorState_isVisible_withRetry() {
        // permission granted → straight at the reader; the injected source fails
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(ERROR_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText(ERROR_CARD).assertExists()

        // §11: the retry action re-attempts (fails again — honest error, no crash)
        activityRule.onNodeWithText(RETRY).performClick()
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(ERROR_CARD).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val ERROR_CARD = "The surah list failed to load. Retry?"
        const val RETRY = "Retry"
    }
}
