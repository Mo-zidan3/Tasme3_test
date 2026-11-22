package com.iqraa.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * §10 Quran text rendering: Amiri Quran (OFL, D-016 asset) per-word styled by §10.2 state
 * encoding — color + glyph/underline decoration + label, NEVER color-only.
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun QuranText(
    words: List<String>,
    states: List<FeedbackEncoding?>,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign = TextAlign.Right,
) {
    require(words.size == states.size) { "words/states size mismatch" }
    Text(
        text = annotatedQuran(words, states),
        modifier = modifier,
        fontFamily = fontFamily ?: FontFamily.Default,
        textAlign = textAlign,
        fontSize = 24.sp,
        lineHeight = 44.sp,
    )
}

/** Build the annotated per-word string: state spans carry BOTH color and decoration. */
private fun annotatedQuran(
    words: List<String>,
    states: List<FeedbackEncoding?>,
): AnnotatedString {
    return buildAnnotatedString {
    words.forEachIndexed { i, w ->
        val enc = states.getOrNull(i)
        if (enc == null) {
            append(w)
        } else {
            withStyle(
                SpanStyle(
                    color = enc.color,
                    textDecoration = enc.decoration.textDecoration(),
                ),
            ) {
                append(enc.decorate(w))
            }
        }
        if (i != words.lastIndex) append(' ')
    }
    }
}

/** The glyph/decoration half of the encoding (§10.2: never color-only). */
private fun FeedbackEncoding.decorate(word: String): String = when (decoration) {
    FeedbackEncoding.Decoration.CHECK -> "$word✓"
    FeedbackEncoding.Decoration.TILDE -> "$word≈"
    FeedbackEncoding.Decoration.DOTTED_UNDERLINE -> word // underline comes via textDecoration
    FeedbackEncoding.Decoration.OUTLINE -> word
    FeedbackEncoding.Decoration.BOX -> word
}

private fun FeedbackEncoding.Decoration.textDecoration(): androidx.compose.ui.text.style.TextDecoration? =
    when (this) {
        FeedbackEncoding.Decoration.DOTTED_UNDERLINE -> androidx.compose.ui.text.style.TextDecoration.Underline
        else -> null
    }
