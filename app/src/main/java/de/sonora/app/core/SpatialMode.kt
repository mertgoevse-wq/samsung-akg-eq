package de.sonora.app.core

import androidx.compose.runtime.Immutable

/**
 * Die neun Klangraeume von Sonora.
 *
 * Die Namen sind eigene Bezeichnungen. Dolby, DTS und Auro sind geschuetzte
 * Marken; Sonora dekodiert diese Formate nicht und benennt sie nicht.
 */
enum class SpatialMode(
    val id: String,
    val title: String,
    val description: String
) {
    KINO_WEIT("kino", "Kino-Weit", "Breite Klangwand mit Anteilen von hinten."),
    OBJEKT_RAUM("objekt", "Objekt-Raum", "Höhen und einzelne Objekte stehen im Raum."),
    RAUM_21_1("raum21", "Raum 21.1", "Viele virtuelle Lautsprecher, weit und tief."),
    KOPF_3D("kopf3d", "Kopf-3D", "Der Klang umschließt deinen Kopf."),
    LIVE_SAAL("saal", "Live-Saal", "Natürlicher Raumhall wie in einem Saal."),
    STUDIO_TROCKEN("studio", "Studio-Trocken", "Neutral und direkt, ohne Hall."),
    SPRACH_FOKUS("sprache", "Sprach-Fokus", "Dialog steht klar vorn."),
    NACHT("nacht", "Nacht", "Leise, aber verständlich."),
    BREIT_WARM("breit", "Breit & Warm", "Entspannt, weich, etwas wärmer.")
}

enum class DspStageState {
    ACTIVE,
    PARTIALLY_ACTIVE,
    UNAVAILABLE,
    PENDING
}

@Immutable
data class DspStageReport(
    val stageName: String,
    val state: DspStageState,
    val detail: String
)

@Immutable
data class ModeDspEvaluation(
    val mode: SpatialMode,
    val overallState: DspStageState,
    val stages: List<DspStageReport>,
    val summaryMessage: String
)

/**
 * Die Mischwerte eines Klangraums, alle zwischen 0 und 1.
 *
 * @param crossfeedMix Anteil des kopfnahen Klangs
 * @param wideningMix Anteil der Breite
 * @param reverbMix Anteil des Halls
 * @param centerFocus Anteil der Mitte
 * @param compression Anteil der Dynamikkontrolle
 */
data class SpatialParams(
    val crossfeedMix: Float,
    val wideningMix: Float,
    val reverbMix: Float,
    val centerFocus: Float,
    val compression: Float
)

object SpatialModeCatalog {

    val all: List<SpatialMode> = SpatialMode.entries.toList()

    private val full: Map<SpatialMode, SpatialParams> = mapOf(
        SpatialMode.KINO_WEIT to SpatialParams(0.10f, 0.75f, 0.35f, 0.10f, 0.10f),
        SpatialMode.OBJEKT_RAUM to SpatialParams(0.05f, 0.60f, 0.30f, 0.15f, 0.05f),
        SpatialMode.RAUM_21_1 to SpatialParams(0.15f, 0.90f, 0.55f, 0.05f, 0.10f),
        SpatialMode.KOPF_3D to SpatialParams(0.35f, 0.50f, 0.10f, 0.20f, 0.05f),
        SpatialMode.LIVE_SAAL to SpatialParams(0.10f, 0.35f, 0.80f, 0.05f, 0.15f),
        SpatialMode.STUDIO_TROCKEN to SpatialParams(0.00f, 0.05f, 0.00f, 0.00f, 0.00f),
        SpatialMode.SPRACH_FOKUS to SpatialParams(0.05f, 0.10f, 0.05f, 0.70f, 0.35f),
        SpatialMode.NACHT to SpatialParams(0.05f, 0.10f, 0.10f, 0.30f, 0.60f),
        SpatialMode.BREIT_WARM to SpatialParams(0.20f, 0.40f, 0.15f, 0.10f, 0.20f)
    )

    /**
     * Mischt den Modus mit seiner Intensitaet.
     *
     * Bei 0 kommt der Originalklang zurueck, bei 1 der volle Modus.
     */
    fun params(mode: SpatialMode, intensity: Float): SpatialParams {
        val t = intensity.coerceIn(0f, 1f)
        val f = full[mode] ?: SpatialParams(0f, 0f, 0f, 0f, 0f)
        return SpatialParams(
            f.crossfeedMix * t,
            f.wideningMix * t,
            f.reverbMix * t,
            f.centerFocus * t,
            f.compression * t
        )
    }

    /**
     * Erstellt einen transparenten DSP-Prüfbericht für jeden Modus.
     * Keine Behauptung von HRTF oder nicht-implementierten Effekten.
     */
    fun evaluateDspStages(
        mode: SpatialMode,
        virtualizerSupported: Boolean,
        reverbSupported: Boolean,
        intensity: Float = 0.7f
    ): ModeDspEvaluation {
        val p = params(mode, intensity)
        val reports = mutableListOf<DspStageReport>()

        // 1. Stereo-Widening
        if (p.wideningMix > 0f) {
            reports.add(
                DspStageReport(
                    stageName = "Stereo-Widening",
                    state = if (virtualizerSupported) DspStageState.ACTIVE else DspStageState.UNAVAILABLE,
                    detail = if (virtualizerSupported) "Hardware-Virtualizer aktiv (${(p.wideningMix * 100).toInt()} %)"
                    else "Gerät unterstützt Virtualizer auf Systemausgang nicht"
                )
            )
        }

        // 2. Crossfeed
        if (p.crossfeedMix > 0f) {
            reports.add(
                DspStageReport(
                    stageName = "Crossfeed (Bauer-Filter)",
                    state = DspStageState.ACTIVE,
                    detail = "L/R-Übersprechen mit 280 µs Verzögerung aktiv (${(p.crossfeedMix * 100).toInt()} %)"
                )
            )
        }

        // 3. Hall & frühe Reflexionen
        if (p.reverbMix > 0f) {
            reports.add(
                DspStageReport(
                    stageName = "Raumhall & frühe Reflexionen",
                    state = if (reverbSupported) DspStageState.ACTIVE else DspStageState.UNAVAILABLE,
                    detail = if (reverbSupported) "EnvironmentalReverb aktiv (${(p.reverbMix * 100).toInt()} %)"
                    else "Gerät unterstützt EnvironmentalReverb auf Systemausgang nicht"
                )
            )
        }

        // 4. Sprachverständlichkeit / Mittenfokus
        if (p.centerFocus > 0f) {
            reports.add(
                DspStageReport(
                    stageName = "Sprachfokus (Präsenzband)",
                    state = DspStageState.ACTIVE,
                    detail = "Mittenpräsenz um 1.5–3 kHz angehoben (${(p.centerFocus * 100).toInt()} %)"
                )
            )
        }

        // 5. Nachtmodus / Dynamikkontrolle
        if (p.compression > 0f) {
            reports.add(
                DspStageReport(
                    stageName = "Dynamikkontrolle / Limiter",
                    state = DspStageState.ACTIVE,
                    detail = "Night-Mode DRC Kompression aktiv (${(p.compression * 100).toInt()} %)"
                )
            )
        }

        // 6. HRTF (Ehrliche Nicht-Verfügbarkeit)
        reports.add(
            DspStageReport(
                stageName = "Individuelles HRTF-Kopfmodell",
                state = DspStageState.UNAVAILABLE,
                detail = "Nicht verfügbar: Kein gerätespezifisches SOFA-Kopfmodell hinterlegt"
            )
        )

        val hasUnavailable = reports.any { it.state == DspStageState.UNAVAILABLE && it.stageName != "Individuelles HRTF-Kopfmodell" }
        val overall = if (hasUnavailable) DspStageState.PARTIALLY_ACTIVE else DspStageState.ACTIVE
        val summary = if (hasUnavailable) {
            "Teilweise aktiv: Einige geräteabhängige Effekte werden von One UI auf diesem Ausgang nicht freigegeben."
        } else {
            "Alle verfügbaren DSP-Stufen für ${mode.title} aktiv."
        }

        return ModeDspEvaluation(
            mode = mode,
            overallState = overall,
            stages = reports,
            summaryMessage = summary
        )
    }
}
