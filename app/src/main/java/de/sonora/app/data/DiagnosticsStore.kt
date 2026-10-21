package de.sonora.app.data

import android.content.Context
import de.sonora.app.core.DiagnosticEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
private data class StoredEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
    val timestampMs: Long = 0L
)

class DiagnosticsStore(context: Context) {
    private val preferences = context.getSharedPreferences("sonora_events_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(StoredEvent.serializer())

    fun load(): List<DiagnosticEvent> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(serializer, raw).map { DiagnosticEvent(it.name, it.properties, it.timestampMs) }
        }.getOrDefault(emptyList())
    }

    fun save(events: List<DiagnosticEvent>) {
        val safe = events.takeLast(MAX_EVENTS).map { StoredEvent(it.name, it.properties, it.timestampMs) }
        preferences.edit().putString(KEY, json.encodeToString(serializer, safe)).apply()
    }

    private companion object {
        const val KEY = "events"
        const val MAX_EVENTS = 500
    }
}
