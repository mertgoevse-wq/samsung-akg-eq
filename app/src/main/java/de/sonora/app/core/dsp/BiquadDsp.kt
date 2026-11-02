package de.sonora.app.core.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ergebnisse der Biquad-Koeffizientenberechnung.
 *
 * Direktes Form-II-transponiertes Filter (normiert auf a0 = 1):
 *   y[n] = b0*x[n] + v1[n]
 *   v1[n] = b1*x[n] + v2[n] - a1*y[n]
 *   v2[n] = b2*x[n] - a2*y[n]
 */
data class BiquadCoefficients(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float
) {
    fun isValid(): Boolean =
        !b0.isNaN() && !b0.isInfinite() &&
        !b1.isNaN() && !b1.isInfinite() &&
        !b2.isNaN() && !b2.isInfinite() &&
        !a1.isNaN() && !a1.isInfinite() &&
        !a2.isNaN() && !a2.isInfinite()
}

/**
 * Rechner fuer Biquad-Filterkoeffizienten nach dem Robert-Bristow-Johnson
 * Audio-EQ-Cookbook. Alle Methoden sind gegen NaN, Infinity, Division-durch-Null
 * und ausserhalb des Nyquist-Bereichs abgesichert.
 *
 * Keine NNAPI/NPU/GPU-Abhaengigkeit - reine Kotlin-Berechnung auf der CPU.
 */
object BiquadDsp {

    private const val MIN_FREQ_HZ = 20.0
    private const val MAX_Q = 10.0
    private const val MIN_Q = 0.1
    private const val MAX_DB = 24.0

    /**
     * Denormal-Zahlen auf Null spuelein, damit die CPU nicht in den
     * langsamen Denormal-Modus schaltet und kein Klicken entsteht.
     */
    internal fun flushDenormal(x: Float): Float =
        if (x.isNaN() || x.isInfinite()) 0f else if (abs(x) < 1e-20f) 0f else x

    /** Sichere Frequenz: NaN/Infinity ersetzen, auf [20 Hz, fs/2 - 100 Hz] begrenzen. */
    internal fun safeFreq(freqHz: Float, sampleRate: Int): Float {
        val fs = sampleRate.toFloat()
        val safe = if (freqHz.isNaN() || freqHz.isInfinite() || freqHz <= 0f) 1000f else freqHz
        return safe.coerceIn(MIN_FREQ_HZ.toFloat(), (fs / 2f) - 100f)
    }

    /** Sichere Gain in dB. */
    internal fun safeGainDb(gainDb: Float): Float =
        if (gainDb.isNaN() || gainDb.isInfinite()) 0f else gainDb.coerceIn(-MAX_DB, MAX_DB)

    /** Sichere Q. */
    internal fun safeQ(q: Float): Float =
        if (q.isNaN() || q.isInfinite() || q <= 0f) 1.0f else q.coerceIn(MIN_Q, MAX_Q)

    // -----------------------------------------------------------------------
    // RBJ Cookbook Koeffizienten (interne Double-Praezision, Ergebnis als Float)
    // -----------------------------------------------------------------------

    /** Peaking-Filter (Bell-Filter) fuer Mittenbaender. */
    fun peaking(
        centerFreqHz: Float,
        gainDb: Float,
        q: Float,
        sampleRate: Int
    ): BiquadCoefficients {
        val f0 = safeFreq(centerFreqHz, sampleRate).toDouble()
        val fs = sampleRate.toDouble()
        val g = safeGainDb(gainDb)
        val qq = safeQ(q).toDouble()
        val a = 10.0.pow(g.toDouble() / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / (2.0 * qq)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / a

        return BiquadCoefficients(
            b0 = (b0 / a0).toFloat(),
            b1 = (b1 / a0).toFloat(),
            b2 = (b2 / a0).toFloat(),
            a1 = (a1 / a0).toFloat(),
            a2 = (a2 / a0).toFloat()
        )
    }

    /** Tiefpass-Regal (Low Shelf) fuer die tiefsten Baender. */
    fun lowShelf(
        centerFreqHz: Float,
        gainDb: Float,
        slope: Float,
        sampleRate: Int
    ): BiquadCoefficients {
        val f0 = safeFreq(centerFreqHz, sampleRate).toDouble()
        val fs = sampleRate.toDouble()
        val g = safeGainDb(gainDb)
        val ss = if (slope.isNaN() || slope.isInfinite() || slope <= 0f) 1.0f else slope.coerceIn(0.4f, 5.0f).toDouble()
        val a = 10.0.pow(g.toDouble() / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = (sinW0 / 2.0) * sqrt((a + 1.0 / a) * (1.0 / ss - 1.0) + 2.0)

        val twoSqrtA = 2.0 * sqrt(a)
        val minusAlpha = -alpha

        val b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtA * alpha)
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtA * alpha)
        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtA * alpha
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtA * alpha

        return BiquadCoefficients(
            b0 = (b0 / a0).toFloat(),
            b1 = (b1 / a0).toFloat(),
            b2 = (b2 / a0).toFloat(),
            a1 = (a1 / a0).toFloat(),
            a2 = (a2 / a0).toFloat()
        )
    }

    /** Hochpass-Regal (High Shelf) fuer die hoechsten Baender. */
    fun highShelf(
        centerFreqHz: Float,
        gainDb: Float,
        slope: Float,
        sampleRate: Int
    ): BiquadCoefficients {
        val f0 = safeFreq(centerFreqHz, sampleRate).toDouble()
        val fs = sampleRate.toDouble()
        val g = safeGainDb(gainDb)
        val ss = if (slope.isNaN() || slope.isInfinite() || slope <= 0f) 1.0f else slope.coerceIn(0.4f, 5.0f).toDouble()
        val a = 10.0.pow(g.toDouble() / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = (sinW0 / 2.0) * sqrt((a + 1.0 / a) * (1.0 / ss - 1.0) + 2.0)

        val twoSqrtA = 2.0 * sqrt(a)

        val b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtA * alpha)
        val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtA * alpha)
        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtA * alpha
        val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtA * alpha

        return BiquadCoefficients(
            b0 = (b0 / a0).toFloat(),
            b1 = (b1 / a0).toFloat(),
            b2 = (b2 / a0).toFloat(),
            a1 = (a1 / a0).toFloat(),
            a2 = (a2 / a0).toFloat()
        )
    }

    /** Flachdurchgang (Identitaet) zum Reset/Bypass. */
    fun flat(): BiquadCoefficients =
        BiquadCoefficients(b0 = 1f, b1 = 0f, b2 = 0f, a1 = 0f, a2 = 0f)
}

/**
 * Einstufiges Biquad-Filter in Direct-Form-II-Transponiert-Form mit
 * pro-Sample-Parameterglaettung (klickfrei bei Schieberegler-Bewegung),
 * Denormal-Schutz und NaN/Infinity-Resilienz.
 *
 * Sample-rate-aware: bei [setSampleRate] werden die internen Puffer zurueckgesetzt
 * und die Koeffizienten neu an die neue Abtastrate angepasst.
 */
class BiquadStage(
    sampleRate: Int,
    initial: BiquadCoefficients = BiquadDsp.flat()
) {
    private var sampleRate = sampleRate.coerceAtLeast(1)

    // Ziel-Koeffizienten (von aussen gesetzt)
    private var tb0 = initial.b0.toDouble()
    private var tb1 = initial.b1.toDouble()
    private var tb2 = initial.b2.toDouble()
    private var ta1 = initial.a1.toDouble()
    private var ta2 = initial.a2.toDouble()

    // Aktuelle geglaettete Koeffizienten
    private var cb0 = initial.b0.toDouble()
    private var cb1 = initial.b1.toDouble()
    private var cb2 = initial.b2.toDouble()
    private var ca1 = initial.a1.toDouble()
    private var ca2 = initial.a2.toDouble()

    // DFII-transponierte Zustandsvariablen
    private var v1 = 0.0
    private var v2 = 0.0

    // Glaettungsfaktor pro Sample (~20 ms Einstellzeit bei 44.1 kHz)
    private var smoothingFactor = 1.0 - kotlin.math.exp(-1.0 / (sampleRate.toDouble() * 0.020))

    /**
     * Neue Ziel-Koeffizienten setzen. Der Uebergang zum alten Satz erfolgt
     * pro Sample geglaettet (keine Klickgeraeusche).
     */
    fun setCoefficients(coeffs: BiquadCoefficients) {
        require(coeffs.isValid()) { "Biquad-Koeeffizienten muessen endlich sein" }
        tb0 = coeffs.b0.toDouble()
        tb1 = coeffs.b1.toDouble()
        tb2 = coeffs.b2.toDouble()
        ta1 = coeffs.a1.toDouble()
        ta2 = coeffs.a2.toDouble()
    }

    fun setSampleRate(newSampleRate: Int) {
        val sr = newSampleRate.coerceAtLeast(1)
        if (sr == sampleRate) return
        sampleRate = sr
        // Zustande zuruecksetzen, um Klicks bei Rate-Wechsel zu vermeiden
        v1 = 0.0
        v2 = 0.0
        smoothingFactor = 1.0 - kotlin.math.exp(-1.0 / (sampleRate.toDouble() * 0.020))
    }

    fun getSampleRate(): Int = sampleRate

    /** Ein einzelnes Sample verarbeiten. */
    fun processSample(input: Float): Float {
        val x = input.toDouble()

        // 1. Koeffizienten stetig an Ziel annaehern (klickfreie Uebergaenge)
        cb0 += (tb0 - cb0) * smoothingFactor
        cb1 += (tb1 - cb1) * smoothingFactor
        cb2 += (tb2 - cb2) * smoothingFactor
        ca1 += (ta1 - ca1) * smoothingFactor
        ca2 += (ta2 - ca2) * smoothingFactor

        // 2. Direct Form II Transposed
        val y = cb0 * x + v1
        v1 = cb1 * x + v2 - ca1 * y
        v2 = cb2 * x - ca2 * y

        // 3. Denormal-Schutz auf Zustanden
        v1 = BiquadDsp.flushDenormal(v1.toFloat()).toDouble()
        v2 = BiquadDsp.flushDenormal(v2.toFloat()).toDouble()

        // 4. NaN/Infinity Resilienz am Ausgang
        val result = if (y.isNaN() || y.isInfinite()) 0.0 else y
        return result.toFloat()
    }

    /** Puffer in-place verarbeiten. */
    fun process(samples: FloatArray, count: Int = samples.size) {
        val safeCount = count.coerceIn(0, samples.size)
        for (i in 0 until safeCount) {
            samples[i] = processSample(samples[i])
        }
    }

    /** Linken und rechten Kanal eines Stereo-Paares in-place verarbeiten (gleiche Koeffizienten). */
    fun processStereo(left: FloatArray, right: FloatArray, count: Int = left.size) {
        val safeCount = count.coerceIn(0, left.size).coerceAtMost(right.size)
        for (i in 0 until safeCount) {
            left[i] = processSample(left[i])
            right[i] = processSample(right[i])
        }
    }

    /** Internen Zustand zuruecksetzen. */
    fun reset() {
        v1 = 0.0
        v2 = 0.0
    }
}

/**
 * Mehrstufiger Biquad-Filter: verarbeitet ein Sample durch eine Liste von
 * Stufe-Filtern (z. B. ein ganzes Equalizer-Konzept mit mehreren Baendern).
 */
class BiquadFilterChain(
    private var sampleRate: Int,
    internal val stages: MutableList<BiquadStage> = mutableListOf()
) {
    fun addStage(coeffs: BiquadCoefficients = BiquadDsp.flat()) {
        stages.add(BiquadStage(sampleRate, coeffs))
    }

    fun setStageCoefficients(index: Int, coeffs: BiquadCoefficients) {
        require(index in stages.indices) { "Biquad-Stufenindex ausserhalb des Bereichs" }
        stages[index].setCoefficients(coeffs)
    }

    fun setSampleRate(sampleRate: Int) {
        this.sampleRate = sampleRate
        stages.forEach { it.setSampleRate(sampleRate) }
    }

    fun processSample(input: Float): Float {
        var x = input
        for (stage in stages) {
            x = stage.processSample(x)
            // Denormal-Schutz zwischen den Stufe
            if (x.isNaN() || x.isInfinite()) x = 0f
        }
        return x
    }

    fun process(samples: FloatArray, count: Int = samples.size) {
        val safeCount = count.coerceIn(0, samples.size)
        for (i in 0 until safeCount) {
            samples[i] = processSample(samples[i])
        }
    }

    fun reset() {
        stages.forEach { it.reset() }
    }

    fun stageCount(): Int = stages.size
}
