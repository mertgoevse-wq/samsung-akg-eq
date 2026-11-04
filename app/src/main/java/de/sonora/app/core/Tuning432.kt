package de.sonora.app.core

import kotlin.math.pow

/**
 * 432 Hz Tuning — A=432 Hz statt A=440 Hz.
 *
 * Schaltet die gesamte Audio-Verarbeitung auf 432 Hz Stimmung um.
 * Der Frequenz-Shift-Faktor ist 432/440 = 0.981818...
 *
 * Verwendung: Wenn aktiviert, werden alle Filter-Frequenzen, EQ-Bänder
 * und DSP-Parameter entsprechend skaliert.
 */
object Tuning432 {

    const val REFERENCE_A440 = 440f
    const val REFERENCE_A432 = 432f

    /** Frequenz-Shift-Faktor: 432/440 */
    val shiftFactor: Float = REFERENCE_A432 / REFERENCE_A440

    /** Ob 432 Hz Tuning aktiv ist. */
    @Volatile
    var enabled: Boolean = false
        private set

    fun enable() { enabled = true }
    fun disable() { enabled = false }
    fun toggle() { enabled = !enabled }

    /** Wandelt eine 440 Hz-Frequenz in die entsprechende 432 Hz-Frequenz um. */
    fun convertFreq(freqHz: Float): Float {
        if (!enabled) return freqHz
        return freqHz * shiftFactor
    }

    /** Wandelt eine 432 Hz-Frequenz zurück in 440 Hz. */
    fun revertFreq(freqHz: Float): Float {
        if (!enabled) return freqHz
        return freqHz / shiftFactor
    }

    /** Berechnet den Pitch-Shift-Faktor für Audio-Playback. */
    fun pitchShiftFactor(): Float = if (enabled) shiftFactor else 1.0f

    /** Gibt den aktuellen Referenzton zurück. */
    fun referencePitch(): Float = if (enabled) REFERENCE_A432 else REFERENCE_A440
}