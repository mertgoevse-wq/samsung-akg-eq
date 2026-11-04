package de.sonora.app.core.dsp

import kotlin.math.pow

/**
 * Preamp-Vorverstaerkung mit Headroom-Management als echte PCM-Sample-Prozessoren.
 *
 * Eigenschaften:
 * - Lineare Gain-Umrechnung aus dB (amplitude = 10^(dB/20))
 * - Stetige Parameteruebergaenge (One-Pole Smoothing), klickfrei bei Schiebebewegung
 * - Headroom-Management: Headroom in dB wird beruecksichtigt, damit das Signal
 *   nicht uebersteuert (Clipping), wenn Preamp und EQ zusammen genutzt werden
 * - True Bypass (enabled = false: 0 dB, trockenes Durchreichen)
 * - Denormal-Schutz und NaN/Infinity-Resilienz
 * - Reine Kotlin-Implementierung, JVM-testbar, keine Android-Abhaengigkeit
 */
class PreampDsp(private var sampleRate: Int = 44100) {

    /** Ziel-Vorverstaerkung in dB (extern gesetzt). */
    var targetGainDb: Float = 0f
        private set

    /** Aktuelle geglaettete Vorverstaerkung in dB. */
    var currentGainDb: Float = 0f
        private set

    /** Nutbarer Headroom in dB (wie viel dB ueber 0 dBFS das Signal noch darf). */
    var headroomDb: Float = 3f
        private set

    /** Ob der Preamp aktiv ist (True Bypass). */
    var enabled: Boolean = true
        private set

    private var smoothingFactor: Float

    init {
        smoothingFactor = onePoleSmoothing(sampleRate)
    }

    fun getSampleRate(): Int = sampleRate

    fun setSampleRate(newSampleRate: Int) {
        sampleRate = newSampleRate.coerceAtLeast(1)
        smoothingFactor = onePoleSmoothing(sampleRate)
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** Zielvorverstaerkung setzen (in dB). Wird stetig angefahren. */
    fun setTargetGainDb(db: Float) {
        targetGainDb = if (db.isNaN() || db.isInfinite()) 0f else db.coerceIn(-24f, 24f)
    }

    /** Headroom setzen (in dB, positiv). Begrenzt die effektive Gain. */
    fun setHeadroomDb(db: Float) {
        headroomDb = if (db.isNaN() || db.isInfinite() || db < 0f) 0f else db.coerceIn(0f, 24f)
    }

    /** Ein einzelnes Sample verarbeiten. */
    fun processSample(input: Float): Float {
        if (!enabled) return flushDenormal(input)

        // Stetige Anfahrt an den Zielwert (klickfrei)
        currentGainDb += (targetGainDb - currentGainDb) * smoothingFactor

        // Headroom begrenzen: effektive Gain darf nicht ueber Headroom hinausgehen,
        // damit bei nachfolgendem EQ/Compressor kein Clipping entsteht.
        val safeGainDb = kotlin.math.min(currentGainDb, headroomDb)

        val gainLinear = if (safeGainDb.isNaN() || safeGainDb.isInfinite()) 1f
            else 10.0.pow(safeGainDb.toDouble() / 20.0).toFloat()

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

    /** Stereo-Paar in-place verarbeiten. */
    fun processStereo(left: FloatArray, right: FloatArray, count: Int = left.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, left.size).coerceAtMost(right.size)
        for (i in 0 until safeCount) {
            left[i] = processSample(left[i])
            right[i] = processSample(right[i])
        }
    }

    /** Zustand zuruecksetzen (Glätung auf Zielwert springen lassen). */
    fun reset() {
        currentGainDb = targetGainDb
    }

    // -----------------------------------------------------------------------
    // Intern
    // -----------------------------------------------------------------------

    private fun onePoleSmoothing(sr: Int): Float {
        val s = sr.coerceAtLeast(1)
        return (1.0f - kotlin.math.exp(-1.0f / (s.toFloat() * 0.020f)).toFloat())
    }

    private fun flushDenormal(x: Float): Float =
        if (x.isNaN() || x.isInfinite()) 0f else if (kotlin.math.abs(x) < 1e-20f) 0f else x

    private fun coerceInt(min: Int, max: Int): Int = min.coerceAtLeast(min).coerceAtMost(max)

    companion object {
        /** Umrechnung von dB in linearen Gain-Faktor. */
        fun dbToLinear(db: Float): Float =
            if (db.isNaN() || db.isInfinite()) 1f
            else 10.0.pow(db.toDouble() / 20.0).toFloat()
    }
}
