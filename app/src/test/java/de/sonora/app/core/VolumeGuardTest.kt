package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeGuardTest {
    @Test fun `unterhalb der lautstaerkegrenze gibt es keine erinnerung`() {
        assertFalse(VolumeGuard().update(79, 90, true).reminderDue)
    }

    @Test fun `ab einer stunde laut wird erinnert`() {
        val state = VolumeGuard().update(90, 60, true)
        assertTrue(state.reminderDue)
        assertEquals(60, state.minutesLoud)
    }

    @Test fun `deaktivierte erinnerung bleibt still`() {
        assertFalse(VolumeGuard().update(100, 120, false).reminderDue)
    }

    @Test fun `guard veraendert den gemeldeten lautstaerkewert nicht`() {
        assertEquals(90, VolumeGuard().update(90, 120, true).volumePercent)
    }

    @Test fun `laute spielzeit beginnt erst bei aktiver wiedergabe ueber der schwelle`() {
        val guard = VolumeGuard()
        assertEquals(100L, guard.loudSessionStart(85, true, null, 100L))
        assertEquals(null, guard.loudSessionStart(79, true, null, 100L))
        assertEquals(null, guard.loudSessionStart(90, false, null, 100L))
        assertEquals(50L, guard.loudSessionStart(90, true, 50L, 100L))
    }

    @Test fun `minuten zaehlen nur ab start und laufen nicht rueckwaerts`() {
        val guard = VolumeGuard()
        assertEquals(61, guard.elapsedMinutes(1_000L, 3_661_000L))
        assertEquals(0, guard.elapsedMinutes(5_000L, 1_000L))
        assertEquals(0, guard.elapsedMinutes(null, 3_661_000L))
    }

    @Test fun `benachrichtigung wird nur einmal pro faelliger sitzung gepostet`() {
        val guard = VolumeGuard()
        assertTrue(guard.shouldPostReminder(previouslySent = false, reminderDue = true))
        assertFalse(guard.shouldPostReminder(previouslySent = true, reminderDue = true))
        assertFalse(guard.shouldPostReminder(previouslySent = false, reminderDue = false))
    }

    @Test fun `in-app-erinnerung bleibt sichtbar bis bestaetigt`() {
        val guard = VolumeGuard()
        assertTrue(guard.shouldShowReminder(reminderDue = true, acknowledged = false))
        assertFalse(guard.shouldShowReminder(reminderDue = true, acknowledged = true))
        assertFalse(guard.shouldShowReminder(reminderDue = false, acknowledged = false))
    }

    @Test fun `eingaben werden auf gueltige werte begrenzt`() {
        val state = VolumeGuard().update(200, -5, true)
        assertEquals(100, state.volumePercent)
        assertEquals(0, state.minutesLoud)
    }
}
