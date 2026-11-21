package com.example.core.quran

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §7.4 golden tests for N(). Derived from tokens.txt inventory (V-005) and the
 *  model repo's own normalizer (V-020); verified against the full Tanzil text. */
class NormalizerTest {

    @Test
    fun `dagger alif U+0670 is dropped`() {
        // ٱلرَّحْمَٰنِ contains dagger alif 0670 on م
        assertEquals("الرحمن", Normalizer.word("ٱلرَّحْمَٰنِ"))
    }

    @Test
    fun `quranic small marks U+06D6 to U+06ED are dropped`() {
        // ۟ (06DF), ۢ (06E2), ۨ (06E8) appear in Tanzil
        assertEquals("اوليك", Normalizer.word("أُو۟لَٰٓئِكَ"))  // 06DF inside, ئ→ي
        assertEquals("لن", Normalizer.word("لَن۠"))
    }

    @Test
    fun `ayah-end U+06DD would be dropped if present`() {
        // Tanzil Uthmani (this flavor) does not use 06DD (verified V-017);
        // if one ever appears it must be dropped, never mapped to a letter.
        assertEquals("", Normalizer.word("ۣ۠ۤۥۦ۪ۭۧۨ۫۬"))
    }

    @Test
    fun `alef wasla folds to alef`() {
        assertEquals("الله", Normalizer.word("ٱللَّهِ"))
        assertEquals("ا", Normalizer.word("ٱ"))
    }

    @Test
    fun `hamza seats fold`() {
        assertEquals("اقرا", Normalizer.word("ٱقْرَأْ"))   // أ→ا
        assertEquals("اذون", Normalizer.word("أُذُونٍ"))
        assertEquals("او", Normalizer.word("أُو۟"))       // 06DF dropped, أ→ا
        assertEquals("ي", Normalizer.word("ئِ"))          // ئ→ي
        assertEquals("و", Normalizer.word("ؤْ"))         // ؤ→و
        assertEquals("من", Normalizer.word("مِن۠"))      // mark dropped
    }

    @Test
    fun `ta marbuta and alif maqsura fold`() {
        assertEquals("ه", Normalizer.word("ة"))           // ة→ه
        assertEquals("ي", Normalizer.word("ىٰ"))          // ى→ي, dagger dropped
        assertEquals("اهدنا", Normalizer.word("ٱهْدِنَا"))
    }

    @Test
    fun `tatweel is dropped`() {
        assertEquals("الله", Normalizer.word("ٱللّٰـهِ"))  // tatweel 0640 inside
        assertEquals("كتب", Normalizer.word("كَتَـٰبِ"))
    }

    @Test
    fun `basmala normalizes to model space`() {
        assertEquals(
            listOf("بسم", "الله", "الرحمن", "الرحيم"),
            Normalizer.text("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"),
        )
    }

    @Test
    fun `taawwudh normalizes to model space`() {
        assertEquals(
            "اعوذ بالله من الشيطن الرجيم",
            Normalizer.joined("أَعُوذُ بِٱللَّهِ مِنَ ٱلشَّيْطَٰنِ ٱلرَّجِيمِ"),
        )
    }

    @Test
    fun `small waw and ya are dropped like other marks`() {
        assertEquals("ه", Normalizer.word("هُۥ"))       // ۥ 06E5
        assertEquals("عليه", Normalizer.word("عَلَيْهِۦ")) // ۦ 06E6
    }

    @Test
    fun `non-arabic input is emptied`() {
        assertEquals("", Normalizer.word("hello 123"))
        assertEquals(listOf<String>(), Normalizer.text("   "))
    }

    @Test
    fun `only model letters are emitted on a long mixed sample`() {
        val mixed = "قُلْ هُوَ ٱللَّهُ أَحَدٌ ٱللَّهُ ٱلصَّمَدُ"
        val out = Normalizer.joined(mixed)
        for (ch in out.replace(" ", "")) {
            assertTrue(ch in Normalizer.MODEL_LETTERS, "unexpected char $ch")
        }
        assertEquals("قل هو الله احد الله الصمد", out)
    }
}
