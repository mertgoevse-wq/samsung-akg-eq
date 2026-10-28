package de.sonora.app.ai.inference

/**
 * Korrektes PCM-Preprocessing für On-Device-Audio-ML.
 * - Mono-Konvertierung
 * - Resampling auf Zielsamplerate (lineare Interpolation, ausreichend für Prototyp)
 * - Normalisierung auf [-1,1]
 */
object PcmPreprocessor {
    fun toMono(samples: FloatArray, channels: Int): FloatArray {
        if (channels <= 1) return samples.copyOf()
        val out = FloatArray(samples.size / channels)
        for (i in out.indices) {
            var acc = 0f
            for (c in 0 until channels) acc += samples[i * channels + c]
            out[i] = acc / channels
        }
        return out
    }

    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return input.copyOf()
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLen = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in out.indices) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceIn(0, input.size - 1)
            val frac = (srcPos - i0).toFloat()
            out[i] = input[i0] * (1 - frac) + input[i1] * frac
        }
        return out
    }

    /**
     * Normalizes audio to [-1, 1] range. Use [normalizeToUnitRange] for peak
     * normalization or [clampToUnitRange] for safe clamping without distortion.
     *
     * For YAMNet compatibility: the model expects 16-bit PCM represented as
     * float32 in [-1.0, 1.0]. Peak normalization should NOT be applied blindly
     * as it can distort the signal dynamics. Use [clampToUnitRange] instead.
     */
    fun normalize(input: FloatArray): FloatArray {
        return normalizeToUnitRange(input)
    }

    /**
     * Peak normalization to [-1, 1]. Use with caution — this can distort
     * signals that intentionally have low peak levels relative to their
     * overall energy content. Prefer [clampToUnitRange] for YAMNet.
     */
    fun normalizeToUnitRange(input: FloatArray): FloatArray {
        var peak = 0f
        for (v in input) peak = maxOf(peak, kotlin.math.abs(v))
        if (peak == 0f) return input.copyOf()
        val scale = 1f / peak
        return FloatArray(input.size) { input[it] * scale }
    }

    /**
     * Clamps values to [-1, 1] without peak normalization. This preserves
     * the original signal dynamics while ensuring compatibility with models
     * that expect bounded float input (e.g., YAMNet float32).
     */
    fun clampToUnitRange(input: FloatArray): FloatArray {
        return FloatArray(input.size) { input[it].coerceIn(-1f, 1f) }
    }
}