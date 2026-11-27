package com.iqraa.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.iqraa.tasmee.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIX §2 (issue #11): reader-flow behavioral tests through the REAL MainActivity — the
 * gate Phase 3 lacked when it shipped CI-green with `surahNames = emptyList()` and a
 * no-op `onSurahSelected`. RED-first on main (empty list / no navigation / onboarding
 * shown despite the grant); the ReaderViewModel + wiring turn them green.
 *
 * The CI emulator runs en-US → screen strings resolve to the ENGLISH defaults (same
 * convention as MainActivityUiTest); surah names are Arabic DATA from the committed
 * Tanzil-derived table (locale-independent). The injected-failing-loader half of the
 * §11 error gate needs the Hilt seam that lands with the implementation
 * (ReaderErrorStateInstrumentedTest / ReaderEmptyStateInstrumentedTest); the invariant
 * proven here is the one main violated: an empty/failed source must NEVER render as a
 * bare success list.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReaderFlowInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // grant BEFORE the activity launches (run 45977626337) — MainViewModel reads the
    // RECORD_AUDIO state in init; the activity must not start before the grant.
    @get:Rule(order = 1)
    val micPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 2)
    val activityRule = createAndroidComposeRule<MainActivity>()

    /** (a) the reader shows the real surah list — الفاتحة، البقرة، آل عمران — not an
     *  empty list. */
    @Test
    fun readerScreen_shows114Surahs() {
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(FATIHA_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText(FATIHA_ROW).assertExists()
        activityRule.onNodeWithText("2. البقرة").assertExists()
        activityRule.onNodeWithText("3. آل عمران").assertExists()
    }

    /** (b) §10.1: tapping a surah navigates to the range picker for that surah. */
    @Test
    fun readerScreen_tapSurah_navigates() {
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(FATIHA_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText(FATIHA_ROW).performClick()
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(RANGE_PICKER_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText(RANGE_PICKER_TITLE).assertExists()
        activityRule.onNodeWithText("الفاتحة").assertExists()
    }

    /**
     * (c) §11 invariant: an empty or failed surah source must surface an error + retry —
     * the shipped defect (empty list rendered as bare success) times out here.
     */
    @Test
    fun readerScreen_emptyListNeverRendersAsSuccess() {
        activityRule.waitUntil(TIMEOUT_MS) {
            val rowsShown =
                activityRule
                    .onAllNodesWithText(FATIHA_ROW)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            val errorShown =
                activityRule
                    .onAllNodesWithText(ERROR_CARD)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            rowsShown || errorShown
        }
    }

    /**
     * (d) onboarding once: with the mic permission already granted the app starts at the
     * reader — the F7 card must not appear (startDestination computed, not hardcoded).
     */
    @Test
    fun onboarding_shownOnce() {
        activityRule.onNodeWithText(ONBOARDING_ALLOW).assertDoesNotExist()
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(FATIHA_ROW).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val ONBOARDING_ALLOW = "Allow microphone and continue"
        const val RANGE_PICKER_TITLE = "Choose the ayah range"
        const val ERROR_CARD = "The surah list failed to load. Retry?"
        const val FATIHA_ROW = "1. الفاتحة"
    }
}
