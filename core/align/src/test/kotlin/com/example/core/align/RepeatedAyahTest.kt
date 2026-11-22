package com.example.core.align

import com.example.core.align.model.AsrWord
import com.example.core.align.model.Observation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V-024/V-031: repeated identical ayahs after N() (surah 55 refrain ×31; surah 77 ×10).
 * Invariants under test:
 *  a) a FINAL matching an EARLIER identical refrain must never move the pointer BACKWARD;
 *  b) re-anchoring prefers the nearest FORWARD match;
 *  c) a segment-INITIAL skip must still produce MISSED — free leading expected skips must not
 *     permanently erase absence evidence.
 */
class RepeatedAyahTest {

    private fun obs(words: List<AsrWord>, s: Long, e: Long, fin: Boolean) =
        Observation(words = words, windowStartMs = s, windowEndMs = e, isFinal = fin, modelRev = "r")

    private fun w(t: String, s: Long) = AsrWord(t, s, s + 350)

    /** 5 refrains of surah 55 (identical after N) with distinct intro words. */
    private val refrain = listOf("فباي", "ءالاء", "ربكما", "تكذبان")

    @Test
    fun `pointer never moves backward on identical refrains`() {
        // expected: intro0..4 + refrain ×5 (a 5-refrain slice of surah 55's structure)
        val expected = (0 until 5).flatMap { i ->
            listOf(ExpectedWord(i * 5 + 0, 55, i + 1, "intro$i")) + refrain.mapIndexed { k, t ->
                ExpectedWord(i * 5 + 1 + k, 55, i + 1, t)
            }
        }
        val t = RecitationTrackerImpl().apply { reset(expected, TrackerConfig()) }
        // recite all 5 intro+refrains in order (words at 400ms pitch)
        val recited = expected.map { AsrWord(it.text, 0L, 0L) } // timing set below
        var time = 0L
        val timed = expected.map { w(it.text, time).also { time += 400 } }
        for (chunkStart in listOf(0, 5, 10, 15, 20)) {
            val chunk = timed.subList(chunkStart, chunkStart + 5)
            val o = obs(chunk, chunk.first().startMs!!, chunk.last().endMs!!, fin = true)
            val pointerBefore = t.pointerPublic()
            t.onObservation(o)
            val pointerAfter = t.pointerPublic()
            assertTrue(pointerAfter >= pointerBefore, "pointer must never move backward (was $pointerBefore, now $pointerAfter) — identical refrains must re-anchor FORWARD")
        }
        // all words eventually CORRECT (nearest-forward re-anchoring)
        val states = t.snapshot().states
        val correct = states.count { it.value == WordState.CORRECT }
        assertTrue(correct >= 20, "nearest-forward re-anchoring must still mark words CORRECT; got $correct/25 (${states.filter { it.value != WordState.CORRECT }})")
    }

    @Test
    fun `FINAL matching an EARLIER identical refrain re-anchors forward, not backward`() {
        // reciter repeats the FIRST refrain verbatim (same audio, new position):
        val expected = (0 until 2).flatMap { i ->
            listOf(ExpectedWord(i * 5 + 0, 55, i + 1, "intro$i")) + refrain.mapIndexed { k, t ->
                ExpectedWord(i * 5 + 1 + k, 55, i + 1, t)
            }
        }
        val t = RecitationTrackerImpl().apply { reset(expected, TrackerConfig()) }
        // recite refrain 1 fully
        var time = 0L
        val first = expected.take(5).map { w(it.text, time).also { time += 400 } }
        t.onObservation(obs(first, 0, time, fin = true))
        val p1 = t.pointerPublic()
        assertEquals(5, p1, "after refrain 1 the pointer is at 5")
        // now a FINAL arrives containing ONLY the first refrain's words again (a repeat)
        val repeat = first // same words/times would be deduped; use NEW later times:
        var time2 = 5000L
        val repeatTimed = first.map { AsrWord(it.text, time2, time2 + 350).also { time2 += 400 } }
        val u = t.onObservation(obs(repeatTimed, 5000, time2, fin = true))
        assertTrue(t.pointerPublic() >= 5, "repeat of an earlier identical refrain must NOT pull the pointer backward")
        // and the repeat words are stats (EXTRA) or absorbed — never MISSED for the recited set
        val sum = t.summary()
        assertTrue(sum.missed == 0, "no word may be MISSED because of a repeat; missed=${sum.missed}")
    }

    @Test
    fun `segment-initial skip still produces MISSED despite free leading skips`() {
        // the reciter starts mid-refrain (skips intro0+first words) — segment-INITIAL skip:
        val expected = (0 until 3).flatMap { i ->
            listOf(ExpectedWord(i * 5 + 0, 55, i + 1, "intro$i")) + refrain.mapIndexed { k, t ->
                ExpectedWord(i * 5 + 1 + k, 55, i + 1, t)
            }
        }
        val t = RecitationTrackerImpl().apply { reset(expected, TrackerConfig()) }
        // FINAL 1: recite from intro1 (idx5) onward — idx0..4 skipped from the START
        var time = 0L
        val fromIdx5 = expected.drop(5).map { w(it.text, time).also { time += 400 } }
        t.onObservation(obs(fromIdx5, 0, time, fin = true))
        // pointer is now 15 (past all 3 groups); absence for 0..4 recorded by final1 (V-031
        // leading-skip absence) — but MISSED needs ≥2 independent absence votes. FINAL 2
        // re-covers later audio (a second final with different windows → independent):
        var time2 = 8000L
        val reobserved = expected.drop(5).take(5).map { w(it.text, time2).also { time2 += 400 } }
        t.onObservation(obs(reobserved, 8000, time2, fin = true))
        val states = t.snapshot().states
        // leading free skips must not erase absence evidence: idx0..4 must reach MISSED
        assertEquals(WordState.MISSED, states[0], "segment-initial skip must still be MISSED (absence evidence permanent)")
        assertEquals(WordState.MISSED, states[1])
        assertEquals(WordState.MISSED, states[4])
    }
}
