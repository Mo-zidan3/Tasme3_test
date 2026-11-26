package com.iqraa.feature.recite

import com.iqraa.core.align.ExpectedWord
import com.iqraa.core.quran.ExpectedWordEntry
import com.iqraa.core.quran.ExpectedWords
import com.iqraa.core.quran.QuranText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V-048 display-source guard: the Uthmani words this screen renders must come from the
 * SAME [ExpectedWords.forRange] entries list the tracker's expected words were built from
 * ([toExpectedWords] + [deriveDisplayWords] — single source of truth). Pins the exact
 * Tanzil strings of 112:1–4, including the V-023 optional-prefix basmala, so "compiles but
 * shows the wrong word" fails HERE, not on Mo's phone. A desynchronized display source
 * must fail loudly — a silent wrong Quranic word (§7.1 verbatim text) is never acceptable.
 */
class DisplayWordsTest {
    /** Same data path as ReciteFlowViewModel.expectedWords(): asset → forRange → mapping. */
    private val ikhlasAyahs = QuranText.load().filter { it.surah == IKHLAS && it.ayah in FROM_AYAH..TO_AYAH }

    private val entries: List<ExpectedWordEntry> = ExpectedWords.forRange(ikhlasAyahs, IKHLAS, FROM_AYAH, TO_AYAH)

    private val expected: List<ExpectedWord> = toExpectedWords(entries)

    @Test
    fun `non-tasmee 112 - 1 to 4 shows the exact Uthmani words`() {
        val words =
            deriveDisplayWords(
                entries,
                expected,
                tasmeeMode = false,
                revealed = { false },
                placeholder = PLACEHOLDER,
            )
        assertEquals(
            listOf(
                "بِسْمِ",
                "ٱللَّهِ",
                "ٱلرَّحْمَٰنِ",
                "ٱلرَّحِيمِ",
                "قُلْ",
                "هُوَ",
                "ٱللَّهُ",
                "أَحَدٌ",
                "ٱللَّهُ",
                "ٱلصَّمَدُ",
                "لَمْ",
                "يَلِدْ",
                "وَلَمْ",
                "يُولَدْ",
                "وَلَمْ",
                "يَكُن",
                "لَّهُۥ",
                "كُفُوًا",
                "أَحَدٌۢ",
            ),
            words,
        )
    }

    @Test
    fun `optional-prefix basmala maps to the first four positions`() {
        assertEquals(BASMALA_WORDS, entries.take(BASMALA_WORDS).count { it.optionalPrefix })
        expected.take(BASMALA_WORDS).forEachIndexed { i, w ->
            assertEquals(i, w.index)
            assertTrue(w.optionalPrefix, "basmala position $i must stay optionalPrefix (§7.6)")
        }
        assertTrue(entries.drop(BASMALA_WORDS).none { it.optionalPrefix }, "only the basmala is optional")
    }

    @Test
    fun `tasmee reveals only the basmala words with their exact Uthmani forms`() {
        val words =
            deriveDisplayWords(
                entries,
                expected,
                tasmeeMode = true,
                revealed = { it.optionalPrefix },
                placeholder = PLACEHOLDER,
            )
        assertEquals("بِسْمِ", words[0])
        assertEquals("ٱللَّهِ", words[1])
        assertEquals("ٱلرَّحْمَٰنِ", words[2])
        assertEquals("ٱلرَّحِيمِ", words[3])
        assertEquals(PLACEHOLDER, words[BASMALA_WORDS], "first mandatory word stays hidden until revealed")
        assertEquals(entries.size, words.size)
    }

    @Test
    fun `desynchronized display source fails loudly - never a silent fallback`() {
        assertThrows(IllegalStateException::class.java) {
            deriveDisplayWords(
                entries.drop(1),
                expected,
                tasmeeMode = false,
                revealed = { false },
                placeholder = PLACEHOLDER,
            )
        }
        val shifted = expected.mapIndexed { i, w -> ExpectedWord(i + 1, w.surah, w.ayah, w.text, w.optionalPrefix) }
        assertThrows(IllegalStateException::class.java) {
            deriveDisplayWords(
                entries,
                shifted,
                tasmeeMode = false,
                revealed = { false },
                placeholder = PLACEHOLDER,
            )
        }
    }

    private companion object {
        const val IKHLAS = 112
        const val FROM_AYAH = 1
        const val TO_AYAH = 4
        const val BASMALA_WORDS = 4
        const val PLACEHOLDER = "•••"
    }
}
