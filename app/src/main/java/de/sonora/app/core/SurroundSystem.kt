package de.sonora.app.core

/**
 * Surround-System-Konfigurationen.
 *
 * Simuliert verschiedene Lautsprecher-Setups auf Kopfhörern.
 */
enum class SurroundSystem(
    val id: String,
    val title: String,
    val channels: Int,
    val description: String
) {
    STEREO_2_0("2.0", "Stereo 2.0", 2, "Klassisches Stereoklangbild."),
    STEREO_2_1("2.1", "Stereo 2.1", 3, "Stereo mit Subwoofer (Bass-Unterstützung)."),
    SURROUND_3_1("3.1", "Surround 3.1", 4, "Links, Rechts, Center + Subwoofer."),
    SURROUND_5_1("5.1", "Surround 5.1", 6, "Standard Surround: 5 Lautsprecher + Subwoofer."),
    SURROUND_7_1("7.1", "Surround 7.1", 8, "Erweitertes Surround mit 7 Lautsprechern."),
    ATMOS_5_1_2("5.1.2", "Dolby Atmos 5.1.2", 8, "5.1 + 2 Height-Kanäle (Atmos)."),
    ATMOS_7_1_4("7.1.4", "Dolby Atmos 7.1.4", 12, "7.1 + 4 Height-Kanäle (Atmos)."),
    ATMOS_21_2("21.2", "Dolby Atmos 21.2", 24, "21 Lautsprecher + 2 Subwoofer (Premium)."),
    BINAURAL("binaural", "Binaural 3D", 2, "Kopfhörer-optimiertes 3D Audio (HRTF).")
}