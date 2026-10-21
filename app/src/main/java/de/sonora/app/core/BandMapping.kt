package de.sonora.app.core

import kotlin.math.abs

object BandMapping {

    /**
     * Ordnet jedem Geraeteband genau ein UI-Band zu.
     *
     * Rueckgabe: Geraetebandindex -> UI-Bandindex. Das naechstgelegene
     * UI-Band gewinnt; jedes UI-Band wird nur einmal vergeben. Dadurch
     * wird eine Anhebung nicht doppelt angewendet.
     */
    fun mapToDevice(uiFrequencies: List<Int>, deviceCenters: List<Int>): Map<Int, Int> {
        if (uiFrequencies.isEmpty() || deviceCenters.isEmpty()) return emptyMap()
        val taken = HashSet<Int>()
        val result = LinkedHashMap<Int, Int>()
        deviceCenters.forEachIndexed { deviceIndex, center ->
            val best = uiFrequencies.indices.minByOrNull { abs(uiFrequencies[it] - center) }
                ?: return@forEachIndexed
            if (taken.add(best)) result[deviceIndex] = best
        }
        return result
    }
}
