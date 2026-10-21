package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSchemaTest {
    @Test
    fun `schema eins wird migriert und behaelt das profil als backup`() {
        val old = ProfileSchema.decode(
            """{"schemaVersion":1,"activeProfileId":"p1","profiles":[{"id":"p1","name":"Bass","levels":[6,4,2,0,0,0,0,0,0,0]}]}"""
        ).getOrThrow()
        val migrated = ProfileSchema.migrate(old)
        assertTrue(migrated.migrated)
        assertEquals(PROFILE_SCHEMA_VERSION, migrated.file.schemaVersion)
        assertEquals(6f, migrated.file.profiles.single().levelsDb.first(), 0.001f)
        assertNotNull(migrated.backup)
    }

    @Test
    fun `schema zwei wird ohne veraenderung gelesen`() {
        val file = StoredProfileFile(profiles = listOf(StoredProfile("p", "Bass", List(10) { 0f })))
        assertEquals(file, ProfileSchema.decode(ProfileSchema.encode(file)).getOrThrow())
        assertFalse(ProfileSchema.migrate(file).migrated)
    }

    @Test
    fun `beschaedigtes oder neueres schema wird abgelehnt`() {
        assertTrue(ProfileSchema.decode("{kaputt").isFailure)
        assertTrue(ProfileSchema.decode("""{"schemaVersion":99,"profiles":[]}""").isFailure)
    }

    @Test
    fun `import verlangt genau ein valides profil`() {
        val valid = StoredProfileFile(profiles = listOf(StoredProfile("p", "Bass", List(10) { 0f })))
        assertTrue(ProfileSchema.validateImport(valid).isValid)
        assertFalse(ProfileSchema.validateImport(valid.copy(profiles = emptyList())).isValid)
        assertFalse(ProfileSchema.validateImport(valid.copy(profiles = listOf(valid.profiles.single().copy(levelsDb = listOf(Float.NaN))))).isValid)
    }

    @Test
    fun `bandzahl unbekannt faellt sicher auf zehn zurueck`() {
        assertEquals(BandCount.TEN, parseBandCount("unknown"))
        assertEquals(BandCount.THIRTY_ONE, parseBandCount("31"))
        assertEquals(BandCount.SIXTY_FOUR, parseBandCount("64"))
        assertNull(ProfileSchema.validateImport(StoredProfileFile()).profile)
    }
}
