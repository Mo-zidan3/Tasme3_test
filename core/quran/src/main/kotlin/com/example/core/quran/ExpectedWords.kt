package com.example.core.quran

/**
 * Derived expected-words table (§7.4, §7.6) — model-space (N()) words per ayah, plus the
 * basmala placement facts from V-023:
 *  - surah 1: basmala IS ayah 1 (mandatory words);
 *  - surah 9: no basmala;
 *  - all other 112 surahs: basmala PREFIXED into ayah 1 as OPTIONAL_PREFIX words
 *    (recited → absorbed; omitted → never MISSED, never counted in Y).
 *
 * Derived data NEVER overwrites the original asset; this table is computed on demand and,
 * when generated to a file, carries the Tanzil notice header (see generate_expected_words).
 */
data class ExpectedWordEntry(
    val surah: Int,
    val ayah: Int,
    /** model-space (N()) word text */
    val text: String,
    /** true when this word belongs to an optional prefixed basmala (§7.6) */
    val optionalPrefix: Boolean = false,
    /** original (Uthmani) word, for display */
    val display: String,
)

object ExpectedWords {

    /** N() of the basmala (V-020 golden): بسم الله الرحمن الرحيم */
    val BASMALA_N: List<String> = listOf("بسم", "الله", "الرحمن", "الرحيم")

    /** First-word letter forms accepted as basmala starts (V-023: plain + shadda variants;
     *  shadda is written BEFORE kasra in the Tanzil bytes: U+0628 U+0651 U+0650 …). */
    val BASMALA_FIRST_WORD_FORMS: Set<String> = setOf(
        "بِسْمِ",
        "\u0628\u0651\u0650\u0633\u0652\u0645\u0650",
    )

    /** Build the derived expected-words table from the parsed ayahs. */
    fun build(ayahs: List<QuranText.Ayah>): List<ExpectedWordEntry> {
        val out = ArrayList<ExpectedWordEntry>(78000)
        for (a in ayahs) {
            val words = a.text.split(' ').filter { it.isNotBlank() }
            var index = 0
            val isSurahStart = a.ayah == 1
            // Optional prefixed basmala: 4-word prefix matching N(basmala) at surah start
            // (surahs other than 1 and 9 — V-023: 112 surahs).
            if (isSurahStart && a.surah != 1 && a.surah != 9 && words.size >= 4) {
                val prefixN = words.take(4).map { Normalizer.word(it) }
                val startsWithBasmalaForm = BASMALA_FIRST_WORD_FORMS.any { a.text.startsWith(it) }
                if (prefixN == BASMALA_N && startsWithBasmalaForm) {
                    for (i in 0 until 4) {
                        out.add(ExpectedWordEntry(a.surah, a.ayah, prefixN[i], true, words[i]))
                    }
                    index = 4
                }
            }
            for (i in index until words.size) {
                val n = Normalizer.word(words[i])
                if (n.isNotEmpty()) out.add(ExpectedWordEntry(a.surah, a.ayah, n, false, words[i]))
            }
        }
        return out
    }

    /** Convenience: expected words for a surah/ayah range (model space), with the optional
     *  basmala prefix of the FIRST surah in range kept as optional entries. */
    fun forRange(ayahs: List<QuranText.Ayah>, surah: Int, fromAyah: Int, toAyah: Int): List<ExpectedWordEntry> =
        build(ayahs.filter { it.surah == surah && it.ayah in fromAyah..toAyah })
}
