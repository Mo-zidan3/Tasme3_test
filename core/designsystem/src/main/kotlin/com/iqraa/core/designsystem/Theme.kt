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
    val correct = Color(0xFF1B8A3F)      // green + check glyph
    val close = Color(0xFFB26A00)        // amber + tilde glyph
    val wrong = Color(0xFFB3261E)        // red + DOTTED UNDERLINE (shape, not color alone)
    val missed = Color(0xFF5F6368)       // grey outline
    val hinted = Color(0xFF1A56DB)       // blue outline
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
        val CORRECT = FeedbackEncoding(
            FeedbackColors.correct, "✓", Decoration.CHECK, "صحيحة", "Correct",
        )
        val CLOSE = FeedbackEncoding(
            FeedbackColors.close, "≈", Decoration.TILDE, "قريبة من النص", "Close to the text",
        )
        val WRONG = FeedbackEncoding(
            FeedbackColors.wrong, "…", Decoration.DOTTED_UNDERLINE,
            "راجع هذه الكلمة", "Please review this word",
        )
        val MISSED = FeedbackEncoding(
            FeedbackColors.missed, "◌", Decoration.OUTLINE, "لم تُقرأ", "Not recited",
        )
        val HINTED = FeedbackEncoding(
            FeedbackColors.hinted, "؟", Decoration.BOX, "تلميح", "Hint",
        )
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

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF00549C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF001B33),
    secondary = Color(0xFF516171),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF191C1F),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF9ECBFF),
    onPrimary = Color(0xFF003259),
    primaryContainer = Color(0xFF004A80),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFB8C8D8),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFE1E2E6),
)

/** §10 Material 3 theme with dark mode + RTL support (LayoutDirection flows from locale). */
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
