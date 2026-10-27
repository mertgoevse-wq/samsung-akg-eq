package de.sonora.app.ai.inference

/**
 * Ringpuffer für überlappende Audioanalysefenster.
 * Thread-sicher für Single-Producer/Single-Consumer.
 */
class RingBuffer(capacity: Int) {
    private val buf = FloatArray(capacity)
    private val cap = capacity
    private var writePos = 0
    private var count = 0

    @Synchronized
    fun write(samples: FloatArray) {
        for (s in samples) {
            buf[writePos] = s
            writePos = (writePos + 1) % cap
            if (count < cap) count++
        }
    }

    @Synchronized
    fun readLatest(n: Int): FloatArray {
        val size = minOf(n, count)
        val out = FloatArray(size)
        var pos = (writePos - size + cap) % cap
        for (i in 0 until size) {
            out[i] = buf[pos]
            pos = (pos + 1) % cap
        }
        return out
    }

    @Synchronized
    fun available(): Int = count

    @Synchronized
    fun clear() { writePos = 0; count = 0 }
}
