package com.example.core.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §8.4 scheduling law + §6.2 segmenter + ring buffer — host-side, fake RTFs (P3). */
class SchedulerTest {

    private fun scheduler() = Scheduler(SchedulerConfig(partialEveryMs = 1000, partialWindowMs = 2500))

    @Test
    fun `law - effective cadence = max(partialEveryMs, RTF x W) for fake RTFs`() {
        val s = scheduler()
        // W = 2500 ms. Assert the ladder:
        assertEquals(1000L, s.effectivePartialEveryMs(0.2))  // RTF×W = 500 < 1000 → floor 1000
        assertEquals(1250L, s.effectivePartialEveryMs(0.5))  // RTF×W = 1250 > 1000 → 1250
        assertEquals(3500L, s.effectivePartialEveryMs(1.4))  // RTF×W = 3500 → 3500
    }

    @Test
    fun `law - RTF 0-5 stretches cadence beyond partialEveryMs`() {
        val s = scheduler()
        assertEquals(1250L, s.effectivePartialEveryMs(0.5))   // 0.5 × 2500 = 1250 > 1000
        assertEquals(2250L, s.effectivePartialEveryMs(0.9))  // 0.9 × 2500 = 2250
        assertEquals(1000L, s.effectivePartialEveryMs(0.3))  // 750 < 1000 → floor at 1000
    }

    @Test
    fun `queue depth 1 - newer replaces queued, final never starves`() {
        val s = scheduler()
        s.enqueue(DecodeRequest(0, 2500, isFinal = false))
        s.enqueue(DecodeRequest(1000, 3500, isFinal = false)) // newer replaces
        assertEquals(1000L, s.peek()!!.windowStartMs)
        s.enqueue(DecodeRequest(2000, 4500, isFinal = true))  // final
        // a newer non-final cannot evict a queued final
        s.enqueue(DecodeRequest(3000, 5500, isFinal = false))
        assertTrue(s.peek()!!.isFinal, "queued FINAL never starves")
        assertEquals(2000L, s.peek()!!.windowStartMs)
        assertNotNull(s.take(), "queued final taken")
        assertNull(s.peek(), "queue empty after take")
    }

    @Test
    fun `stale results dropped by windowEndMs`() {
        val s = scheduler()
        assertTrue(s.onComplete(DecodeResult(0, 2500, isFinal = false, decodeDurationMs = 500)))
        // a late-arriving older windowEnd is stale:
        assertFalse(s.onComplete(DecodeResult(0, 2000, isFinal = false, decodeDurationMs = 400)))
        // equal windowEnd for a partial is also stale (already emitted):
        assertFalse(s.onComplete(DecodeResult(1000, 2500, isFinal = false, decodeDurationMs = 400)))
        // a newer partial passes:
        assertTrue(s.onComplete(DecodeResult(1000, 3500, isFinal = false, decodeDurationMs = 600)))
        // a FINAL always passes (even with an older windowEnd — pause-final semantics):
        assertTrue(s.onComplete(DecodeResult(0, 2500, isFinal = true, decodeDurationMs = 800)))
    }

    @Test
    fun `shouldDecodePartialAt enforces the effective cadence`() {
        val s = scheduler()
        assertTrue(s.shouldDecodePartialAt(0, 0.2))      // t=0 first decode
        assertFalse(s.shouldDecodePartialAt(900, 0.2))   // < 1000 since t=0
        assertTrue(s.shouldDecodePartialAt(1000, 0.2))
        // RTF 0.5 → cadence 1250:
        assertFalse(s.shouldDecodePartialAt(2000, 0.5))   // only 1000 since t=1000 < 1250
        assertTrue(s.shouldDecodePartialAt(2250, 0.5))
    }
}

class PcmRingBufferTest {

    @Test
    fun `fifo order preserved across wrap`() {
        val b = PcmRingBuffer(8)
        assertEquals(0, b.write(shortArrayOf(1, 2, 3, 4)))
        val out = ShortArray(4)
        assertEquals(4, b.read(out))
        assertTrue(out.contentEquals(shortArrayOf(1, 2, 3, 4)))
        // wrap: write 6 more (4+2 over capacity 8 after the first 4 read → no drop)
        assertEquals(0, b.write(shortArrayOf(5, 6, 7, 8)))
        assertEquals(0, b.write(shortArrayOf(9, 10)))
        assertEquals(6, b.available())
        val out2 = ShortArray(6)
        assertEquals(6, b.read(out2))
        assertTrue(out2.contentEquals(shortArrayOf(5, 6, 7, 8, 9, 10)))
    }

    @Test
    fun `overflow drops the OLDEST samples and reports the count`() {
        val b = PcmRingBuffer(4)
        b.write(shortArrayOf(1, 2))
        val dropped = b.write(shortArrayOf(3, 4, 5, 6)) // 4+4 > 4 → drop 2 oldest (1,2)
        assertEquals(2, dropped)
        val out = ShortArray(4)
        assertEquals(4, b.read(out))
        assertTrue(out.contentEquals(shortArrayOf(3, 4, 5, 6)))
        assertEquals(2L, b.droppedTotal())
    }

    @Test
    fun `degenerate write larger than capacity keeps the newest tail`() {
        val b = PcmRingBuffer(3)
        val dropped = b.write(shortArrayOf(1, 2, 3, 4, 5, 6))
        assertEquals(3, dropped)
        val out = ShortArray(3)
        assertEquals(3, b.read(out))
        assertTrue(out.contentEquals(shortArrayOf(4, 5, 6)))
    }

    @Test
    fun `read respects requested size and available`() {
        val b = PcmRingBuffer(10)
        b.write(shortArrayOf(1, 2, 3, 4, 5))
        val out = ShortArray(2)
        assertEquals(2, b.read(out, 2))
        assertTrue(out.contentEquals(shortArrayOf(1, 2)))
        assertEquals(3, b.available())
    }
}

class SegmenterTest {

    private fun seg() = Segmenter(SegmenterParams())

    @Test
    fun `pause after speech emits one FINAL covering the segment`() {
        val s = seg()
        val ev1 = s.onVad(0, 2000, true)
        // partials at 1000, 2000
        assertEquals(2, ev1.count { it is SegmenterEvent.Partial })
        // silence frame that covers 500 ms of silence already fires the FINAL (pause elapsed
        // at endMs − lastSpeechEndMs ≥ pauseFinalMs):
        val ev2 = s.onVad(2000, 3000, false)
        assertEquals(1, ev2.size, "a 1000 ms silence frame ends the segment")
        val fin = ev2[0] as SegmenterEvent.Final
        assertEquals(0L, fin.windowStartMs)
        assertEquals(2000L, fin.windowEndMs)
        assertFalse(fin.forcedCut)
        // further silence emits nothing
        val ev3 = s.onVad(3000, 3500, false)
        assertTrue(ev3.isEmpty())
    }

    @Test
    fun `force-cut at maxSegmentMs with overlap carried`() {
        val s = seg()
        val ev = s.onVad(0, 8000, true) // 8000ms speech → force-cut
        val fin = ev.filterIsInstance<SegmenterEvent.Final>().single()
        assertTrue(fin.forcedCut)
        assertEquals(0L, fin.windowStartMs)
        assertEquals(8000L, fin.windowEndMs)
        // speech continues: the next segment starts 1000ms back (overlap)
        val ev2 = s.onVad(8000, 8100, true)
        val nextPartial = ev2.filterIsInstance<SegmenterEvent.Partial>().first()
        assertEquals(7000L, nextPartial.windowStartMs)
    }

    @Test
    fun `partials are trailing windows of partialWindowMs`() {
        val s = seg()
        val ev = s.onVad(0, 6000, true)
        val partials = ev.filterIsInstance<SegmenterEvent.Partial>()
        assertEquals(listOf(1000L, 2000L, 3000L, 4000L, 5000L, 6000L), partials.map { it.windowEndMs })
        // trailing 2500 windows, truncated at segment start
        assertEquals(0L, partials[0].windowStartMs) // max(segmentStart=0, 1000-2500)=0 → truncated
        assertEquals(500L, partials[2].windowStartMs) // max(0, 3000-2500)=500
    }

    @Test
    fun `long silence then new speech starts a fresh segment`() {
        val s = seg()
        s.onVad(0, 1000, true)
        s.onVad(1000, 2500, false) // final at 1500+
        val ev = s.onVad(5000, 6000, true)
        val partials = ev.filterIsInstance<SegmenterEvent.Partial>()
        assertEquals(5000L, partials.first().windowStartMs)
    }
}
