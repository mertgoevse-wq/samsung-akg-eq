package de.sonora.app.audio

import android.media.audiofx.Equalizer
import kotlin.math.abs

sealed interface EngineStatus {
    data object Inactive : EngineStatus
    data class Active(val bands: Int) : EngineStatus
    data class Unavailable(val reason: String) : EngineStatus
}

/**
 * Best-effort system-wide equalizer. Android exposes the global output mix as
 * audio session 0; whether an OEM actually lets an app control it is decided by
 * the device. Every failure is surfaced instead of hidden.
 */
object SonoraEngine {

    val uiFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    private var equalizer: Equalizer? = null

    var status: EngineStatus = EngineStatus.Inactive
        private set

    var bandLevelRange: IntRange = -1500..1500
        private set

    var deviceBandCentersHz: List<Int> = emptyList()
        private set

    @Synchronized
    fun initialize(): EngineStatus {
        release()
        return try {
            val eq = Equalizer(0, 0)
            if (!eq.hasControl()) {
                eq.release()
                status = EngineStatus.Unavailable("Das Gerät erlaubt keine Steuerung des Systemausgangs.")
                return status
            }
            equalizer = eq
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            bandLevelRange = range[0].toInt()..range[1].toInt()
            deviceBandCentersHz = (0 until bands).map { band ->
                val freqRange = eq.getBandFreqRange(band.toShort())
                ((freqRange[0].toLong() + freqRange[1].toLong()) / 2L / 1000L).toInt()
            }
            status = EngineStatus.Active(bands)
            status
        } catch (t: Throwable) {
            status = EngineStatus.Unavailable(t.message ?: t.javaClass.simpleName)
            status
        }
    }

    @Synchronized
    fun applyLevels(levelsDb: List<Float>) {
        val eq = equalizer ?: return
        if (deviceBandCentersHz.isEmpty()) return
        val targets = HashMap<Int, Float>()
        uiFrequencies.forEachIndexed { index, frequency ->
            val nearest = deviceBandCentersHz.indices.minByOrNull { band ->
                abs(deviceBandCentersHz[band] - frequency)
            } ?: return@forEachIndexed
            targets[nearest] = levelsDb.getOrElse(index) { 0f }
        }
        targets.forEach { (band, db) ->
            val milliBel = (db * 100f).toInt().coerceIn(bandLevelRange.first, bandLevelRange.last)
            try {
                eq.setBandLevel(band.toShort(), milliBel.toShort())
            } catch (_: Throwable) {
                // A band the OEM refuses is skipped; the rest still applies.
            }
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun release() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        status = EngineStatus.Inactive
        deviceBandCentersHz = emptyList()
    }
}
