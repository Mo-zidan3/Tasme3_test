package com.iqraa.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.iqraa.feature.reader.SurahListSource
import com.iqraa.tasmee.MainActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIX §2(c) (issue #11): an EMPTY source is an ERROR, never a success — the exact defect
 * class Phase 3 shipped (`surahNames = emptyList()` rendered as a bare empty screen).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReaderEmptyStateInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    /** Injection: the loader returns an EMPTY list — must surface as §11 error, not success. */
    @BindValue
    @JvmField
    val emptySource: SurahListSource = SurahListSource { emptyList() }

    @Test
    fun readerScreen_emptySource_rendersError_notSuccess() {
        activityRule.waitUntil(TIMEOUT_MS) {
            activityRule.onAllNodesWithText(ERROR_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        // the §11 error card is visible (an empty list must NEVER render as bare success)
        activityRule.onNodeWithText(ERROR_CARD).assertExists()
        // and no surah rows exist to be mistaken for content
        Assert.assertTrue(
            activityRule.onAllNodesWithText("1. الفاتحة").fetchSemanticsNodes().isEmpty(),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val ERROR_CARD = "The surah list failed to load. Retry?"
    }
}
