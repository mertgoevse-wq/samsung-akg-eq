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

    fun normalize(input: FloatArray): FloatArray {
        var peak = 0f
        for (v in input) peak = maxOf(peak, kotlin.math.abs(v))
        if (peak == 0f) return input.copyOf()
        val scale = 1f / peak
        return FloatArray(input.size) { input[it] * scale }
    }
}