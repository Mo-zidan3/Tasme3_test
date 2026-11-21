package com.example.core.quran

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** V-023 basmala modeling: OPTIONAL_PREFIX tagging + arithmetic reconciliation. */
class ExpectedWordsTest {

    private val table: List<ExpectedWordEntry> = ExpectedWords.build(QuranText.load())

    @Test
    fun `exactly 112 surahs carry an optional prefixed basmala`() {
        val withPrefix = table.filter { it.optionalPrefix }.map { it.surah }.toSortedSet()
        assertEquals(112, withPrefix.size)
        // expected set: 2..8 and 10..114
        val expected = (2..8).toSet() + (10..114).toSet()
        assertEquals(expected, withPrefix)
        assertFalse(withPrefix.contains(1), "Fatihah basmala is ayah 1, not a prefix")
        assertFalse(withPrefix.contains(9), "At-Tawbah has no basmala")
    }

    @Test
    fun `surah 95 and 97 use the shadda variant and are still recognized`() {
        for (s in intArrayOf(95, 97)) {
            val first = table.first { it.surah == s && it.ayah == 1 }
            assertTrue(first.optionalPrefix, "surah $s basmala must be OPTIONAL_PREFIX")
            assertEquals("بسم", first.text)
        }
    }

    @Test
    fun `Fatihah ayah 1 is mandatory basmala words`() {
        val f1 = table.filter { it.surah == 1 && it.ayah == 1 }
        assertEquals(4, f1.size)
        assertTrue(f1.none { it.optionalPrefix }, "Fatihah words are mandatory")
        assertEquals(listOf("بسم", "الله", "الرحمن", "الرحيم"), f1.map { it.text })
    }

    @Test
    fun `At-Tawbah ayah 1 has no basmala words`() {
        val t1 = table.filter { it.surah == 9 && it.ayah == 1 }
        assertTrue(t1.none { it.optionalPrefix })
        assertTrue(t1.none { it.text == "بسم" }, "Tawbah 9:1 first word is براءة")
    }

    @Test
    fun `arithmetic reconciliation - 77881 total words explained`() {
        val total = table.size
        val optional = table.count { it.optionalPrefix }
        // 77,881 words in the text (V-020); the 448 optional basmala words (112×4) are part
        // of them; mandatory words = 77,881.
        assertEquals(77881, total)
        assertEquals(448, optional)
        assertEquals(77433, total - optional)
    }

    @Test
    fun `omitted basmala path - range words exclude optional prefix when counting Y`() {
        // Baqarah 1:1 with basmala stripped for the mandatory count:
        val baqarah1 = table.filter { it.surah == 2 && it.ayah == 1 }
        assertEquals(4 + 1, baqarah1.size, "basmala(4) + الٓمٓ (1) in Baqarah 1:1")
        assertEquals(4, baqarah1.count { it.optionalPrefix })
        assertEquals(1, baqarah1.count { !it.optionalPrefix })
    }
}
