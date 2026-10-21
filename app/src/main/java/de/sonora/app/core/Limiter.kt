package de.sonora.app.core

/**
 * Ergebnis einer Limiter-Rechnung.
 *
 * @param gainDb die tatsaechlich gesetzte Vorverstaerkung
 * @param clipped true, wenn die Grenze gegriffen hat
 * @param reducedByDb wie viele dB zurueckgenommen wurden
 */
data class LimiterResult(
    val gainDb: Float,
    val clipped: Boolean,
    val reducedByDb: Float
)

object Limiter {

    /**
     * Begrenzt die Gesamtverstaerkung auf [maxSumDb].
     *
     * Der Limiter laesst sich nicht abschalten (NFR-012). Er rechnet nur;
     * das Anwenden auf dem Geraet bleibt Aufgabe der Engine.
     */
    fun limit(sumDb: Float, preampDb: Float, maxSumDb: Float = 0f): LimiterResult {
        val total = sumDb + preampDb
        if (total <= maxSumDb) {
            return LimiterResult(gainDb = preampDb, clipped = false, reducedByDb = 0f)
        }
        val reduction = total - maxSumDb
        return LimiterResult(
            gainDb = preampDb - reduction,
            clipped = true,
            reducedByDb = reduction
        )
    }
}
