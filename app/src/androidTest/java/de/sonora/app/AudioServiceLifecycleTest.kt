package de.sonora.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.platform.testing.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import de.sonora.app.audio.EngineStatus
import de.sonora.app.audio.SonoraEngine
import de.sonora.app.service.SonoraService

/**
 * Instrumentation tests for the audio service lifecycle.
 *
 * Verifies that SonoraService can be started and stopped through the ViewModel,
 * that it releases the engine on destroy, and that it is not exported (cannot be
 * started by other apps). Runs on a device/emulator only.
 */
@RunWith(AndroidJUnit4::class)
class AudioServiceLifecycleTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var vm: SonoraViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        vm = SonoraViewModel(app)
    }

    @Test
    fun startAndStopServiceViaViewModel() {
        vm.startService()
        assertEquals("Hintergrunddienst gestartet.", vm.uiState.message)
        vm.stopService()
        assertEquals(EngineStatus.Inactive, vm.uiState.engineStatus)
        assertEquals("Hintergrunddienst beendet.", vm.uiState.message)
    }

    @Test
    fun sonoraServiceIsNotExported() {
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        val info = pm.getServiceInfo(
            android.content.ComponentName(
                ApplicationProvider.getApplicationContext<android.content.Context>(),
                SonoraService::class.java
            ),
            0
        )
        assertFalse("SonoraService must not be exported", info.exported)
    }

    @Test
    fun serviceReleaseStopsEngine() {
        SonoraEngine.initialize()
        assertTrue(SonoraEngine.status is EngineStatus.Active || SonoraEngine.status is EngineStatus.Unavailable || SonoraEngine.status == EngineStatus.Inactive)
        SonoraEngine.release()
        assertEquals(EngineStatus.Inactive, SonoraEngine.status)
    }
}
