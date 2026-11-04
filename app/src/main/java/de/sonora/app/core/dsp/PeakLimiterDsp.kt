package de.sonora.app.core.dsp

import kotlin.math.pow

/**
 * Sample-domain Peak-Limiter als echter PCM-Sample-Prozessor.
 *
 * Begrenzt den Spitzenpegel des Signals auf eine maximale Amplitude
 * (Ceiling in dBFS). Uebersteuerungen werden mit kurzer Attack- und
 * Release-Huellkurve gedemfaeundet, sodass keine harten Clip-Artefakte
 * entstehen.
 *
 * Eigenschaften:
 * - Ceiling in dBFS (Default: -1 dBFS)
 * - Attack/Release-Huellkurve (One-Pole, sample-rate-aware)
 * - True Bypass (enabled = false: trockenes Durchreichen)
 * - Denormal-Schutz und NaN/Infinity-Resilienz
 * - Reine Kotlin-Implementierung, JVM-testbar, keine Android-Abhaengigkeit
 *
 * Ergänzt [Limiter] (Gain-Staging/Headroom-Rechner in de.sonora.app.core)
 * um eine wirkliche Probenbegrenzung im Signalweg.
 */
class PeakLimiterDsp(private var sampleRate: Int = 44100) {

    /** Ziel-Ceiling in dBFS (0 = 0 dBFS, negativer Wert dämpft). */
    var targetCeilingDb: Float = -1f
        private set

    /** Aktuelle geglaettete Ceiling. */
    var currentCeilingDb: Float = -1f
        private set

    private var smoothingFactor: Float

    private var enabled: Boolean = true

    private var envelope: Float = 0f

    init {
        smoothingFactor = onePoleSmoothing(sampleRate)
    }

    fun getSampleRate(): Int = sampleRate

    fun setSampleRate(newSampleRate: Int) {
        sampleRate = newSampleRate.coerceAtLeast(1)
        smoothingFactor = onePoleSmoothing(sampleRate)
        attackCoeff = attackCoefficient(sampleRate)
        releaseCoeff = releaseCoefficient(sampleRate)
    }

    private var attackCoeff: Float = attackCoefficient(44100)
    private var releaseCoeff: Float = releaseCoefficient(44100)

    fun isEnabled(): Boolean = enabled

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** Ziel-Ceiling setzen (dBFS). Wird stetig angefahren. */
    fun setTargetCeilingDb(ceilingDb: Float) {
        targetCeilingDb = if (ceilingDb.isNaN() || ceilingDb.isInfinite()) -1f
            else ceilingDb.coerceIn(-24f, 0f)
    }

    /** Ein einzelnes Sample verarbeiten. */
    fun processSample(input: Float): Float {
        if (!enabled) return flushDenormal(input)

        currentCeilingDb += (targetCeilingDb - currentCeilingDb) * smoothingFactor

        val absSample = kotlin.math.abs(input)

        // Huellkurve: Attack wenn lauter als aktueller Envelope, sonst Release
        val targetEnv = absSample
        envelope = if (targetEnv > envelope) {
            envelope + (targetEnv - envelope) * attackCoeff
        } else {
            envelope + (targetEnv - envelope) * releaseCoeff
        }

        // Pegel in dBFS
        val envDb = if (envelope <= 0f) -120f
            else 20f * kotlin.math.log10(envelope.toDouble()).toFloat()

        // Wie viel dB ueber dem Ceiling?
        val exceedDb = envDb - currentCeilingDb

        val gainDb = if (exceedDb > 0f) -exceedDb else 0f

        val gainLinear = if (gainDb.isNaN() || gainDb.isInfinite()) 1f
            else 10.0.pow(gainDb.toDouble() / 20.0).toFloat()

        return flushDenormal(input * gainLinear)
    }

    /** Puffer in-place verarbeiten (mono). */
    fun process(samples: FloatArray, count: Int = samples.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, samples.size)
        for (i in 0 until safeCount) {
            samples[i] = processSample(samples[i])
        }
    }

    /** Stereo-Paar in-place verarbeiten (gemeinsame Huellkurve). */
    fun processStereo(left: FloatArray, right: FloatArray, count: Int = left.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, left.size).coerceAtMost(right.size)
        for (i in 0 until safeCount) {
            left[i] = processSample(left[i])
            right[i] = processSample(right[i])
        }
    }

    fun reset() {
        envelope = 0f
        currentCeilingDb = targetCeilingDb
    }

    // -----------------------------------------------------------------------
    // Intern
    // -----------------------------------------------------------------------

    private fun attackCoefficient(sr: Int): Float {
        return onePoleCoeff(0.001f, sr) // ~1 ms Attack
    }

    private fun releaseCoefficient(sr: Int): Float {
        return onePoleCoeff(0.050f, sr) // ~50 ms Release
    }

    private fun onePoleCoeff(timeSeconds: Float, sr: Int): Float {
        val s = sr.coerceAtLeast(1)
        return 1.0f - kotlin.math.exp(-1.0f / (s.toFloat() * timeSeconds)).toFloat()
    }

    private fun onePoleSmoothing(sr: Int): Float {
        val s = sr.coerceAtLeast(1)
        return (1.0f - kotlin.math.exp(-1.0f / (s.toFloat() * 0.020f)).toFloat())
    }

    private fun flushDenormal(x: Float): Float =
        if (x.isNaN() || x.isInfinite()) 0f else if (kotlin.math.abs(x) < 1e-20f) 0f else x

    private fun coerceInt(min: Int, max: Int): Int = min.coerceAtLeast(min).coerceAtMost(max)

    companion object {
        fun dbToLinear(db: Float): Float =
            if (db.isNaN() || db.isInfinite()) 1f
            else 10.0.pow(db.toDouble() / 20.0).toFloat()
    }
}
