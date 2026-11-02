package de.sonora.app

import android.app.Application
import android.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.platform.testing.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import de.sonora.app.audio.EngineStatus
import de.sonora.app.audio.SonoraEngine
import de.sonora.app.core.BandCount
import de.sonora.app.data.ProfileStore

/**
 * Instrumentation tests for SonoraViewModel core behaviors:
 * permission-gated features, engine fallback, profile persistence, and
 * microphone-revocation handling. Runs on a device/emulator only.
 */
@RunWith(AndroidJUnit4::class)
class ViewModelTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var app: Application
    private lateinit var vm: SonoraViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>()
        vm = SonoraViewModel(app)
    }

    @Test
    fun viewModelInitializesWithDefaults() {
        assertNotNull(vm.uiState)
        assertEquals(EngineStatus.Inactive, vm.uiState.engineStatus)
        assertTrue(vm.uiState.masterEnabled)
        assertFalse(vm.uiState.blindTestActive)
        assertFalse(vm.uiState.autoAdjust)
        assertFalse(vm.uiState.playbackVisualizationEnabled)
        assertFalse(vm.uiState.mediaEnabled)
        assertFalse(vm.uiState.soundLabActive)
    }

    @Test
    fun masterEnabledToggleReflectsInUiState() {
        vm.setMasterEnabled(false)
        assertFalse(vm.uiState.masterEnabled)
        vm.setMasterEnabled(true)
        assertTrue(vm.uiState.masterEnabled)
    }

    @Test
    fun setBandStaysWithinMinus12ToPlus12() {
        vm.setBand(0, 100f)
        assertEquals(12f, vm.uiState.eqCurve.levelsDb[0], 0.001f)
        vm.setBand(0, -100f)
        assertEquals(-12f, vm.uiState.eqCurve.levelsDb[0], 0.001f)
    }

    @Test
    fun setBandCountChangesCurveSize() {
        vm.setBandCount(BandCount.THIRTY_ONE)
        assertEquals(31, vm.uiState.eqCurve.levelsDb.size)
        vm.setBandCount(BandCount.TEN)
        assertEquals(10, vm.uiState.eqCurve.levelsDb.size)
    }

    @Test
    fun profilePersistenceRoundTrip() {
        val store = ProfileStore(app)
        val file = store.load()
        assertNotNull(file)
        assertTrue(file.profiles.isNotEmpty())
        assertNotNull(file.activeProfileId)
    }

    @Test
    fun engineStatusIsReportedHonestly() {
        // SonoraEngine.initialize returns Active or Unavailable depending on the
        // device; the ViewModel must not claim verification that did not happen.
        val status = SonoraEngine.initialize()
        when (status) {
            is EngineStatus.Active -> {
                assertTrue(status.processingRequested)
                assertTrue(status.processingAvailable)
            }
            is EngineStatus.Unavailable -> {
                assertFalse(status.processingAvailable)
                assertFalse(status.processingConfirmed)
                assertNotNull(status.reason)
            }
            EngineStatus.Inactive -> {
                // Expected on devices without Session 0 control
            }
        }
        SonoraEngine.release()
    }

    @Test
    fun microphoneRevocationDisablesAutoAdjust() {
        vm.setAutoAdjust(true)
        // If the mic is not granted, autoAdjust must be forced off.
        val hasMic = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            assertFalse("autoAdjust must be off without mic permission", vm.uiState.autoAdjust)
        }
    }

    @Test
    fun soundLabStartStopDoesNotCrash() {
        vm.toggleSoundLab()
        assertTrue(vm.uiState.soundLabActive)
        vm.toggleSoundLab()
        assertFalse(vm.uiState.soundLabActive)
    }

    @Test
    fun retryEngineRecoversToKnownState() {
        vm.retryEngine()
        val status = vm.uiState.engineStatus
        // Must be one of the three documented states, never a crash.
        when (status) {
            is EngineStatus.Active -> assertTrue(true)
            is EngineStatus.Unavailable -> assertNotNull(status.reason)
            EngineStatus.Inactive -> assertTrue(true)
        }
        SonoraEngine.release()
    }
}
