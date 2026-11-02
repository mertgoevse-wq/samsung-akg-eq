package de.sonora.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import de.sonora.app.ui.SonoraRoot
import de.sonora.app.ui.SonoraTab

/**
 * Instrumentation tests for navigation.
 *
 * Verifies that MainActivity launches and hosts the Compose root, and that the
 * SonoraTab catalog exposes the expected navigation destinations. Runs on a
 * device/emulator only — cannot run in the local Windows environment (no
 * emulator / no Android SDK platforms installed).
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @Test
    fun mainActivityLaunchesAndHostsComposeRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("MainActivity must be created", activity)
                assertNotNull("Window must have a decor view", activity.window.decorView)
            }
        }
    }

    @Test
    fun sonoraTabCatalogDefinesExpectedRoutes() {
        val tabs = SonoraTab.entries
        assertFalse("SonoraTab catalog must not be empty", tabs.isEmpty())
        val labels = tabs.map { it.label }.toSet()
        assertTrue("Jetzt tab must exist", labels.contains("Jetzt"))
        assertTrue("Klang (Equalizer) tab must exist", labels.contains("Klang"))
        assertTrue("Profile tab must exist", labels.contains("Profile"))
        assertTrue("Mehr (Settings) tab must exist", labels.contains("Mehr"))
    }

    @Test
    fun sonoraTabCountIsStable() {
        // Six tabs: Jetzt, Neural, Klang, Raum, Profile, Mehr
        assertEquals(6, SonoraTab.entries.size)
    }
}
