package com.example.core.quran

/**
 * N() — Tanzil Uthmani → model token space (§7.4).
 *
 * Rules are DERIVED FROM the pinned model repository's own normalizer
 * (`retrieve.py` at revision 0c2744b9d70aaf45ba574c30c5d318c575258e66):
 *  - fold letter variants: أ/إ/آ/ٱ/ٲ/ٳ→ا, ى/ئ/ے/ی→ي, ؤ→و, ة→ه, ک→ك
 *  - drop every character that is not one of the 29 letters
 *    ءابتثجحخدذرزسشصضطظعغفقكلمنهوي (i.e. all harakat, Quranic annotation marks,
 *    dagger alif U+0670, tatweel U+0640, small waw/ya U+06E5/U+06E6)
 *  - word boundaries = source whitespace, preserved; per-word normalization
 *
 * Verified against the committed Tanzil asset (V-020): 77,881 words → 327,514
 * letters; 0 words normalize to empty; exactly 29 distinct letters emitted.
 * Never used at runtime: spelling.json is eval-only (§7.2).
 */
object Normalizer {

    /** The 29 model letters (tokens.txt minus `<blk>` and `▁`). */
    val MODEL_LETTERS: Set<Char> = setOf(
        'ء', 'ا', 'ب', 'ت', 'ث', 'ج', 'ح', 'خ', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص',
        'ض', 'ط', 'ظ', 'ع', 'غ', 'ف', 'ق', 'ك', 'ل', 'م', 'ن', 'ه', 'و', 'ي',
    )

    /** Letter-variant folds (author's table, verbatim semantics). */
    val FOLD: Map<Char, Char> = mapOf(
        'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا', 'ٲ' to 'ا', 'ٳ' to 'ا',
        'ى' to 'ي', 'ئ' to 'ي', 'ے' to 'ي', 'ی' to 'ي',
        'ؤ' to 'و',
        'ة' to 'ه',
        'ک' to 'ك',
    )

    /** Normalize one word (any spacing chars must be split by the caller). */
    fun word(word: String): String {
        val out = StringBuilder(word.length)
        for (ch in word) {
            val folded = FOLD[ch] ?: ch
            if (folded in MODEL_LETTERS) out.append(folded)
        }
        return out.toString()
    }

    /** Normalize a phrase/ayah: whitespace-separated words, boundaries preserved. */
    fun text(text: String): List<String> =
        text.split(' ').filter { it.isNotBlank() }.map { word(it) }.filter { it.isNotEmpty() }

    /** Normalized phrase joined with single spaces (for retrieval/eval tooling). */
    fun joined(text: String): String = text(text).joinToString(" ")
}
