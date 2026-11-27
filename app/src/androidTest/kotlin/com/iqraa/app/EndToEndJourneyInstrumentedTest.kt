package com.iqraa.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.iqraa.core.data.SessionRepository
import com.iqraa.tasmee.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * FIX §5 (issue #11): the full §10.1 journey through the REAL MainActivity on the CI
 * emulator — grant → reader (real list) → tap الفاتحة → range picker → start recitation →
 * words render → end → §9.6 summary → history row (§15 write) → reader again.
 *
 * HONEST LIMIT (emulator): the virtual mic yields silence, so word STATES cannot change
 * live here — that half is proven by RecitationFlowInstrumentedTest (real fixture audio
 * through the same assembly) and stays Mo's H1–H4 hardware steps (docs/PHONE_TEST.md).
 * On silence the summary is the honest «Correct: 0 of N».
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EndToEndJourneyInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // grant BEFORE the activity launches (run 45977626337): MainViewModel reads the
    // RECORD_AUDIO state in init — with the old order the activity started first and saw
    // granted=false → onboarding card → the assertDoesNotExist below failed.
    @get:Rule(order = 1)
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 2)
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var sessions: SessionRepository

    @Test
    fun fullJourney_grantToRangeToReciteToSummaryToHistory() {
        hiltRule.inject()
        runBlocking { sessions.deleteAll() }

        // 1. permission granted → NO onboarding card; the reader shows the real list
        activityRule.onNodeWithText(ONBOARDING_ALLOW).assertDoesNotExist()
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(FATIHA_ROW).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. tap الفاتحة → the §10.1 range picker
        activityRule.onNodeWithText(FATIHA_ROW).performClick()
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(RANGE_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText("الفاتحة").assertExists()

        // 3. start the recitation (defaults: full surah, follow-along)
        activityRule.onNodeWithText(START).performClick()

        // 4. the recite screen renders the expected words after model load (Preparing → live)
        activityRule.waitUntil(LONG_TIMEOUT_MS) {
            activityRule.onAllNodesWithText(FIRST_WORD, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 5. end the session → the §9.6 summary (honest silence counts)
        activityRule.onNodeWithText(END).performClick()
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(SUMMARY_ZERO, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 6. Done → the reader; the History tab shows the persisted session row (§15)
        activityRule.onNodeWithText(DONE).performClick()
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(FATIHA_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText(HISTORY_TAB).performClick()
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText("1:1–7").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val LONG_TIMEOUT_MS = 120_000L
        const val ONBOARDING_ALLOW = "Allow microphone and continue"
        const val FATIHA_ROW = "1. الفاتحة"
        const val RANGE_TITLE = "Choose the ayah range"
        const val START = "Start recitation"
        const val FIRST_WORD = "بِسْمِ"
        const val END = "End session"
        const val SUMMARY_ZERO = "Correct: 0 of"
        const val DONE = "Done"
        const val HISTORY_TAB = "History"
    }
}
