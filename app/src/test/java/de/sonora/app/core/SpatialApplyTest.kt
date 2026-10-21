package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import de.sonora.app.ui.SpatialScreenState
import de.sonora.app.ui.spatialNoticeText
import org.junit.Test

class SpatialApplyTest {

    @Test
    fun `alle neun Modi der Spezifikation existieren`() {
        assertEquals(9, SpatialModeCatalog.all.size)
        assertEquals(
            setOf(
                "Kino-Weit", "Objekt-Raum", "Raum 21.1", "Kopf-3D", "Live-Saal",
                "Studio-Trocken", "Sprach-Fokus", "Nacht", "Breit & Warm"
            ),
            SpatialModeCatalog.all.map { it.title }.toSet()
        )
    }

    @Test
    fun `Aus liefert neutrale Parameter`() {
        assertEquals(
            SpatialParams(0f, 0f, 0f, 0f, 0f),
            SpatialModeCatalog.params(SpatialMode.KINO_WEIT, 0f)
        )
    }

    @Test
    fun `jeder Modus liefert bei voller Intensitaet mindestens einen Effekt`() {
        SpatialModeCatalog.all.forEach { mode ->
            val p = SpatialModeCatalog.params(mode, 1f)
            val sum = p.crossfeedMix + p.wideningMix + p.reverbMix + p.centerFocus + p.compression
            assertTrue("Modus ${mode.title} ist ohne Wirkung", sum > 0f)
        }
    }

    @Test
    fun `Intensitaet wird auf null bis eins begrenzt`() {
        assertTrue(SpatialModeCatalog.params(SpatialMode.NACHT, 5f).compression in 0f..1f)
        assertEquals(0f, SpatialModeCatalog.params(SpatialMode.NACHT, -3f).compression, 0.001f)
    }

    @Test
    fun `Studio-Trocken hat keinen Hall`() {
        assertEquals(0f, SpatialModeCatalog.params(SpatialMode.STUDIO_TROCKEN, 1f).reverbMix, 0.001f)
    }

    @Test
    fun `Sprach-Fokus betont die Mitte`() {
        assertTrue(SpatialModeCatalog.params(SpatialMode.SPRACH_FOKUS, 1f).centerFocus > 0f)
    }

    @Test
    fun `alle Effektparameter bleiben im erlaubten Bereich`() {
        SpatialModeCatalog.all.forEach { mode ->
            val params = SpatialModeCatalog.params(mode, 1f)
            listOf(
                params.crossfeedMix,
                params.wideningMix,
                params.reverbMix,
                params.centerFocus,
                params.compression
            ).forEach { assertTrue("${mode.title}: $it", it in 0f..1f) }
        }
    }

    @Test
    fun `global ausgeschaltet zeigt Aus-Hinweis statt falscher Geraetewarnung`() {
        val state = SpatialScreenState(
            masterEnabled = false,
            spatialMode = SpatialMode.KINO_WEIT,
            spatialIntensity = 0.7f,
            spatialAvailable = false,
            availabilityMessage = null
        )

        assertEquals(
            "Sonora ist ausgeschaltet. Schalte Sonora im Bereich „Jetzt“ ein, um Raumklang anzuwenden.",
            spatialNoticeText(state)
        )
    }

    @Test
    fun `nicht unterstuetzter Modus zeigt Geraetehinweis bei aktivem Hauptschalter`() {
        val state = SpatialScreenState(
            masterEnabled = true,
            spatialMode = SpatialMode.KINO_WEIT,
            spatialIntensity = 0.7f,
            spatialAvailable = false,
            availabilityMessage = "Das Gerät bietet keinen steuerbaren Raumhall."
        )

        assertEquals("Das Gerät bietet keinen steuerbaren Raumhall.", spatialNoticeText(state))
    }

    @Test
    fun `Aus ohne deaktivierten Hauptschalter zeigt keinen Verfuegbarkeitshinweis`() {
        val state = SpatialScreenState(
            masterEnabled = true,
            spatialMode = null,
            spatialIntensity = 0.7f,
            spatialAvailable = false,
            availabilityMessage = null
        )

        assertEquals(null, spatialNoticeText(state))
    }

    @Test
    fun `Modusnamen enthalten keine geschuetzten Marken`() {
        val protectedNames = listOf("dolby", "dts", "auro")
        SpatialModeCatalog.all.forEach { mode ->
            val haystack = (mode.title + " " + mode.description).lowercase()
            protectedNames.forEach { brand ->
                assertFalse("Modus '${mode.title}' nennt $brand", haystack.contains(brand))
            }
        }
    }
}
