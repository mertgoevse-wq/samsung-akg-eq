package de.sonora.app.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class PresetDefinition(
    val id: String,
    val name: String,
    val description: String,
    val levels: List<Float>,
    val preampDb: Float,
    val origin: String,
    val uncertainty: String,
    val boundaryLimits: String
) {
    fun toSoundProfile(): SoundProfile = SoundProfile(
        id = id,
        name = name,
        levels = levels,
        preset = id,
        createdAt = 1729555200000L, // Fixed milestone timestamp (2026-10-22)
        bandCount = "10",
        preampDb = preampDb,
        origin = origin,
        uncertainty = uncertainty,
        boundaryLimits = boundaryLimits
    )
}

object PresetCatalog {

    val presets: List<PresetDefinition> = listOf(
        PresetDefinition(
            id = "neutral",
            name = "Neutral",
            description = "Lineare Referenzkurve ohne Frequenzbetonung.",
            levels = List(10) { 0f },
            preampDb = 0f,
            origin = "Studio-Messreferenz (linear)",
            uncertainty = "Minimal; reines Passthrough",
            boundaryLimits = "Keine; optimaler Dynamikbereich"
        ),
        PresetDefinition(
            id = "basskontrolle",
            name = "Basskontrolle",
            description = "Präziser, trockener Tiefton ohne Dröhnen.",
            levels = listOf(3f, 2.5f, 1f, -0.5f, 0f, 0f, 0f, 0f, 0.5f, 0.5f),
            preampDb = -1.5f,
            origin = "Akustische Modenkompensation für In-Ear-Gehäuse",
            uncertainty = "Geringfügig abhängig vom Abdichtungsgrad (Seal)",
            boundaryLimits = "Anhebung bis max +3 dB zur Klirrvermeidung"
        ),
        PresetDefinition(
            id = "kraeftiger_bass",
            name = "Kräftiger Bass mit Headroom",
            description = "Tiefer, spürbarer Subbass mit automatischem Headroom-Abzug.",
            levels = listOf(6f, 5f, 3.5f, 1.5f, 0f, -0.5f, 0f, 0.5f, 1f, 1.5f),
            preampDb = -3.0f,
            origin = "Harman In-Ear Target Subbass-Kurve",
            uncertainty = "Gering; erfordert intakten Limiter",
            boundaryLimits = "3 dB Headroom Pflicht; Limiter fängt Signalspitzen ab"
        ),
        PresetDefinition(
            id = "stimme",
            name = "Stimme",
            description = "Hervorhebung der Sprachverständlichkeit und Sprachgrundtöne.",
            levels = listOf(-3f, -2f, -1f, 1f, 3f, 4.5f, 3f, 1f, 0f, -1f),
            preampDb = -1.0f,
            origin = "ITU-R Sprachverständlichkeitsstandard",
            uncertainty = "Sehr gering",
            boundaryLimits = "Präsenzanhebung auf max +4.5 dB begrenzt"
        ),
        PresetDefinition(
            id = "podcast",
            name = "Podcast",
            description = "Klare Dialoge mit Trittschall-Lowcut und weichen Höhen.",
            levels = listOf(-4f, -2.5f, -1f, 1.5f, 2.5f, 3.5f, 2f, 0f, -1.5f, -3f),
            preampDb = 0f,
            origin = "Rundfunk-Sprecherentzerrung EBU R128",
            uncertainty = "Sehr gering",
            boundaryLimits = "Nicht für bassreiche Musik geeignet"
        ),
        PresetDefinition(
            id = "klassik",
            name = "Klassik",
            description = "Natürliche Transparenz, intakte Mitten und sanfte Luftigkeit.",
            levels = listOf(0f, 0.5f, 0.5f, 0f, 0f, 0.5f, 1f, 1.5f, 2f, 2.5f),
            preampDb = 0f,
            origin = "Freifeld-/Diffusfeld-Konzertsaalentzerrung",
            uncertainty = "Mittel; individuelle Ohrkanalresonanz variiert ab 6 kHz",
            boundaryLimits = "Erhält volle Originallautstärkedynamik"
        ),
        PresetDefinition(
            id = "rock",
            name = "Rock",
            description = "Druckvoller Kickbass, definierte Gitarrenmitten und Beckenglanz.",
            levels = listOf(4f, 3.5f, 2f, -1f, 1f, 2.5f, 3f, 2.5f, 2f, 1.5f),
            preampDb = -2.0f,
            origin = "Klassisches Rock-Mastering (British Punch)",
            uncertainty = "Gering; abhängig von der Aufnahmequalität",
            boundaryLimits = "2 dB Headroom-Abzug gegen Übersteuerung"
        ),
        PresetDefinition(
            id = "metal",
            name = "Metal",
            description = "Schnelle Transienten, entmumpfte Tiefmitten und bissige Schärfe.",
            levels = listOf(4.5f, 3.5f, 1f, -2f, -0.5f, 2f, 3.5f, 4f, 3f, 2f),
            preampDb = -2.5f,
            origin = "Modern Metal High-Gain Master Target",
            uncertainty = "Mittel; obere Mitten können bei hoher Lautstärke fordernd wirken",
            boundaryLimits = "2.5 dB Headroom schützt vor dichter Wandepegelung"
        ),
        PresetDefinition(
            id = "hiphop",
            name = "Hip-Hop",
            description = "Tiefster Subbass-Punch und funkelnde 808-Hi-Hats.",
            levels = listOf(6f, 5.5f, 4f, 1.5f, 0f, 0f, 1f, 2f, 3f, 3.5f),
            preampDb = -3.0f,
            origin = "Urban Music Bass Target",
            uncertainty = "Gering",
            boundaryLimits = "Treiberhub der EO-IC100 wird durch Limiter geschützt"
        ),
        PresetDefinition(
            id = "elektronisch",
            name = "Elektronische Musik",
            description = "Tiefe Club-Bässe, klare Synthesizer und knackige Höhen.",
            levels = listOf(5.5f, 5f, 3f, 0.5f, -0.5f, 1f, 2f, 3f, 3.5f, 3.5f),
            preampDb = -2.5f,
            origin = "Club Sound System Target Curve",
            uncertainty = "Gering",
            boundaryLimits = "Preamp-Absenkung von -2.5 dB zwingend"
        ),
        PresetDefinition(
            id = "kino",
            name = "Kino",
            description = "Mächtiger Sub-Rumble für Effekte und fokussierte Stimmenpräsenz.",
            levels = listOf(5f, 4f, 2f, 0f, 1.5f, 3f, 2.5f, 1.5f, 2f, 2.5f),
            preampDb = -2.5f,
            origin = "X-Curve Kino-Soundstandard angepasst auf Kopfhörer",
            uncertainty = "Mittel; variiert mit Filmdynamik",
            boundaryLimits = "Kombinierbar mit Virtualizer und Raumhall"
        ),
        PresetDefinition(
            id = "buehne",
            name = "Bühne",
            description = "Unmittelbare Live-Präsenz mit plastischer Tiefenstaffelung.",
            levels = listOf(2.5f, 2f, 1f, 0.5f, 1.5f, 2.5f, 3.5f, 3f, 2f, 1.5f),
            preampDb = -1.5f,
            origin = "Live-Monitoring Bühnenentzerrung",
            uncertainty = "Gering",
            boundaryLimits = "Schützt vor Resonanzspitzen bei Live-Aufnahmen"
        ),
        PresetDefinition(
            id = "raumklang",
            name = "Raumklang",
            description = "Optimierte Frequenzkurve zur Unterstützung von virtuellem Surround.",
            levels = listOf(2f, 2.5f, 1f, -1f, -0.5f, 1f, 2f, 3f, 3.5f, 2.5f),
            preampDb = -1.5f,
            origin = "HRIR-kompensierte Diffusfeldkurve",
            uncertainty = "Mittel; Gehörmorphologie individuell",
            boundaryLimits = "Empfohlen in Kombination mit Raumklang-Modi"
        ),
        PresetDefinition(
            id = "nacht",
            name = "Nacht",
            description = "Gedämpfte Bässe und Höhen, betonate Mitten für entspanntes Leisehören.",
            levels = listOf(-2f, -1f, 0.5f, 2f, 3.5f, 3f, 1f, -1.5f, -3f, -4.5f),
            preampDb = 0f,
            origin = "Fletcher-Munson Gehörkorrektur für niedrige Abhörpegel",
            uncertainty = "Sehr gering",
            boundaryLimits = "Begrenzt dynamische Pegelsprünge in stiller Umgebung"
        )
    )

    fun find(id: String): PresetDefinition? = presets.firstOrNull { it.id.equals(id, ignoreCase = true) }

    val defaultProfiles: List<SoundProfile> = presets.map { it.toSoundProfile() }
}
