package com.iqraa.core.audio

/**
 * Fixed-capacity ring buffer for 16 kHz mono PCM frames (§6.2 audio front-end).
 * Thread-safe single-producer (capture) / single-consumer (segmenter) via a lock.
 * Overwrites are FORBIDDEN by policy: when full, [write] drops the oldest data and returns
 * the number of dropped samples (the segmenter consumes continuously; overflow means the
 * pipeline is stalled and the caller should back-pressure).
 */
class PcmRingBuffer(private val capacitySamples: Int) {
    private val data = ShortArray(capacitySamples)
    private var head = 0 // next read position
    private var count = 0
    private var totalDropped = 0L

    private val lock = Object()

    /** Write samples; returns the number of OLDEST samples dropped if the buffer was full. */
    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Int {
        if (length > capacitySamples) {
            // degenerate: keep only the newest tail
            return synchronized(lock) {
                val keep = samples.copyOfRange(offset + length - capacitySamples, offset + length)
                System.arraycopy(keep, 0, data, 0, capacitySamples)
                head = 0
                count = capacitySamples
                val dropped = totalDropped + (length - capacitySamples)
                (dropped - totalDropped).toInt().also { totalDropped = dropped }
            }
        }
        return synchronized(lock) {
            var dropped = 0
            val available = capacitySamples - count
            if (length > available) {
                val overflow = length - available
                // drop the oldest `overflow` samples
                head = (head + overflow) % capacitySamples
                count -= overflow
                dropped = overflow
                totalDropped += overflow
            }
            val writeStart = (head + count) % capacitySamples
            val first = minOf(length, capacitySamples - writeStart)
            System.arraycopy(samples, offset, data, writeStart, first)
            System.arraycopy(samples, offset + first, data, 0, length - first)
            count += length
            dropped
        }
    }

    /** Read up to [maxSamples] oldest samples; returns the number actually read. */
    fun read(out: ShortArray, maxSamples: Int = out.size): Int {
        if (maxSamples > out.size) throw IllegalArgumentException("maxSamples > out size")
        return synchronized(lock) {
            val n = minOf(maxSamples, count)
            val first = minOf(n, capacitySamples - head)
            System.arraycopy(data, head, out, 0, first)
            if (n > first) System.arraycopy(data, 0, out, first, n - first)
            head = (head + n) % capacitySamples
            count -= n
            n
        }
    }

    fun available(): Int = synchronized(lock) { count }

    fun droppedTotal(): Long = synchronized(lock) { totalDropped }

    fun clear() = synchronized(lock) {
        head = 0; count = 0
    }
}
