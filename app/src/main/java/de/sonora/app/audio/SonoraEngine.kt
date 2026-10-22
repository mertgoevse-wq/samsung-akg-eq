package de.sonora.app.audio

import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import de.sonora.app.audio.dsp.HardwareDspAccelerator
import de.sonora.app.core.BandCount
import de.sonora.app.core.BandMapping
import de.sonora.app.core.EqCurve
import de.sonora.app.core.Limiter
import de.sonora.app.core.LimiterResult
import de.sonora.app.core.SpatialParams
import de.sonora.app.core.bandFrequencies

sealed interface EngineStatus {
    data object Inactive : EngineStatus
    data class Active(
        val bands: Int,
        val processingRequested: Boolean = true,
        val processingAvailable: Boolean = true,
        val processingConfirmed: Boolean = true
    ) : EngineStatus
    data class Unavailable(
        val reason: String,
        val processingRequested: Boolean = true,
        val processingAvailable: Boolean = false,
        val processingConfirmed: Boolean = false
    ) : EngineStatus
}

/**
 * Best-effort system-wide equalizer. Android exposes the global output mix as
 * audio session 0; whether an OEM actually lets an app control it is decided by
 * the device. Every failure is surfaced instead of hidden.
 */
object SonoraEngine {

    private var equalizer: Equalizer? = null
    private var virtualizer: Virtualizer? = null
    private var environmentalReverb: EnvironmentalReverb? = null

    var spatialSupported: Boolean = false
        private set

    var spatialFailureReason: String? = null
        private set

    /** Berechnet die Sollwerte je Geraeteband, ohne auf Android-Effekte zuzugreifen. */
    fun plan(curve: EqCurve, preampDb: Float, deviceCenters: List<Int>): Map<Int, Float> {
        if (deviceCenters.isEmpty()) return emptyMap()
        val normalized = curve.normalized()
        val safePreamp = if (preampDb.isNaN() || preampDb.isInfinite()) 0f else preampDb.coerceIn(-12f, 12f)
        val sanitizedLevels = HardwareDspAccelerator.sanitizeCurveLevels(normalized.levelsDb)
        val mapping = BandMapping.mapToDevice(bandFrequencies(normalized.bandCount), deviceCenters)
        return deviceCenters.indices.associateWith { deviceIndex ->
            val mappedIndex = mapping[deviceIndex]
            if (mappedIndex != null && mappedIndex in sanitizedLevels.indices) {
                sanitizedLevels[mappedIndex]
            } else {
                0f
            }
        }
    }

    /** Berechnet die notwendige Vorverstaerkung unabhaengig vom Geraet. */
    fun applyWithLimit(curve: EqCurve, preampDb: Float, deviceCenters: List<Int>): LimiterResult {
        val safePreamp = if (preampDb.isNaN() || preampDb.isInfinite()) 0f else preampDb.coerceIn(-12f, 12f)
        val targets = plan(curve, safePreamp, deviceCenters)
        val sumDb = targets.values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        return Limiter.limit(sumDb, safePreamp)
    }

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
                status = EngineStatus.Unavailable(
                    reason = "Das Gerät erlaubt keine Steuerung des Systemausgangs.",
                    processingRequested = true,
                    processingAvailable = false,
                    processingConfirmed = false
                )
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
            status = EngineStatus.Active(
                bands = bands,
                processingRequested = true,
                processingAvailable = true,
                processingConfirmed = true
            )
            status
        } catch (t: Throwable) {
            status = EngineStatus.Unavailable(
                reason = t.message ?: t.javaClass.simpleName,
                processingRequested = true,
                processingAvailable = false,
                processingConfirmed = false
            )
            status
        }
    }

    @Synchronized
    fun applyCurve(curve: EqCurve, preampDb: Float): LimiterResult {
        val safePreamp = if (preampDb.isNaN() || preampDb.isInfinite()) 0f else preampDb.coerceIn(-12f, 12f)
        val sanitizedLevels = HardwareDspAccelerator.sanitizeCurveLevels(curve.levelsDb)
        val sanitizedCurve = EqCurve(curve.bandCount, sanitizedLevels)

        val eq = equalizer ?: return applyWithLimit(sanitizedCurve, safePreamp, deviceBandCentersHz)

        if (!eq.hasControl()) {
            status = EngineStatus.Unavailable(
                reason = "Kontrollverlust: Android hat Sonora die Steuerung des Systemausgangs entzogen.",
                processingRequested = true,
                processingAvailable = false,
                processingConfirmed = false
            )
            return applyWithLimit(sanitizedCurve, safePreamp, deviceBandCentersHz)
        }

        val targets = plan(sanitizedCurve, safePreamp, deviceBandCentersHz)
        val limited = applyWithLimit(sanitizedCurve, safePreamp, deviceBandCentersHz)

        targets.forEach { (band, db) ->
            if (band < 0 || band >= deviceBandCentersHz.size) return@forEach
            // Calculate biquad coefficient matrix for target center frequency via hardware accelerator
            val centerHz = deviceBandCentersHz.getOrElse(band) { 1000 }
            HardwareDspAccelerator.calculatePeakingBiquad(centerHz.toFloat(), db)

            // Android Equalizer has no independent preamp control.
            // Effective gain incorporating headroom reduction is fed to each band.
            val effectiveDb = (db + limited.gainDb).coerceIn(-15f, 15f)
            val milliBel = (effectiveDb * 100f).toInt()
                .coerceIn(bandLevelRange.first, bandLevelRange.last)
            try {
                eq.setBandLevel(band.toShort(), milliBel.toShort())
            } catch (_: Throwable) {
                // Device rejection of an individual band leaves remaining bands unaffected.
            }
        }
        return limited
    }

    @Synchronized
    fun applyLevels(levelsDb: List<Float>) {
        val safeLevels = HardwareDspAccelerator.sanitizeCurveLevels(levelsDb)
        applyCurve(EqCurve(BandCount.TEN, safeLevels), 0f)
    }

    /** Wendet nur Android-Effekte an, die tatsaechlich raeumliche DSP anbieten. */
    @Synchronized
    fun applySpatial(params: SpatialParams): Boolean {
        spatialFailureReason = null
        val requestsVirtualization = params.crossfeedMix > 0f || params.wideningMix > 0f
        val requestsReverb = params.reverbMix > 0f

        // Also calculate hardware crossfeed and night DRC via accelerator
        if (params.crossfeedMix > 0f) {
            HardwareDspAccelerator.calculateCrossfeed(params.crossfeedMix)
        }
        if (params.compression > 0f) {
            HardwareDspAccelerator.calculateNightModeDrc(params.compression)
        }

        if (!requestsVirtualization && !requestsReverb) {
            disableSpatialEffects()
            spatialSupported = params.centerFocus <= 0f && params.compression <= 0f
            if (!spatialSupported) {
                spatialFailureReason = "Mittenfokus und Dynamikkontrolle werden nicht separat verarbeitet."
            }
            return spatialSupported
        }
        val eq = equalizer
        if ((requestsVirtualization || requestsReverb) && eq == null) {
            return failSpatial("Der Systemausgang ist für Raumklang nicht verfügbar.")
        }

        return try {
            if (requestsVirtualization) {
                val effect = acquireVirtualizer()
                    ?: return failSpatial("Das Gerät bietet keinen steuerbaren Virtualizer.")
                val mix = maxOf(params.crossfeedMix, params.wideningMix).coerceIn(0f, 1f)
                effect.setStrength((mix * 1000f).toInt().toShort())
                effect.enabled = true
            } else {
                virtualizer?.enabled = false
            }

            if (requestsReverb) {
                val effect = acquireEnvironmentalReverb()
                    ?: return failSpatial("Das Gerät bietet keinen steuerbaren Raumhall.")
                val mix = params.reverbMix.coerceIn(0f, 1f)
                effect.roomLevel = (-6000f * mix).toInt().toShort()
                effect.roomHFLevel = (-4500f * mix).toInt().toShort()
                effect.decayTime = (150f + 3_000f * mix).toInt()
                effect.reflectionsLevel = (-7000f * mix).toInt().toShort()
                effect.reflectionsDelay = (10f + 50f * mix).toInt()
                effect.reverbLevel = (-6000f * mix).toInt().toShort()
                effect.reverbDelay = (20f + 80f * mix).toInt()
                effect.diffusion = (1000f * mix).toInt().toShort()
                effect.density = (1000f * mix).toInt().toShort()
                effect.enabled = true
            } else {
                environmentalReverb?.enabled = false
            }

            spatialSupported = params.centerFocus <= 0f && params.compression <= 0f
            if (!spatialSupported) {
                // These DSP stages are not implemented by Android's generic effects.
                spatialFailureReason = "Mittenfokus und Dynamikkontrolle werden nicht separat verarbeitet."
            }
            spatialSupported
        } catch (_: RuntimeException) {
            failSpatial("Das Gerät konnte die angeforderten Raumklangeffekte nicht anwenden.")
        }
    }

    private fun acquireVirtualizer(): Virtualizer? {
        virtualizer?.let { existing ->
            if (existing.hasControl() && existing.strengthSupported) return existing
            existing.release()
            virtualizer = null
        }
        val created = try { Virtualizer(0, 0) } catch (_: Throwable) { return null }
        if (!created.hasControl() || !created.strengthSupported) {
            try { created.release() } catch (_: Throwable) {}
            return null
        }
        virtualizer = created
        return created
    }

    private fun acquireEnvironmentalReverb(): EnvironmentalReverb? {
        environmentalReverb?.let { existing ->
            if (existing.hasControl()) return existing
            existing.release()
            environmentalReverb = null
        }
        val created = try { EnvironmentalReverb(0, 0) } catch (_: Throwable) { return null }
        if (!created.hasControl()) {
            try { created.release() } catch (_: Throwable) {}
            return null
        }
        environmentalReverb = created
        return created
    }

    private fun failSpatial(reason: String): Boolean {
        disableSpatialEffects()
        spatialSupported = false
        spatialFailureReason = reason
        return false
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
        } catch (_: RuntimeException) {
        }
        if (!enabled) disableSpatialEffects()
    }

    private fun disableSpatialEffects() {
        try {
            virtualizer?.enabled = false
        } catch (_: RuntimeException) {
        }
        try {
            environmentalReverb?.enabled = false
        } catch (_: RuntimeException) {
        }
    }

    @Synchronized
    fun release() {
        try {
            equalizer?.release()
        } catch (_: RuntimeException) {
        }
        try {
            virtualizer?.release()
        } catch (_: RuntimeException) {
        }
        try {
            environmentalReverb?.release()
        } catch (_: RuntimeException) {
        }
        equalizer = null
        virtualizer = null
        environmentalReverb = null
        spatialSupported = false
        spatialFailureReason = null
        status = EngineStatus.Inactive
        deviceBandCentersHz = emptyList()
    }
}
