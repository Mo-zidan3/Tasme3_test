package com.iqraa.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iqraa.core.designsystem.FeedbackEncoding
import com.iqraa.feature.recite.ReciteScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T9 (B3): Compose UI tests for the recite flows — run IN THE CI EMULATOR.
 *  - follow-along vs tasmee' mode: the hint button exists ONLY in tasmee' (§10.3);
 *  - §10.2 encoding renders (color + glyph, never color-only);
 *  - the §10.4 pause banner shows «الجلسة متوقفة»;
 *  - 2.0× font scale still renders (accessibility floor).
 */
@RunWith(AndroidJUnit4::class)
class ReciteUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = listOf("قل", "هو", "الله", "احد")
    private val states =
        listOf<FeedbackEncoding?>(
            FeedbackEncoding.CORRECT,
            null,
            FeedbackEncoding.CLOSE,
            FeedbackEncoding.WRONG,
        )

    // ONE setContent per test rule (a second call throws "has already set content" —
    // run 45340839779), so tasmee' vs follow-along are separate tests.
    @Test
    fun tasmeeMode_showsHintButton() {
        compose.setContent {
            ReciteScreen(
                words = words,
                states = states,
                isTasmeeMode = true,
                pausedBanner = null,
                onResume = {},
                onHint = {},
            )
        }
        // tasmee': hint button present. The CI emulator runs en-US, so resources resolve
        // to the ENGLISH defaults; the AR variants are exercised by the manual script.
        compose.onNodeWithText("Hint").assertExists()
        compose.onNodeWithText("Hint").performClick()
    }

    @Test
    fun followAlongMode_doesNotShowHintButton() {
        compose.setContent {
            ReciteScreen(
                words = words,
                states = states,
                isTasmeeMode = false, // follow-along: NO hint button (§10.3)
                pausedBanner = null,
                onResume = {},
                onHint = {},
            )
        }
        compose.onAllNodesWithText("Hint").fetchSemanticsNodes().let { nodes ->
            assertEquals("follow-along mode must NOT show the hint button", 0, nodes.size)
        }
    }

    @Test
    fun fontScale2_rendersWords() {
        // §10.4 accessibility floor: 2.0× font scale must still render the Quran text.
        // Component rule (no activity content to conflict with); the system-level setting
        // is covered by the manual script's `settings put system font_scale 2.0` step.
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit
                        .Density(density = 1f, fontScale = 2f),
            ) {
                ReciteScreen(
                    words = words,
                    states = states,
                    isTasmeeMode = true,
                    pausedBanner = null,
                    onResume = {},
                    onHint = {},
                )
            }
        }
        compose.onNodeWithText("قل✓", substring = true).assertExists()
    }

    @Test
    fun pauseBanner_showsArabicText() {
        compose.setContent {
            ReciteScreen(
                words = words,
                states = states,
                isTasmeeMode = true,
                pausedBanner = "الجلسة متوقفة", // §10.4 pause banner (ar primary)
                onResume = {},
                onHint = {},
            )
        }
        compose.onNodeWithText("الجلسة متوقفة").assertExists()
    }

    @Test
    fun words_renderWithEncodedStates() {
        compose.setContent {
            ReciteScreen(
                words = words,
                states = states,
                isTasmeeMode = false,
                pausedBanner = null,
                onResume = {},
                onHint = {},
            )
        }
        // §10.2: CORRECT carries the check glyph, CLOSE the tilde — encoding is NEVER
        // color-only, so the glyph is assertable text (WRONG is a dotted underline on the
        // word itself — no glyph, verified visually in the manual script)
        compose.onNodeWithText("قل✓", substring = true).assertExists()
        compose.onNodeWithText("الله≈", substring = true).assertExists()
    }
}
