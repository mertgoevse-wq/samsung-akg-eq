package de.sonora.app.ai

import de.sonora.app.core.BandCount

object AiValidator {

    fun validate(
        bandCount: BandCount,
        levelsDb: List<Float>,
        preampDb: Float
    ): ValidationStatus {
        val passed = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1. Band size check
        if (levelsDb.size == bandCount.size) {
            passed.add("Bandanzahl stimmt mit Profil überein (${levelsDb.size} Bänder)")
        } else {
            errors.add("Ungültige Bandanzahl: Erwartet ${bandCount.size}, erhalten ${levelsDb.size}")
        }

        // 2. Numeric safety (NaN / Infinity)
        val hasNonFinite = levelsDb.any { it.isNaN() || it.isInfinite() }
        if (!hasNonFinite) {
            passed.add("Alle Bandpegel sind gültige endliche Gleitkommazahlen")
        } else {
            errors.add("Mindestens ein Bandwert ist NaN oder unendlich")
        }

        // 3. Pegelbereich [-12 .. +12 dB]
        val outOfBounds = levelsDb.any { it < -12.0f || it > 12.0f }
        if (!outOfBounds) {
            passed.add("Alle Pegelwerte liegen innerhalb des zulässigen Bereichs (−12 bis +12 dB)")
        } else {
            errors.add("Pegel überschreitet den Grenzwert von ±12 dB")
        }

        // 4. Preamp safety
        if (!preampDb.isNaN() && !preampDb.isInfinite() && preampDb in -12.0f..12.0f) {
            passed.add("Vorverstärkung ist im gültigen Bereich (${preampDb} dB)")
        } else {
            errors.add("Vorverstärkung ungültig oder außerhalb von ±12 dB ($preampDb)")
        }

        // 5. Headroom verification
        val maxBoost = levelsDb.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val peakSum = maxBoost + preampDb
        if (peakSum <= 3.5f) {
            passed.add("Headroom-Schutz erfüllt (Summenanhebung: %.1f dB)".format(peakSum))
        } else {
            errors.add("Headroom-Warnung: Summenanhebung (%.1f dB) birgt Übersteuerungsgefahr".format(peakSum))
        }

        return ValidationStatus(
            isValid = errors.isEmpty(),
            checksPassed = passed,
            errors = errors
        )
    }
}
