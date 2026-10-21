package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test fun `ringpuffer behaelt die neuesten ereignisse in reihenfolge`() {
        var now = 10L
        val log = EventLog(maxSize = 3, clock = { now++ })
        repeat(5) { log.record("e$it") }
        assertEquals(listOf("e2", "e3", "e4"), log.events().map { it.name })
        assertEquals(listOf(12L, 13L, 14L), log.events().map { it.timestampMs })
    }

    @Test fun `ereignisse bewahren kopie der eigenschaften`() {
        val properties = mutableMapOf("profile_id" to "p1")
        val log = EventLog()
        log.record(EventNames.PROFILE_APPLIED, properties)
        properties["profile_id"] = "changed"
        assertEquals("p1", log.events().single().properties["profile_id"])
    }

    @Test fun `clear leert den puffer und replaceAll begrenzt`() {
        val log = EventLog(maxSize = 2)
        log.replaceAll(listOf(DiagnosticEvent("a"), DiagnosticEvent("b"), DiagnosticEvent("c")))
        assertEquals(listOf("b", "c"), log.events().map { it.name })
        log.clear()
        assertTrue(log.events().isEmpty())
    }

    @Test fun `alle elf tracking events aus der PRD existieren`() {
        assertEquals(11, EventNames.ALL.size)
        assertTrue(EventNames.ALL.containsAll(listOf(
            "app_opened", "profile_applied", "interview_completed", "environment_adapted",
            "blind_test_rated", "preset_selected", "spatial_mode_changed", "streaming_connected",
            "audio_effect_failed", "crash_reported", "profile_exported"
        )))
    }
}
