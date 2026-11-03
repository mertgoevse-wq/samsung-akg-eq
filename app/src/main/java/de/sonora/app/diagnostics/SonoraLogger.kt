package de.sonora.app.diagnostics

import android.util.Log

/**
 * Zentrales Diagnostik-Logging für Sonora.
 *
 * Erfasst, was funktioniert und was nicht, mit Begründung.
 * Logs werden über Android Logcat ausgegeben (Tag "Sonora").
 * Kein Netzwerk, kein Telemetry-Upload — rein lokal.
 */
object SonoraLogger {

    private const val TAG = "Sonora"

    /** Status eines Subsystems oder einer Operation. */
    enum class Status { OK, PARTIAL, FAILED, UNKNOWN }

    data class Entry(
        val subsystem: String,
        val status: Status,
        val message: String,
        val cause: String? = null
    )

    private val entries = mutableListOf<Entry>()

    fun log(subsystem: String, status: Status, message: String, cause: String? = null) {
        val entry = Entry(subsystem, status, message, cause)
        entries.add(entry)
        val prefix = when (status) {
            Status.OK -> "✅"
            Status.PARTIAL -> "⚠️"
            Status.FAILED -> "❌"
            Status.UNKNOWN -> "❓"
        }
        val fullMsg = buildString {
            append("$prefix [$subsystem] $message")
            if (cause != null) append(" — Grund: $cause")
        }
        when (status) {
            Status.FAILED -> Log.e(TAG, fullMsg)
            Status.PARTIAL, Status.UNKNOWN -> Log.w(TAG, fullMsg)
            Status.OK -> Log.i(TAG, fullMsg)
        }
    }

    fun ok(subsystem: String, message: String) = log(subsystem, Status.OK, message)
    fun partial(subsystem: String, message: String, cause: String? = null) = log(subsystem, Status.PARTIAL, message, cause)
    fun fail(subsystem: String, message: String, cause: String? = null) = log(subsystem, Status.FAILED, message, cause)
    fun unknown(subsystem: String, message: String, cause: String? = null) = log(subsystem, Status.UNKNOWN, message, cause)

    /** Gibt alle bisherigen Log-Einträge zurück (für Diagnostics-UI). */
    fun getEntries(): List<Entry> = entries.toList()

    /** Löscht alle Einträge (z. B. bei App-Neustart). */
    fun clear() = entries.clear()

    /** Zusammenfassung als lesbaren String. */
    fun summary(): String = entries.joinToString("\n") { e ->
        val icon = when (e.status) {
            Status.OK -> "✅"
            Status.PARTIAL -> "⚠️"
            Status.FAILED -> "❌"
            Status.UNKNOWN -> "❓"
        }
        "$icon ${e.subsystem}: ${e.message}" + (e.cause?.let { " ($it)" } ?: "")
    }
}
