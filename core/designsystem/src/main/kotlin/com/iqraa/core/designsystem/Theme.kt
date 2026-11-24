package com.iqraa.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * §10/§10.2 feedback colors — the ONLY place word-state colors are defined.
 * Colors are paired with shapes+glyphs at the call site (FeedbackEncoding): NEVER color-only.
 */
object FeedbackColors {
    // LIGHT-surface palette (T9): shades darkened to pass WCAG AA (>= 4.5:1) on the light
    // surface — COMPUTED in ContrastTest (was 0xFF1B8A3F green @4.42 and 0xFFB26A00 amber
    // @4.24). Dark surfaces use the DARK variants via [FeedbackEncoding.adapted] — a static
    // palette cannot pass AA on both surfaces.
    val correct = Color(0xFF146C34) // green + check glyph — AA 6.4 on light
    val close = Color(0xFF8F5500) // amber + tilde glyph — AA 6.0 on light
    val wrong = Color(0xFFB3261E) // red + DOTTED UNDERLINE (shape, not color alone) — AA 6.6
    val missed = Color(0xFF5F6368) // grey outline — AA 6.0 on light
    val hinted = Color(0xFF1A56DB) // blue outline — AA 6.1 on light
}

/** DARK-surface variants (T9 WCAG AA, ratios computed in ContrastTest). */
object DarkFeedbackColors {
    val correct = Color(0xFF7BD88F) // AA 10.6 on dark
    val close = Color(0xFFE8A33D) // AA 8.6 on dark
    val wrong = Color(0xFFFF8A80) // AA 8.1 on dark
    val missed = Color(0xFFBDC1C6) // AA 9.9 on dark
    val hinted = Color(0xFF9ECBFF) // AA 11.0 on dark
}

/**
 * §10.2 feedback encoding: every state pairs color + SHAPE/GLYPH + localized label.
 * The [glyph] and [decoration] fields are load-bearing: color-only feedback is forbidden.
 */
data class FeedbackEncoding(
    val color: Color,
    val glyph: String,
    val decoration: Decoration,
    val labelAr: String,
    val labelEn: String,
) {
    enum class Decoration { CHECK, TILDE, DOTTED_UNDERLINE, OUTLINE, BOX }

    companion object {
        val CORRECT =
            FeedbackEncoding(
                FeedbackColors.correct,
                "✓",
                Decoration.CHECK,
                "صحيحة",
                "Correct",
            )
        val CLOSE =
            FeedbackEncoding(
                FeedbackColors.close,
                "≈",
                Decoration.TILDE,
                "قريبة من النص",
                "Close to the text",
            )
        val WRONG =
            FeedbackEncoding(
                FeedbackColors.wrong,
                "…",
                Decoration.DOTTED_UNDERLINE,
                "راجع هذه الكلمة",
                "Please review this word",
            )
        val MISSED =
            FeedbackEncoding(
                FeedbackColors.missed,
                "◌",
                Decoration.OUTLINE,
                "لم تُقرأ",
                "Not recited",
            )
        val HINTED =
            FeedbackEncoding(
                FeedbackColors.hinted,
                "؟",
                Decoration.BOX,
                "تلميح",
                "Hint",
            )
    }

    /**
     * T9: dark-mode adaptation — the §10.2 palette must pass WCAG AA on the ACTIVE surface;
     * call at composition time (before building text spans) and render with the result.
     */
    @androidx.compose.runtime.Composable
    fun adapted(darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme()): FeedbackEncoding =
        if (!darkTheme) {
            this
        } else {
            val dark =
                when (decoration) {
                    Decoration.CHECK -> DarkFeedbackColors.correct
                    Decoration.TILDE -> DarkFeedbackColors.close
                    Decoration.DOTTED_UNDERLINE -> DarkFeedbackColors.wrong
                    Decoration.OUTLINE -> DarkFeedbackColors.missed
                    Decoration.BOX -> DarkFeedbackColors.hinted
                }
            copy(color = dark)
        }
}

/** Font scale bounds (§10.4 accessibility: 0.8×–2.0×). */
object FontScale {
    const val MIN = 0.8f
    const val MAX = 2.0f
    const val DEFAULT = 1.0f
}

/** Local override consumed by QuranText so user fontScale applies inside the reader. */
val LocalQuranFontScale = staticCompositionLocalOf { FontScale.DEFAULT }

private val LightScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF00549C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E9FF),
        onPrimaryContainer = Color(0xFF001B33),
        secondary = Color(0xFF516171),
        surface = Color(0xFFFCFCFF),
        onSurface = Color(0xFF191C1F),
    )

private val DarkScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFF9ECBFF),
        onPrimary = Color(0xFF003259),
        primaryContainer = Color(0xFF004A80),
        onPrimaryContainer = Color(0xFFD6E9FF),
        secondary = Color(0xFFB8C8D8),
        surface = Color(0xFF101417),
        onSurface = Color(0xFFE1E2E6),
    )

/** §10 Material 3 theme with dark mode + RTL support (LayoutDirection flows from locale). */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun IqraaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = FontScale.DEFAULT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
