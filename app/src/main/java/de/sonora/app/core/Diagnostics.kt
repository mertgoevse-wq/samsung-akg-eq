package de.sonora.app.core

data class DiagnosticEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
    val timestampMs: Long = 0L
)

object EventNames {
    const val APP_OPENED = "app_opened"
    const val PROFILE_APPLIED = "profile_applied"
    const val INTERVIEW_COMPLETED = "interview_completed"
    const val ENVIRONMENT_ADAPTED = "environment_adapted"
    const val BLIND_TEST_RATED = "blind_test_rated"
    const val PRESET_SELECTED = "preset_selected"
    const val SPATIAL_MODE_CHANGED = "spatial_mode_changed"
    const val STREAMING_CONNECTED = "streaming_connected"
    const val AUDIO_EFFECT_FAILED = "audio_effect_failed"
    const val CRASH_REPORTED = "crash_reported"
    const val PROFILE_EXPORTED = "profile_exported"

    val ALL = listOf(
        APP_OPENED, PROFILE_APPLIED, INTERVIEW_COMPLETED, ENVIRONMENT_ADAPTED,
        BLIND_TEST_RATED, PRESET_SELECTED, SPATIAL_MODE_CHANGED, STREAMING_CONNECTED,
        AUDIO_EFFECT_FAILED, CRASH_REPORTED, PROFILE_EXPORTED
    )
}

class EventLog(private val maxSize: Int = 500, private val clock: () -> Long = System::currentTimeMillis) {
    init { require(maxSize > 0) }

    private val entries = ArrayDeque<DiagnosticEvent>()

    fun record(name: String, properties: Map<String, String> = emptyMap()) {
        require(name.isNotBlank())
        while (entries.size >= maxSize) entries.removeFirst()
        entries.addLast(DiagnosticEvent(name, properties.toMap(), clock()))
    }

    fun events(): List<DiagnosticEvent> = entries.toList()

    fun clear() = entries.clear()

    fun replaceAll(events: List<DiagnosticEvent>) {
        entries.clear()
        events.takeLast(maxSize).forEach(entries::addLast)
    }
}
