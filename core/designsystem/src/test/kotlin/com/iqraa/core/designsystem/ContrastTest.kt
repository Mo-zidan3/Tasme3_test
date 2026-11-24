package com.iqraa.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * T9 (B3) accessibility: WCAG 2.1 contrast for the §10.2 feedback palette — COMPUTED from
 * the actual Color values (relative luminance per WCAG definition), not claimed. AA for
 * normal text = 4.5:1 (the palette must pass on BOTH light (white) and dark (#101417)
 * surfaces: the recite text renders on both).
 */
class ContrastTest {
    private fun linearize(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        val r = linearize((argb shr 16) and 0xFF)
        val g = linearize((argb shr 8) and 0xFF)
        val b = linearize(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun ratio(
        fg: Color,
        bg: Color,
    ): Double {
        val l1 = maxOf(luminance(fg), luminance(bg))
        val l2 = minOf(luminance(fg), luminance(bg))
        return (l1 + 0.05) / (l2 + 0.05)
    }

    @Test
    fun `light palette meets WCAG AA on the light surface`() {
        val light = Color(0xFFFCFCFF)
        val palette =
            mapOf(
                "CORRECT" to FeedbackColors.correct,
                "CLOSE" to FeedbackColors.close,
                "WRONG" to FeedbackColors.wrong,
                "MISSED" to FeedbackColors.missed,
                "HINTED" to FeedbackColors.hinted,
            )
        for ((name, color) in palette) {
            val r = ratio(color, light)
            assertTrue(r >= AA_NORMAL, "$name fails AA on light surface: $r < $AA_NORMAL")
        }
    }

    @Test
    fun `dark palette meets WCAG AA on the dark surface`() {
        val dark = Color(0xFF101417)
        val palette =
            mapOf(
                "CORRECT" to DarkFeedbackColors.correct,
                "CLOSE" to DarkFeedbackColors.close,
                "WRONG" to DarkFeedbackColors.wrong,
                "MISSED" to DarkFeedbackColors.missed,
                "HINTED" to DarkFeedbackColors.hinted,
            )
        for ((name, color) in palette) {
            val r = ratio(color, dark)
            assertTrue(r >= AA_NORMAL, "$name fails AA on dark surface: $r < $AA_NORMAL")
        }
    }

    private companion object {
        const val AA_NORMAL = 4.5
    }
}
