package com.iqraa.core.quran

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V-048 integrity tests for the surah-names table (issue #11): structure, spot names, and
 * the full cross-validation against the committed quran-uthmani.txt ayah asset (both are
 * Tanzil — per-surah counts must agree exactly; totals 6,236).
 */
class SurahNamesTest {
    private companion object {
        const val SURAHS = 114
        const val TOTAL_AYAHS = 6_236
    }

    @Test
    fun `114 surahs with contiguous numbering`() {
        val surahs = SurahNames.load()
        assertEquals(SURAHS, surahs.size)
        assertEquals((1..114).toList(), surahs.map { it.number })
    }

    @Test
    fun `spot names are the canonical plain forms`() {
        val surahs = SurahNames.load()
        assertEquals("الفاتحة", surahs[0].name)
        assertEquals("البقرة", surahs[1].name)
        assertEquals("آل عمران", surahs[2].name)
        assertEquals("الناس", surahs.last().name)
    }

    @Test
    fun `ayah counts sum to 6236`() {
        assertEquals(TOTAL_AYAHS, SurahNames.load().sumOf { it.ayahCount })
    }

    @Test
    fun `per-surah ayah counts match the committed ayah asset exactly`() {
        val counts = QuranText.load().groupingBy { it.surah }.eachCount()
        val surahs = SurahNames.load()
        assertEquals(SURAHS, counts.size)
        surahs.forEach { s ->
            assertEquals(
                s.ayahCount,
                counts[s.number],
                "surah ${s.number} (${s.name}): names table says ${s.ayahCount}, asset has ${counts[s.number]}",
            )
        }
    }

    @Test
    fun `malformed lines fail loudly, never silently`() {
        val bad = "# header\n1|الفاتحة\n".byteInputStream()
        val error =
            try {
                SurahNames.parse(bad)
                null
            } catch (e: IllegalArgumentException) {
                e
            }
        assertTrue(error != null, "a malformed line must throw, not pass")
    }
}
