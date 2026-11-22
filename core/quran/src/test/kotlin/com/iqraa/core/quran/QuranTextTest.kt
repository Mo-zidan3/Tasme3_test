package com.iqraa.core.quran

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §7.3 integrity tests over the committed asset: 114 / 6,236 / checksum / no mutation path. */
class QuranTextTest {

    @Test
    fun `asset parses with 114 surahs and 6236 ayahs`() {
        val ayahs = QuranText.load()
        assertEquals(6236, ayahs.size)
        assertEquals((1..114).toSet(), ayahs.map { it.surah }.toSet())
    }

    @Test
    fun `whole-asset sha256 matches the pinned value`() {
        val bytes = QuranText::class.java.getResourceAsStream(QuranText.ASSET_PATH)!!.readBytes()
        assertEquals(QuranText.ASSET_SHA256, QuranText.sha256Hex(bytes))
    }

    @Test
    fun `ayah payload sha256 matches the pinned value`() {
        val lines = QuranText::class.java.getResourceAsStream(QuranText.ASSET_PATH)!!
            .bufferedReader(Charsets.UTF_8).readLines()
        val payload = lines.filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString("\n") + "\n"
        assertEquals(QuranText.AYAH_PAYLOAD_SHA256, QuranText.sha256Hex(payload.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `verbatim tanzil notice is present in the header`() {
        val head = QuranText::class.java.getResourceAsStream(QuranText.ASSET_PATH)!!
            .bufferedReader(Charsets.UTF_8).readText().take(1200)
        assertTrue("# PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK" in head)
        assertTrue("#  Copyright (C) 2007-2026 Tanzil Project" in head)
        assertTrue("#  License: Creative Commons Attribution 3.0" in head)
    }

    @Test
    fun `parse rejects wrong counts`() {
        val bad = "1|1|a\n2|1|b\n2|2|c\n" // 2 surahs, 3 ayahs
        val ex = runCatching { QuranText.parse(bad.byteInputStream()) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException, "expected failure for short text")
    }

    @Test
    fun `parse rejects non-contiguous numbering`() {
        val bad = (1..6236).map { i -> "1|$i|x$i" }.joinToString("\n") + "\n" // 6236 but surah 1 only → wrong surah count
        val ex = runCatching { QuranText.parse(bad.byteInputStream()) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `first ayah of Al-Fatihah is the basmala and At-Tawbah has none`() {
        val ayahs = QuranText.load()
        val fatihah1 = ayahs.first { it.surah == 1 && it.ayah == 1 }
        val tawbah1 = ayahs.first { it.surah == 9 && it.ayah == 1 }
        // byte-exact basmala as stored in the asset (mark order shadda-then-fatha matters)
        val basmala = "\u0628\u0650\u0633\u0652\u0645\u0650 " +
            "\u0671\u0644\u0644\u0651\u064E\u0647\u0650 " +
            "\u0671\u0644\u0631\u0651\u064E\u062D\u0652\u0645\u064E\u0670\u0646\u0650 " +
            "\u0671\u0644\u0631\u0651\u064E\u062D\u0650\u064A\u0645\u0650"
        assertEquals(basmala, fatihah1.text)
        assertTrue(!tawbah1.text.startsWith("\u0628\u0650\u0633\u0652\u0645\u0650"), "At-Tawbah 9:1 must not start with basmala")
    }

    @Test
    fun `every ayah word normalizes to non-empty model-space word`() {
        val ayahs = QuranText.load()
        for (a in ayahs) {
            for (w in a.text.split(' ')) {
                assertTrue(Normalizer.word(w).isNotEmpty(), "empty N() at surah ${a.surah}:${a.ayah}")
            }
        }
    }

    @Test
    fun `total word and letter counts match the host measurement`() {
        val ayahs = QuranText.load()
        var words = 0
        var letters = 0
        for (a in ayahs) {
            for (w in a.text.split(' ').filter { it.isNotBlank() }) {
                words++
                letters += Normalizer.word(w).length
            }
        }
        assertEquals(77881, words)
        assertEquals(327514, letters)
    }
}
