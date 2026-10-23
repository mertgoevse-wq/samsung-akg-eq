package de.sonora.app.core

import de.sonora.app.data.PresetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetCatalogTest {

    private val requiredIds = listOf(
        "neutral", "basskontrolle", "kraeftiger_bass", "stimme", "podcast",
        "klassik", "rock", "metal", "hiphop", "elektronisch",
        "kino", "buehne", "raumklang", "nacht"
    )

    @Test
    fun `alle 14 geforderten Presets sind in der Bibliothek vorhanden`() {
        val foundIds = PresetCatalog.presets.map { it.id }.toSet()
        requiredIds.forEach { req ->
            assertTrue("Preset '$req' fehlt im PresetCatalog", foundIds.contains(req))
        }
        assertEquals(14, PresetCatalog.presets.size)
    }

    @Test
    fun `jedes Preset enthaelt Zielkurve, Preamp, Herkunft, Unsicherheit und Grenzen`() {
        PresetCatalog.presets.forEach { preset ->
            assertEquals("Preset ${preset.id} muss 10 Bänder haben", 10, preset.levels.size)
            assertTrue("Preamp für ${preset.id} muss zwischen -12 und +12 dB liegen", preset.preampDb in -12f..12f)
            assertTrue("Herkunft für ${preset.id} darf nicht leer sein", preset.origin.isNotBlank())
            assertTrue("Unsicherheit für ${preset.id} darf nicht leer sein", preset.uncertainty.isNotBlank())
            assertTrue("Grenzen für ${preset.id} darf nicht leer sein", preset.boundaryLimits.isNotBlank())
            assertTrue("Vergleich für ${preset.id} muss in Klammern stehen", preset.comparison.startsWith("[") && preset.comparison.endsWith("]"))
            preset.levels.forEach { level ->
                assertTrue("Level $level in ${preset.id} muss im Bereich ±12 dB liegen", level in -12f..12f)
            }
        }
    }

    @Test
    fun `kraeftiger Bass enthaelt korrekten Headroom-Abzug`() {
        val bass = PresetCatalog.find("kraeftiger_bass")
        assertNotNull(bass)
        assertTrue("Kräftiger Bass muss mindestens 2.5 dB Headroom-Abzug (Preamp <= -2.5) haben", bass!!.preampDb <= -2.5f)
        assertTrue("Subbass muss spürbar angehoben sein", bass.levels[0] >= 5.0f)
    }

    @Test
    fun `Preset-Vergleiche behaupten keine lizenzierten oder echten Mehrkanalformate`() {
        PresetCatalog.presets.forEach { preset ->
            assertFalse("Preset ${preset.id} darf keinen Dolby-Codec behaupten", preset.comparison.contains("Dolby", ignoreCase = true))
            assertFalse("Preset ${preset.id} darf kein Atmos behaupten", preset.comparison.contains("Atmos", ignoreCase = true))
            assertFalse("Preset ${preset.id} darf kein echtes Mehrkanalformat behaupten", preset.comparison.contains("5.1", ignoreCase = true))
        }
    }

    @Test
    fun `Preset zu SoundProfile Konvertierung erzeugt vollstaendige Profile`() {
        val voice = PresetCatalog.find("stimme")?.toSoundProfile()
        assertNotNull(voice)
        assertEquals("stimme", voice!!.preset)
        assertEquals("Stimme", voice.name)
        assertEquals(-1.0f, voice.preampDb, 0.001f)
        assertTrue(voice.origin!!.contains("ITU-R"))
    }
}
