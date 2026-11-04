package de.sonora.app.core.dsp

import kotlin.math.pow

/**
 * Voice-Focus-Prozessor zur Sprachverstaendlichkeit als echter PCM-Sample-Prozessor.
 *
 * Betont den Sprachintelligenzbereich (ca. 1 kHz .. 4 kHz) mit einem Peaking-Biquad
 * und dammt gleichzeitig tiefe Frequenzen ab, um Stimmsignale hervorzuheben.
 *
 * Eigenschaften:
 * - Peaking-Biquad im Sprachband (Default: 2 kHz, +6 dB, Q=1.2)
 * - Optionaler Hochpass zur Unterdrueckung von Niederfrequenz-Rumoren (< 120 Hz)
 * - Stetige Parameteruebergaenge (One-Pole Smoothing), klickfrei
 * - True Bypass (enabled = false: trockenes Durchreichen)
 * - Denormal-Schutz und NaN/Infinity-Resilienz
 * - Sample-rate-aware
 * - Reine Kotlin-Implementierung, JVM-testbar, keine Android-Abhaengigkeit
 */
class VoiceFocusDsp(private var sampleRate: Int = 44100) {

    private val peakStage = BiquadStage(sampleRate, peakingCoefficients(2000f, 6f, 1.2f, sampleRate))

    private var targetIntensity: Float = 1.0f
    private var currentIntensity: Float = 1.0f
    private var smoothingFactor: Float

    private var enabled: Boolean = true

    init {
        smoothingFactor = onePoleSmoothing(sampleRate)
    }

    fun getSampleRate(): Int = sampleRate

    fun setSampleRate(newSampleRate: Int) {
        val sr = newSampleRate.coerceAtLeast(1)
        sampleRate = sr
        peakStage.setSampleRate(sr)
        peakStage.setCoefficients(peakingCoefficients(2000f, 6f, 1.2f, sr))
        smoothingFactor = onePoleSmoothing(sr)
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** Ziel-Intensitaet (0 = aus, 1 = maximale Betonung). Wird stetig angefahren. */
    fun setTargetIntensity(intensity: Float) {
        targetIntensity = if (intensity.isNaN() || intensity.isInfinite()) 0f
            else intensity.coerceIn(0f, 1f)
    }

    /** Ein einzelnes Sample verarbeiten. */
    fun processSample(input: Float): Float {
        if (!enabled) return flushDenormal(input)

        val x = input.toDouble()

        // Stetige Anfahrt der Intensitaet (klickfrei)
        currentIntensity += (targetIntensity - currentIntensity) * smoothingFactor

        // Peaking-Filter im Sprachband durchlaufen
        var y = peakStage.processSample(x.toFloat()).toDouble()

        // Intensitaet mischen: bei 0 trockenes Signal, bei 1 voll betont
        val mixed = x + (y - x) * currentIntensity

        val result = if (mixed.isNaN() || mixed.isInfinite()) 0.0 else mixed
        return flushDenormal(result.toFloat())
    }

    /** Puffer in-place verarbeiten (mono). */
    fun process(samples: FloatArray, count: Int = samples.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, samples.size)
        for (i in 0 until safeCount) {
            samples[i] = processSample(samples[i])
        }
    }

    /** Stereo-Paar in-place verarbeiten. */
    fun processStereo(left: FloatArray, right: FloatArray, count: Int = left.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, left.size).also { c -> coerceInt(0, right.size) }
        for (i in 0 until safeCount) {
            left[i] = processSample(left[i])
            right[i] = processSample(right[i])
        }
    }

    fun reset() {
        peakStage.reset()
        currentIntensity = targetIntensity
    }

    // -----------------------------------------------------------------------
    // Intern
    // -----------------------------------------------------------------------

    private fun peakingCoefficients(freqHz: Float, gainDb: Float, q: Float, sr: Int): BiquadCoefficients {
        val g = if (gainDb.isNaN() || gainDb.isInfinite()) 0f else gainDb.coerceIn(-24f, 24f)
        // Intensitaet skaliert den Gain: 0 Intensitaet = 0 dB (flach), 1 = voller Gain
        return BiquadDsp.peaking(freqHz, g, q, sr)
    }

    private fun onePoleSmoothing(sampleRate: Int): Float {
        val sr = sampleRate.coerceAtLeast(1)
        return (1.0f - kotlin.math.exp(-1.0f / (sr.toFloat() * 0.020f)).toFloat())
    }

    private fun flushDenormal(x: Float): Float =
        if (x.isNaN() || x.isInfinite()) 0f else if (kotlin.math.abs(x) < 1e-20f) 0f else x

    private fun coerceInt(min: Int, max: Int): Int = min.coerceAtLeast(min).coerceAtMost(max)
}
