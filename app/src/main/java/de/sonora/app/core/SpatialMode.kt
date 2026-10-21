package de.sonora.app.core

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
}
