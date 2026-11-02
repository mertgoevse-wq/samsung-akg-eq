package de.sonora.app.core.dsp

import kotlin.math.pow

/**
 * Dynamik-Kompressor (Dynamic Range Compression) als echter PCM-Sample-Prozessor.
 *
 * Reduziert den Dynamikumfang: Signale ueber einem Schwellenwert werden mit einem
 * verstaerkten Verhaeltnis (ratio) gedemfaeundet. Signale unter dem Schwellenwert
 * bleiben unveraendert.
 *
 * Eigenschaften:
 * - Peak-Detektion mit Attack/Release-Huellkurve (One-Pole)
 * - Threshold in dBFS, Ratio (z.B. 4:1), Knee in dB (weicheres Einsetzen)
 * - Makeup-Gain zur Pegelwiederherstellung
 * - Stetige Parameteruebergaenge (One-Pole Smoothing), klickfrei
 * - True Bypass (enabled = false: trockenes Durchreichen)
 * - Denormal-Schutz und NaN/Infinity-Resilienz
 * - Sample-rate-aware (Attack/Release in Samples abhaengig von Abtastrate)
 * - Reine Kotlin-Implementierung, JVM-testbar, keine Android-Abhaengigkeit
 */
class CompressorDsp(private var sampleRate: Int = 44100) {

    /** Ziel-Threshold in dBFS (0 = 0 dBFS, negativer Wert = leiser). */
    var targetThresholdDb: Float = -20f
        private set

    /** Kompressionsverhaeltnis, z.B. 2.0f = 2:1. */
    var targetRatio: Float = 4.0f
        private set

    /** Knee-Breite in dB (weicheres Einsetzen um den Schwellenwert). */
    var targetKneeDb: Float = 4.0f
        private set

    /** Makeup-Gain in dB (Pegelwiederherstellung nach Kompression). */
    var targetMakeupDb: Float = 0f
        private set

    /** Aktuelle geglaetteten Werte. */
    var currentThresholdDb: Float = -20f
        private set
    var currentRatio: Float = 4.0f
        private set
    var currentKneeDb: Float = 4.0f
        private set
    var currentMakeupDb: Float = 0f
        private set

    private var smoothingFactor: Float

    private var enabled: Boolean = true

    // Huellkurve-Zustand (lineare Amplitude, nicht dB)
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

    fun isEnabled(): Boolean = enabled

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** Target-Parameter setzen (werden stetig angefahren). */
    fun setTargetParameters(
        thresholdDb: Float? = null,
        ratio: Float? = null,
        kneeDb: Float? = null,
        makeupDb: Float? = null
    ) {
        if (thresholdDb != null) {
            targetThresholdDb = if (thresholdDb.isNaN() || thresholdDb.isInfinite()) -60f
                else thresholdDb.coerceIn(-60f, 0f)
        }
        if (ratio != null) {
            targetRatio = if (ratio.isNaN() || ratio.isInfinite() || ratio < 1f) 1f
                else ratio.coerceIn(1f, 100f)
        }
        if (kneeDb != null) {
            targetKneeDb = if (kneeDb.isNaN() || kneeDb.isInfinite() || kneeDb < 0f) 0f
                else kneeDb.coerceIn(0f, 24f)
        }
        if (makeupDb != null) {
            targetMakeupDb = if (makeupDb.isNaN() || makeupDb.isInfinite()) 0f
                else makeupDb.coerceIn(-24f, 24f)
        }
    }

    /** Ein einzelnes Sample verarbeiten. */
    fun processSample(input: Float): Float {
        if (!enabled) return flushDenormal(input)

        // 1. Stetige Anfahrt der Parameter (klickfrei)
        currentThresholdDb += (targetThresholdDb - currentThresholdDb) * smoothingFactor
        currentRatio += (targetRatio - currentRatio) * smoothingFactor
        currentKneeDb += (targetKneeDb - currentKneeDb) * smoothingFactor
        currentMakeupDb += (targetMakeupDb - currentMakeupDb) * smoothingFactor

        // 2. Peak-Detektion (absoluter Betrag)
        val absSample = kotlin.math.abs(input)

        // 3. Huellkurve: Attack wenn lauter, Release wenn leiser
        val attack = attackCoeff
        val release = releaseCoeff
        val targetEnv = absSample
        envelope = if (targetEnv > envelope) {
            envelope + (targetEnv - envelope) * attack
        } else {
            envelope + (targetEnv - envelope) * release
        }

        // 4. Gain-Berechnung im dB-Bereich mit Knee
        val envDb = if (envelope <= 0f) -120f
            else 20f * kotlin.math.log10(envelope.toDouble()).toFloat()

        val gainDb = computeGainDb(envDb, currentThresholdDb, currentRatio, currentKneeDb)

        // 5. Makeup-Gain aufaddieren
        val totalGainDb = gainDb + currentMakeupDb

        // 6. Linearen Gain anwenden
        val gainLinear = if (totalGainDb.isNaN() || totalGainDb.isInfinite()) 1f
            else 10.0.pow(totalGainDb.toDouble() / 20.0).toFloat()

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

    /** Stereo-Paar in-place verarbeiten (gemeinsame Huellkurve beider Kanale). */
    fun processStereo(left: FloatArray, right: FloatArray, count: Int = left.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, left.size).also { c -> coerceInt(0, right.size) }
        for (i in 0 until safeCount) {
            val l = processSample(left[i])
            val r = processSample(right[i])
            left[i] = l
            right[i] = r
        }
    }

    /** Zustand zuruecksetzen. */
    fun reset() {
        envelope = 0f
        currentThresholdDb = targetThresholdDb
        currentRatio = targetRatio
        currentKneeDb = targetKneeDb
        currentMakeupDb = targetMakeupDb
    }

    // -----------------------------------------------------------------------
    // Intern
    // -----------------------------------------------------------------------

    private var attackCoeff: Float = attackCoefficient(44100)
    private var releaseCoeff: Float = releaseCoefficient(44100)

    private fun attackCoefficient(sr: Int): Float {
        // ~5 ms Attack
        return onePoleCoeff(0.005f, sr)
    }

    private fun releaseCoefficient(sr: Int): Float {
        // ~100 ms Release
        return onePoleCoeff(0.100f, sr)
    }

    private fun onePoleCoeff(timeSeconds: Float, sr: Int): Float {
        val s = sr.coerceAtLeast(1)
        return 1.0f - kotlin.math.exp(-1.0f / (s.toFloat() * timeSeconds)).toFloat()
    }

    /** Gain in dB fuer einen gegebenen Pegel (envDb) berechnen. */
    private fun computeGainDb(
        envDb: Float,
        thresholdDb: Float,
        ratio: Float,
        kneeDb: Float
    ): Float {
        if (ratio <= 1f) return 0f

        val knee = if (kneeDb < 0f) 0f else kneeDb
        val lower = thresholdDb - knee / 2f
        val upper = thresholdDb + knee / 2f

        return when {
            envDb <= lower -> 0f // Unter dem Knee: keine Kompression
            envDb >= upper -> {
                // Ueber dem Knee: volle Kompression
                val excess = envDb - thresholdDb
                val compressedExcess = excess / ratio
                compressedExcess - excess // negativer Gain (Demaeufung)
            }
            else -> {
                // Innerhalb des Knee: quadratisches Ein- und Ausblenden
                val x = (envDb - lower) / knee
                // Gain ohne Kompression im Knee-Bereich (linearer Anstieg)
                val gainBefore = ((x * x) / (2f * knee)) * (1f - 1f / ratio)
                // Ziel-Gain bei vollen Kompression
                val excess = envDb - thresholdDb
                val compressedExcess = excess / ratio
                val targetGain = compressedExcess - excess
                // Interpolation zwischen 0 und targetGain
                val t = x * x
                gainBefore + t * (targetGain - gainBefore)
            }
        }
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
