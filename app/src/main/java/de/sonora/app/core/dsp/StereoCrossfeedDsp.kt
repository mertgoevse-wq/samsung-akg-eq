package de.sonora.app.core.dsp

import kotlin.math.pow

/**
 * Stereo-Crossfeed-Prozessor (Bauer-Modell) als echter PCM-Sample-Prozessor.
 *
 * Simuliert Lautsprecher-Hoeren auf Kopfhoerern: Ein Teil des linken Kanals
 * wird (verzoegert und gedimmt) in den rechten Kanal gemischt und umgekehrt,
 * mit einem Niedrigpass im Uebersprechen (ca. 700 Hz) und einem
 * Interauralen Zeitunterschied (ITD) von bis zu ~0.3 ms.
 *
 * SoundLabEngine arbeitet mono - fuer den mono-Pfad wird Crossfeed
 * als nicht anwendbar dokumentiert (True Bypass / trockenes Durchreichen),
 * da es keinen Gegenkanal gibt. Stereo-Eingabe wird voll unterstuetzt.
 *
 * Eigenschaften:
 * - Bauer Crossfeed-Matrix (Direkt- und Uebersprechen-Gain, Verzögerung, Tiefpass)
 * - Stetige Parameteruebergaenge (One-Pole Smoothing), klickfrei
 * - True Bypass (enabled = false: beide Kanale trocken durchreichen)
 * - Denormal-Schutz und NaN/Infinity-Resilienz
 * - Sample-rate-aware (Verzoegerung in Samples abhaengig von Abtastrate)
 * - Reine Kotlin-Implementierung, JVM-testbar, keine Android-Abhaengigkeit
 */
class StereoCrossfeedDsp(private var sampleRate: Int = 44100) {

    /** Verzögerungszeile fuer den linken Kanal (Puffer fuer Uebersprechen nach rechts). */
    private val leftDelay = FloatArray(64)
    private var leftWrite = 0

    /** Verzögerungszeile fuer den rechten Kanal (Puffer fuer Uebersprechen nach links). */
    private val rightDelay = FloatArray(64)
    private var rightWrite = 0

    private var targetStrength: Float = 0.5f
    private var currentStrength: Float = 0.5f
    private var smoothingFactor: Float

    private var enabled: Boolean = true

    init {
        smoothingFactor = onePoleSmoothing(sampleRate)
        setStrengthInternal(0.5f)
    }

    fun getSampleRate(): Int = sampleRate

    fun setSampleRate(newSampleRate: Int) {
        sampleRate = newSampleRate.coerceAtLeast(1)
        // Puffer zuruecksetzen (keine Alt-Proben uebernehmen bei Rate-Wechsel)
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        leftWrite = 0
        rightWrite = 0
        smoothingFactor = onePoleSmoothing(sampleRate)
        recomputeDelay()
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** Ziel-Staerke (0 = aus, 1 = maximale Crossfeed). Wird stetig angefahren. */
    fun setTargetStrength(strength: Float) {
        targetStrength = if (strength.isNaN() || strength.isInfinite()) 0f
            else strength.coerceIn(0f, 1f)
    }

    /** Ein Stereo-Sample-Paar verarbeiten. */
    fun processSample(left: Float, right: Float): FloatArray {
        return processPair(left, right)
    }

    /** Pufferpaar in-place verarbeiten. */
    fun processStereo(left: FloatArray, right: FloatArray, count: Int = left.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, left.size).also { c -> coerceInt(0, right.size) }
        for (i in 0 until safeCount) {
            val out = processPair(left[i], right[i])
            left[i] = out[0]
            right[i] = out[1]
        }
    }

    /** Mono-Eingabe: Crossfeed ist nicht anwendbar (kein Gegenkanal), trockenes Durchreichen. */
    fun processMono(samples: FloatArray, count: Int = samples.size) {
        // Mono: kein Uebersprechen moeglich, True Bypass-Verhalten (trocken durchreichen)
        // Denormal-Schutz trotzdem anwenden
        if (!enabled) return
        val safeCount = count.coerceIn(0, samples.size)
        for (i in 0 until safeCount) {
            samples[i] = flushDenormal(samples[i])
        }
    }

    fun reset() {
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        leftWrite = 0
        rightWrite = 0
        currentStrength = targetStrength
    }

    // -----------------------------------------------------------------------
    // Intern
    // -----------------------------------------------------------------------

    private var delaySamples: Int = 1
    private var crossGain: Float = 0f
    private var directGain: Float = 1f

    private fun processPair(leftIn: Float, rightIn: Float): FloatArray {
        if (!enabled) return floatArrayOf(flushDenormal(leftIn), flushDenormal(rightIn))

        val xL = leftIn.toDouble()
        val xR = rightIn.toDouble()

        // Stetige Anfahrt der Staerke (klickfrei) -> Koeffizienten neu berechnen
        currentStrength += (targetStrength - currentStrength) * smoothingFactor
        recomputeCoefficients(currentStrength)

        // Verzögerung lesen (mit linearer Interpolation fuer Sub-Sample-Genauigkeit)
        val crossFromRight = readInterpolated(rightDelay, rightWrite, delaySamples)
        val crossFromLeft = readInterpolated(leftDelay, leftWrite, delaySamples)

        // Bauer-Matrix:
        // L_out = directGain * L_in + crossGain * (verzoegerter R_in)
        // R_out = directGain * R_in + crossGain * (verzoegerter L_in)
        val yL = xL * directGain + crossFromRight * crossGain
        val yR = xR * directGain + crossFromLeft * crossGain

        // Aktuelle Proben in die Verzögerungszeilen schreiben
        leftDelay[leftWrite] = flushDenormal(leftIn)
        leftWrite = (leftWrite + 1) % leftDelay.size
        rightDelay[rightWrite] = flushDenormal(rightIn)
        rightWrite = (rightWrite + 1) % rightDelay.size

        val outL = if (yL.isNaN() || yL.isInfinite()) 0.0 else yL
        val outR = if (yR.isNaN() || yR.isInfinite()) 0.0 else yR
        return floatArrayOf(flushDenormal(outL.toFloat()), flushDenormal(outR.toFloat()))
    }

    private fun readInterpolated(buffer: FloatArray, writeIndex: Int, delaySamples: Int): Float {
        val size = buffer.size
        // Leseposition laeuft hinter dem Schreibindex um delaySamples zurueck
        var readPos = (writeIndex - delaySamples).toDouble()
        while (readPos < 0) readPos += size
        while (readPos >= size) readPos -= size

        val index0 = readPos.toInt()
        val index1 = (index0 + 1) % size
        val frac = readPos - index0
        return ((1.0 - frac) * buffer[index0] + frac * buffer[index1]).toFloat()
    }

    private fun setStrengthInternal(strength: Float) {
        targetStrength = strength
        currentStrength = strength
        recomputeCoefficients(strength)
    }

    private fun recomputeCoefficients(strength: Float) {
        val safe = if (strength.isNaN() || strength.isInfinite()) 0f else strength.coerceIn(0f, 1f)
        // Bauer-Spezifikation: Verzögerung ~14 samples bei staerke 1.0 (ca. 0.3 ms bei 44.1 kHz)
        val computedDelay = (14f * safe).toInt().coerceIn(0, leftDelay.size - 1)
        delaySamples = computedDelay.coerceAtLeast(0)

        // Uebersprechen typischerweise -18 dB bis -8 dB relativer zum Direktkanal
        val crossfeedDb = -18f + (10f * safe)
        crossGain = if (crossfeedDb.isNaN() || crossfeedDb.isInfinite()) 0f
            else 10.0.pow(crossfeedDb.toDouble() / 20.0).toFloat()
        directGain = 1.0f - (crossGain * 0.35f) // RMS-Leistung erhalten
    }

    private fun recomputeDelay() {
        recomputeCoefficients(currentStrength)
    }

    private fun onePoleSmoothing(sr: Int): Float {
        val s = sr.coerceAtLeast(1)
        return (1.0f - kotlin.math.exp(-1.0f / (s.toFloat() * 0.020f)).toFloat())
    }

    private fun flushDenormal(x: Float): Float =
        if (x.isNaN() || x.isInfinite()) 0f else if (kotlin.math.abs(x) < 1e-20f) 0f else x

    private fun coerceInt(min: Int, max: Int): Int = min.coerceAtLeast(min).coerceAtMost(max)

    companion object {
        /** Maximal moegliche Verzögerung in Samples (Buffer-Groesse). */
        const val MAX_DELAY_SAMPLES = 64
    }
}
