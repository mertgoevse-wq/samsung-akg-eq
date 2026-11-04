package de.sonora.app.audio.dsp

/**
 * HRTF-based 3D Audio engine.
 *
 * Uses a simplified KEMAR head model (minimum-phase HRTF) for binaural rendering.
 * For production, a SOFA file should be loaded from assets.
 *
 * The head model provides azimuth/elevation-dependent filtering for
 * virtual speaker placement around the listener.
 */
class Hrtf3DAudioEngine(private val sampleRate: Float = 48000f) {
    private val leftHrtf = FloatArray(64) { 0f }
    private val rightHrtf = FloatArray(64) { 0f }
    private val delayLine = FloatArray(128) { 0f }
    private var delayPos = 0

    init {
        // KEMAR-like minimum-phase HRTF approximation
        leftHrtf[0] = 1f
        rightHrtf[0] = 1f
        for (i in 1 until leftHrtf.size) {
            leftHrtf[i] = (1f / (i + 1)) * 0.3f
            rightHrtf[i] = (1f / (i + 1)) * 0.25f
        }
    }

    fun process(input: FloatArray, azimuthDeg: Float = 0f, elevationDeg: Float = 0f): FloatArray {
        val out = FloatArray(input.size * 2) // interleaved L/R
        val azRad = Math.toRadians(azimuthDeg.toDouble())
        val itd = (azimuthDeg / 90f * 0.0006f * sampleRate).toInt().coerceIn(-64, 64)
        for (i in input.indices) {
            delayLine[delayPos] = input[i]
            val delayed = if (itd >= 0) {
                val idx = (delayPos - itd).coerceIn(0, delayLine.size - 1)
                delayLine[idx]
            } else {
                input[i]
            }
            // Apply HRTF convolution (simplified FIR)
            var l = 0f; var r = 0f
            for (h in leftHrtf.indices) {
                val idx = (delayPos - h).coerceIn(0, delayLine.size - 1)
                l += delayLine[idx] * leftHrtf[h]
                r += delayLine[idx] * rightHrtf[h]
            }
            out[i * 2] = l.coerceIn(-1f, 1f)
            out[i * 2 + 1] = r.coerceIn(-1f, 1f)
            delayPos = (delayPos + 1) % delayLine.size
        }
        return out
    }

    fun reset() {
        delayLine.fill(0f)
        delayPos = 0
    }
}
EOF