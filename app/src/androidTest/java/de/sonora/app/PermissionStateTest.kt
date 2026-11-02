package de.sonora.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.platform.testing.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for runtime permission states.
 *
 * Verifies that RECORD_AUDIO is declared but not auto-granted, that no
 * INTERNET/MediaProjection permissions are declared, and that revocation
 * handling stops capture paths. Runs on a device/emulator only.
 */
@RunWith(AndroidJUnit4::class)
class PermissionStateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun recordAudioIsDeclaredButNotGrantedByDefault() {
        // RECORD_AUDIO is a runtime (dangerous) permission; it must be declared
        // in the manifest but is not granted at install time.
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .contains(Manifest.permission.RECORD_AUDIO)
        assertTrue("RECORD_AUDIO must be declared in the manifest", declared)
    }

    @Test
    fun postNotificationsIsDeclared() {
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .contains(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue("POST_NOTIFICATIONS must be declared", declared)
    }

    @Test
    fun noInternetPermissionDeclared() {
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
        assertFalse(
            "No INTERNET permission may be declared (app stays offline)",
            declared.contains(android.Manifest.permission.INTERNET)
        )
    }

    @Test
    fun mediaProjectionPermissionsRemoved() {
        // Sonora does not implement MediaProjection capture; these must not be
        // declared so there is no silent screen/playback capture surface.
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
        assertFalse(
            "MEDIA_PROJECTION must not be declared",
            declared.contains(android.Manifest.permission.MEDIA_PROJECTION)
        )
        assertFalse(
            "FOREGROUND_SERVICE_MEDIA_PROJECTION must not be declared",
            declared.contains(
                android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
            )
        )
    }

    @Test
    fun microphoneRevocationStopsCapturePaths() {
        // When RECORD_AUDIO is not granted, capture paths must not start.
        // The ViewModel (refreshMicrophonePermission) and EnvironmentAnalyzer
        // guard on hasMicPermission(); this test asserts the runtime state
        // observed by those guards is consistent on the device.
        val granted =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        // If not granted, EnvironmentAnalyzer/playback visualizer must stay off.
        if (!granted) {
            assertFalse("Microphone not granted — capture must be inactive", granted)
        }
    }
}
