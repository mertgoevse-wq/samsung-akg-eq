package de.sonora.app.core

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Zahl der Equalizer-Baender, die die Oberflaeche anbietet.
 * Das Geraet kann weniger Bands bereitstellen; die Abbildung passt das an.
 */
enum class BandCount(val size: Int) {
    TEN(10),
    THIRTY_ONE(31),
    SIXTY_FOUR(64)
}

object EqModel {
    const val MIN_DB = -12f
    const val MAX_DB = 12f
}

/**
 * Eine Equalizer-Kurve in dB, bezogen auf ein Bandraster.
 *
 * Wird die Bandzahl gewechselt, interpoliert [withBandCount] die vorhandene
 * Kurve auf das neue Raster, statt Werte zu verlieren oder sprunghaft
 * zu wechseln.
 */
data class EqCurve(
    val bandCount: BandCount = BandCount.TEN,
    val levelsDb: List<Float> = List(BandCount.TEN.size) { 0f }
) {
    fun normalized(): EqCurve = EqCurve(
        bandCount,
        List(bandCount.size) {
            levelsDb.getOrElse(it) { 0f }.coerceIn(EqModel.MIN_DB, EqModel.MAX_DB)
        }
    )

    fun withBandCount(target: BandCount): EqCurve {
        if (target == bandCount) return normalized()
        val from = bandFrequencies(bandCount)
        val to = bandFrequencies(target)
        val sampled = to.map { f -> sampleAt(from, levelsDb, f) }
        return EqCurve(target, sampled).normalized()
    }

    private fun sampleAt(xs: List<Int>, ys: List<Float>, x: Int): Float {
        if (xs.isEmpty() || ys.isEmpty()) return 0f
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        for (i in 0 until xs.size - 1) {
            val lo = xs[i]
            val hi = xs[i + 1]
            if (x in lo..hi) {
                val t = if (hi == lo) 0f else (x - lo).toFloat() / (hi - lo)
                return ys.getOrElse(i) { 0f } * (1 - t) + ys.getOrElse(i + 1) { 0f } * t
            }
        }
        return ys.last()
    }
}

/**
 * Frequenzraster in Hz.
 *
 * - 10 Baender: die uebliche Geraeteaufteilung 31 Hz bis 16 kHz
 * - 31 Baender: 1/3-Oktave von 20 Hz bis 20 kHz
 * - 64 Baender: 1/6-Oktave von 20 Hz bis 20 kHz
 */
fun bandFrequencies(count: BandCount): List<Int> = when (count) {
    BandCount.TEN -> listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    BandCount.THIRTY_ONE -> List(31) { i ->
        (20.0 * 2.0.pow(i / 3.0)).roundToInt().coerceAtLeast(1)
    }
    BandCount.SIXTY_FOUR -> List(64) { i ->
        // Zehn Oktaven auf 63 Schritte verteilen: etwa 1/6-Oktave je Band.
        val f = 20.0 * 2.0.pow(i * (log2(20000.0 / 20.0) / 63.0))
        f.roundToInt().coerceAtLeast(1)
    }
}
