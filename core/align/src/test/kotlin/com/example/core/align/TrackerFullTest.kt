package com.example.core.align

import com.example.core.align.model.AsrWord
import com.example.core.align.model.Observation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Table-driven + property-based tests for the §9 tracker. */

private fun word(text: String, start: Long? = null, end: Long? = null) =
    AsrWord(text = text, startMs = start, endMs = end)

private fun obs(words: List<AsrWord>, start: Long, end: Long, isFinal: Boolean = false, rev: String = "r1") =
    Observation(words = words, windowStartMs = start, windowEndMs = end, isFinal = isFinal, modelRev = rev)

private fun expected(vararg texts: String) = texts.mapIndexed { i, t ->
    ExpectedWord(index = i, surah = 1, ayah = 1, text = t)
}

class AlignerTest {

    @Test
    fun `sim basic properties`() {
        assertEquals(1.0, sim("قل", "قل"))
        assertEquals(0.0, sim("", "قل"))
        assertTrue(sim("قل", "قلب") == 1.0 - 1.0 / 3.0)
        assertEquals(1.0, sim("", ""))
    }

    @Test
    fun `levenshtein sanity`() {
        assertEquals(0, levenshtein("الله", "الله"))
        assertEquals(1, levenshtein("قل", "قلب"))
        assertEquals(2, levenshtein("قل", "هو"))
    }

    @Test
    fun `exact match sequence aligns and scores high`() {
        val exp = expected("بسم", "الله", "الرحمن", "الرحيم")
        val words = listOf(word("بسم", 0, 300), word("الله", 300, 600), word("الرحمن", 600, 900), word("الرحيم", 900, 1200))
        val a = Aligner.align(words, exp, 0, 3, TrackerConfig())
        val matches = a.moves.filterIsInstance<Move.Match>()
        assertEquals(4, matches.size)
        assertEquals(listOf(0, 1, 2, 3), matches.map { it.expectedIndex })
        assertTrue(matches.all { it.sim == 1.0 })
    }

    @Test
    fun `leading and trailing out-of-text speech is free (semi-global)`() {
        val exp = expected("بسم", "الله")
        // taawwudh-like leading + trailing junk
        val words = listOf(word("اعوذ"), word("بسم"), word("الله"), word("صدق"))
        val a = Aligner.align(words, exp, 0, 1, TrackerConfig())
        val matches = a.moves.filterIsInstance<Move.Match>().map { it.expectedIndex }
        assertEquals(listOf(0, 1), matches)
        val extras = a.moves.filterIsInstance<Move.Extra>()
        assertEquals(2, extras.size)
    }

    @Test
    fun `skipped word produces no match at that index (MISSED candidate)`() {
        val exp = expected("قل", "هو", "الله", "احد")
        // reciter skips الله (clearly different neighbor words, no high-sim substitute available)
        val words = listOf(word("قل"), word("هو"), word("احد"))
        val a = Aligner.align(words, exp, 0, 3, TrackerConfig())
        val matches = a.moves.filterIsInstance<Move.Match>().map { it.expectedIndex }.sorted()
        assertEquals(listOf(0, 1, 3), matches)
    }

    @Test
    fun `merge two observed words into one expected (1 to 2 split by reciter)`() {
        val exp = expected("بسم")
        // model emits "بس" + "م" for one word
        val words = listOf(word("بس"), word("م"))
        val a = Aligner.align(words, exp, 0, 0, TrackerConfig())
        val merges = a.moves.filterIsInstance<Move.Merge>()
        assertEquals(1, merges.size)
        assertEquals(0, merges[0].expectedIndex)
        assertEquals(1.0, merges[0].sim)
    }

    @Test
    fun `substitution lowers sim but still matches`() {
        val exp = expected("الرحمن")
        val words = listOf(word("الرحيم"))
        val a = Aligner.align(words, exp, 0, 0, TrackerConfig())
        val m = a.moves.filterIsInstance<Move.Match>()
        assertEquals(1, m.size)
        assertTrue(m[0].sim < 1.0 && m[0].sim > 0.5)
    }
}

class TrackerTest {

    private fun tracker(exp: List<ExpectedWord>, config: TrackerConfig = TrackerConfig()) =
        RecitationTrackerImpl().apply { reset(exp, config) }

    @Test
    fun `correct words become CORRECT on interior evidence`() {
        val exp = expected("بسم", "الله", "الرحمن", "الرحيم")
        val t = tracker(exp)
        val words = listOf(word("بسم", 0, 300), word("الله", 300, 600), word("الرحمن", 600, 900), word("الرحيم", 900, 1200))
        val u = t.onObservation(obs(words, 0, 1200, isFinal = true))
        assertEquals(WordState.CORRECT, u.states[0])
        assertEquals(WordState.CORRECT, u.states[1])
        assertEquals(WordState.CORRECT, u.states[2])
        assertEquals(WordState.CORRECT, u.states[3])
        assertEquals(4, u.pointer)
    }

    @Test
    fun `idempotency - re-observing identical window adds no vote`() {
        val exp = expected("بسم", "الله", "الرحمن")
        val t = tracker(exp, TrackerConfig()) // conservative needs 2 independent
        val words = listOf(word("الرحمن", 600, 900))
        // window where الرحمن is at boundary → NOT interior; never CORRECT
        val o1 = obs(words, 500, 1000)
        t.onObservation(o1)
        val s1 = t.snapshot()
        t.onObservation(o1) // identical
        val s2 = t.snapshot()
        assertEquals(s1.states, s2.states, "identical observation must not change states")
    }

    @Test
    fun `wrong word needs two independent observations in conservative preset`() {
        val exp = expected("قل", "هو", "الله")
        val t = tracker(exp)
        // الصمد substitutes for الله with context anchoring قل هو
        val w = listOf(word("قل", 0, 300), word("هو", 300, 600), word("الصمد", 600, 900))
        val u1 = t.onObservation(obs(w, 0, 1000, isFinal = true))
        assertEquals(WordState.CORRECT, u1.states[0])  // context anchors; الصمد interior vs الله: CLOSE
        assertEquals(WordState.CLOSE, u1.states[2])
        // second INDEPENDENT window (overlap 400 < 50% of shorter 1000)
        val u2 = t.onObservation(obs(w, 600, 1600))
        assertEquals(WordState.CLOSE, u2.states[2], "still CLOSE after 1 independent vote")
        // third independent window
        val u3 = t.onObservation(obs(w, 2000, 3000, isFinal = true))
        assertEquals(WordState.WRONG, u3.states[2], "WRONG after ≥2 independent interior votes")
    }

    @Test
    fun `balanced preset - single final interior observation can set WRONG`() {
        val exp = expected("قل", "هو", "الله")
        val t = tracker(exp, TrackerConfig(preset = TrackerConfig.Preset.BALANCED))
        val w = listOf(word("قل", 0, 300), word("هو", 300, 600), word("الصمد", 600, 900))
        val u = t.onObservation(obs(w, 0, 1000, isFinal = true))
        assertEquals(WordState.CORRECT, u.states[0])
        assertEquals(WordState.WRONG, u.states[2], "single FINAL interior vote suffices in Balanced")
    }

    @Test
    fun `nothing downgrades CORRECT`() {
        val exp = expected("بسم", "الله")
        val t = tracker(exp)
        // first: perfect interior CORRECT
        t.onObservation(obs(listOf(word("بسم", 0, 400), word("الله", 400, 800)), 0, 800, isFinal = true))
        assertEquals(WordState.CORRECT, t.snapshot().states[0])
        // then garbage observation overlapping
        t.onObservation(obs(listOf(word("صدق", 0, 800)), 0, 800))
        assertEquals(WordState.CORRECT, t.snapshot().states[0])
    }

    @Test
    fun `HINTED is terminal`() {
        val exp = expected("بسم", "الله", "الرحمن", "الرحيم")
        val t = tracker(exp)
        t.onObservation(obs(listOf(word("بسم", 0, 400)), 0, 400, isFinal = true))
        t.hint() // reveals الله → HINTED
        assertEquals(WordState.HINTED, t.snapshot().states[1])
        // later perfect evidence must NOT overwrite
        t.onObservation(obs(listOf(word("الله", 400, 800), word("الرحمن", 800, 1200)), 400, 1200, isFinal = true))
        assertEquals(WordState.HINTED, t.snapshot().states[1])
    }

    @Test
    fun `hint reveals next pending word only`() {
        val exp = expected("بسم", "الله", "الرحمن")
        val t = tracker(exp)
        t.hint()
        assertEquals(WordState.HINTED, t.snapshot().states[0])
        assertEquals(WordState.PENDING, t.snapshot().states[1])
        assertEquals(1, t.snapshot().pointer)
    }

    @Test
    fun `MISSED requires pointer beyond plus 3 and two independent skips`() {
        // Realistic partial sequence: the skip (word 2) is bracketed by matched words
        // in two INDEPENDENT observations while the pointer still allows the back-window
        // to reach it; the pointer then advances ≥3 beyond on the final.
        val exp = expected("w0", "w1", "w2", "w3", "w4", "w5")
        val t = tracker(exp)
        // partial 1: reciter said w0,w1,w3 (skipping w2) → matches 0,1,3 → pointer=4
        t.onObservation(obs(listOf(word("w0", 0, 200), word("w1", 200, 400), word("w3", 400, 600)), 0, 600, isFinal = false))
        // partial 2 (independent: non-overlapping window), brackets w2 via matches j1..j3
        t.onObservation(obs(listOf(word("w1", 700, 900), word("w3", 900, 1100)), 700, 1100, isFinal = false))
        // state not yet MISSED (pointer=4 < 2+3=5)
        assertEquals(WordState.CLOSE, t.snapshot().states[2])
        // final: recite through w5 → pointer=6 ≥ 5
        val u3 = t.onObservation(obs(listOf(word("w3", 1200, 1400), word("w4", 1400, 1600), word("w5", 1600, 1800)), 1200, 1800, isFinal = true))
        assertEquals(WordState.MISSED, u3.states[2])
    }

    @Test
    fun `repeated word before pointer minus backWords is EXTRA and never penalized`() {
        val exp = expected("w0", "w1", "w2", "w3", "w4", "w5", "w6")
        val t = tracker(exp)
        // recite w0..w4 fully → pointer=5; w0 is behind pointer−backWords (=2)
        t.onObservation(obs((0..4).map { word("w$it", it * 200L, it * 200L + 200) }, 0, 1000, isFinal = true))
        val u = t.snapshot()
        assertEquals(5, u.pointer)
        // reciter repeats w0: window with just w0 → matches nothing in [2..19] → EXTRA
        val u2 = t.onObservation(obs(listOf(word("w0", 1000, 1200)), 1000, 1200, isFinal = true))
        assertEquals(u.states[0], u2.states[0], "repeat behind window must not change state")
        assertTrue(u2.extras.isNotEmpty(), "repeat behind window should surface as EXTRA")
    }

    @Test
    fun `EXTRA dedup by text and 250ms bucket`() {
        val exp = expected("قل", "هو", "الله")
        val t = tracker(exp)
        // one window with صدق twice at nearby times → same bucket dedup
        t.onObservation(obs(listOf(word("صدق", 0, 200), word("صدق", 210, 400), word("قل", 0, 400)), 0, 400, isFinal = true))
        // overlapMs windows: same EXTRA counted once
        t.onObservation(obs(listOf(word("صدق", 50, 390), word("قل", 50, 390)), 50, 390))
        val u = t.snapshot()
        assertEquals(1, u.extras.count { it.text == "صدق" })
    }

    @Test
    fun `null-timing mode - token-index interiority`() {
        val exp = expected("بسم", "الله", "الرحمن", "الرحيم", "مالك", "يوم", "الدين")
        val t = tracker(exp, TrackerConfig())
        // no timings at all; kEdgeTokens=2 → words 0..1 and 5..6 are edges
        val ws = listOf(
            word("بسم"), word("الله"), word("الرحمن"), word("الرحيم"), word("مالك"), word("يوم"), word("الدين"),
        )
        val u = t.onObservation(obs(ws, 0, 7000, isFinal = true))
        // interior words 2..4 are CORRECT-capable; edges rely on final
        assertEquals(WordState.CORRECT, u.states[2])
        assertEquals(WordState.CORRECT, u.states[3])
        assertEquals(WordState.CORRECT, u.states[4])
    }

    @Test
    fun `boundary exclusion applies to partials not pause-finals`() {
        val exp = expected("بسم", "الله", "الرحمن")
        val t = tracker(exp, TrackerConfig())
        // PARTIAL window where بسم sits at the start boundary → not interior → not CORRECT yet
        t.onObservation(obs(listOf(word("بسم", 0, 300), word("الله", 300, 600)), 0, 600, isFinal = false))
        assertEquals(WordState.CLOSE, t.snapshot().states[0])
        // pause-FINAL over the same audio: interior rule relaxes
        t.onObservation(obs(listOf(word("بسم", 0, 300), word("الله", 300, 600)), 0, 600, isFinal = true))
        assertEquals(WordState.CORRECT, t.snapshot().states[0])
    }

    @Test
    fun `lost timer only advances during speech`() {
        val exp = expected("قل", "هو", "الله")
        val t = tracker(exp, TrackerConfig(lostAfterMs = 1000))
        // silence window (no usable speech): does not advance
        t.onObservation(obs(emptyList(), 0, 2000))
        val s1 = t.snapshot()
        assertEquals(WordState.PENDING, s1.states[0])
        // pointer must be unchanged, no resync trigger
        assertEquals(0, s1.pointer)
    }

    @Test
    fun `reanchor sets pointer to ayah start`() {
        val exp = (0 until 10).map { i ->
            ExpectedWord(i, surah = 1, ayah = if (i < 5) 1 else 2, text = "w$i")
        }
        val t = tracker(exp)
        t.reanchor(2)
        assertEquals(5, t.snapshot().pointer)
    }

    @Test
    fun `summary semantics - CORRECT only in X, HINTED excluded from Y`() {
        val exp = expected("قل", "هو", "الله", "احد")
        val t = tracker(exp)
        // Realistic window: 4 words over 2.4 s; هو interior (CORRECT); substitutions stay CLOSE;
        // قل at the start edge is NOT interior → CLOSE until the hint makes it HINTED.
        t.onObservation(obs(listOf(word("قل", 0, 600), word("هو", 600, 1200), word("الصمد", 1200, 1800), word("قلب", 1800, 2400)), 0, 2400, isFinal = true))
        // §9.2: boundary exclusion does NOT apply to natural pause-finals → the edge word قل
        // is also CORRECT on this pause-final.
        val before = t.snapshot()
        assertEquals(WordState.CORRECT, before.states[1])
        assertEquals(WordState.CORRECT, before.states[0])
        t.hint() // hints the first non-terminal word (الله here)
        val s = t.summary()
        assertEquals(2, s.correct)
        assertEquals(3, s.y) // 4 expected − 1 HINTED
        assertEquals("صحيحة: 2 من 3", s.headline)
        assertEquals(1, s.hinted)
        assertTrue(s.close >= 1, "substituted words stay CLOSE")
    }

    @Test
    fun `monotonic pointer with repeats`() {
        val exp = expected("قل", "هو", "الله", "احد")
        val t = tracker(exp)
        t.onObservation(obs(listOf(word("قل", 0, 200), word("هو", 200, 400)), 0, 400, isFinal = true))
        assertEquals(2, t.snapshot().pointer)
        // observe a window that includes هو again inside window (pointer-3..pointer+15)
        t.onObservation(obs(listOf(word("هو", 400, 600), word("الله", 600, 800)), 400, 800, isFinal = true))
        assertEquals(3, t.snapshot().pointer)
        // pointer never decreases
        assertTrue(t.snapshot().pointer >= 2)
    }
}

class OptionalPrefixTest {

    private fun obsWith(words: List<AsrWord>, s: Long, e: Long, fin: Boolean = true) =
        Observation(words = words, windowStartMs = s, windowEndMs = e, isFinal = fin, modelRev = "r")

    @Test
    fun `recited optional basmala is absorbed and never penalized when omitted`() {
        // Baqarah 1:1: optional basmala(4) + mandatory الٓمٓ (1)
        val exp = listOf(
            ExpectedWord(0, 2, 1, "بسم", optionalPrefix = true),
            ExpectedWord(1, 2, 1, "الله", optionalPrefix = true),
            ExpectedWord(2, 2, 1, "الرحمن", optionalPrefix = true),
            ExpectedWord(3, 2, 1, "الرحيم", optionalPrefix = true),
            ExpectedWord(4, 2, 1, "الم"),
        )
        val t = RecitationTrackerImpl().apply { reset(exp, TrackerConfig()) }

        // path A: reciter SKIPS the basmala entirely, recites only الم
        t.onObservation(obsWith(listOf(word("الم", 300, 900)), 0, 2400))
        val s = t.snapshot()
        assertEquals(WordState.PENDING, s.states[0], "optional word never MISSED")
        assertEquals(WordState.PENDING, s.states[1])
        assertEquals(WordState.PENDING, s.states[2])
        assertEquals(WordState.PENDING, s.states[3])
        assertEquals(WordState.CORRECT, s.states[4], "mandatory word unaffected")
        val sum = t.summary()
        assertEquals(1, sum.y, "Y excludes optional prefix words")

        // path B: fresh tracker, reciter RECITES the basmala + الم → absorbed
        val t2 = RecitationTrackerImpl().apply { reset(exp, TrackerConfig()) }
        t2.onObservation(
            obsWith(
                listOf(
                    word("بسم", 300, 900), word("الله", 900, 1500),
                    word("الرحمن", 1500, 2100), word("الرحيم", 2100, 2700),
                    word("الم", 2700, 3300),
                ),
                0, 3600,
            ),
        )
        val s2 = t2.snapshot()
        assertEquals(WordState.CORRECT, s2.states[0], "recited basmala word can be CORRECT (absorbed)")
        assertEquals(WordState.CORRECT, s2.states[4])
        assertEquals(5 - 0, t2.summary().y, "Y = 5 − 0 HINTED − 0 (optional recited → counted normally)")
    }

    @Test
    fun `fatihah basmala is mandatory and can be MISSED`() {
        val exp = (0 until 8).map { i ->
            ExpectedWord(i, 1, if (i < 4) 1 else 2, "f$i", optionalPrefix = false)
        }
        val t = RecitationTrackerImpl().apply { reset(exp, TrackerConfig()) }
        // reciter skips f2 (mandatory) with partial bracketing + final
        t.onObservation(obsWith(listOf(word("f0", 0, 200), word("f1", 200, 400), word("f3", 400, 600)), 0, 600, fin = false))
        t.onObservation(obsWith(listOf(word("f1", 700, 900), word("f3", 900, 1100)), 700, 1100, fin = false))
        val u3 = t.onObservation(obsWith(listOf(word("f3", 1200, 1400), word("f4", 1400, 1600), word("f5", 1600, 1800), word("f6", 1800, 2000), word("f7", 2000, 2200)), 1200, 2200))
        assertEquals(WordState.MISSED, u3.states[2], "mandatory skipped word becomes MISSED")
    }
}
