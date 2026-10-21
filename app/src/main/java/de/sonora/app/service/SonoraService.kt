package de.sonora.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.sonora.app.MainActivity
import de.sonora.app.R
import de.sonora.app.audio.EngineStatus
import de.sonora.app.audio.SonoraEngine
import de.sonora.app.core.VOLUME_LOUD_SINCE_KEY
import de.sonora.app.core.VOLUME_REMINDER_ACKNOWLEDGED_KEY
import de.sonora.app.core.VOLUME_REMINDER_ENABLED_KEY
import de.sonora.app.core.VOLUME_REMINDER_NOTIFICATION_ID
import de.sonora.app.core.VOLUME_REMINDER_SENT_KEY
import de.sonora.app.core.VOLUME_REMINDER_THRESHOLD_PERCENT
import de.sonora.app.core.VolumeGuard
import de.sonora.app.core.MINUTE_MS
import de.sonora.app.core.EqCurve
import de.sonora.app.core.parseBandCount
import de.sonora.app.data.ProfileStore

class SonoraService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val safetyPreferences by lazy { getSharedPreferences("sonora_safety_v1", Context.MODE_PRIVATE) }
    private val volumeGuard = VolumeGuard()
    private val volumeReminderCheck = object : Runnable {
        override fun run() {
            checkVolumeReminder()
            handler.postDelayed(this, VOLUME_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val status = if (SonoraEngine.status is EngineStatus.Active) SonoraEngine.status else SonoraEngine.initialize()
        if (status is EngineStatus.Active) {
            val stored = ProfileStore(this).load()
            val profile = stored.profiles.firstOrNull { it.id == stored.activeProfileId } ?: stored.profiles.firstOrNull()
            if (profile != null) {
                val bandCount = parseBandCount(profile.bandCount)
                SonoraEngine.applyCurve(EqCurve(bandCount, profile.levelsFor(bandCount)), preampDb = 0f)
                val enabled = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean(MASTER_ENABLED_KEY, true)
                SonoraEngine.setEnabled(enabled)
            }
        }
        handler.post(volumeReminderCheck)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        SonoraEngine.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun checkVolumeReminder() {
        val manager = getSystemService(AudioManager::class.java)
        val maxVolume = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        val volumePercent = ((manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) * 100f / maxVolume).toInt()
        val now = SystemClock.elapsedRealtime()
        val previousStart = safetyPreferences.getLong(VOLUME_LOUD_SINCE_KEY, -1L).takeIf { it >= 0 && it <= now }
        val enabled = safetyPreferences.getBoolean(VOLUME_REMINDER_ENABLED_KEY, true)
        val sessionStart = if (enabled) volumeGuard.loudSessionStart(volumePercent, manager?.isMusicActive == true, previousStart, now) else null
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (sessionStart == null) {
            safetyPreferences.edit()
                .remove(VOLUME_LOUD_SINCE_KEY)
                .remove(VOLUME_REMINDER_SENT_KEY)
                .remove(VOLUME_REMINDER_ACKNOWLEDGED_KEY)
                .apply()
            notificationManager.cancel(VOLUME_REMINDER_NOTIFICATION_ID)
        } else if (sessionStart != previousStart) {
            safetyPreferences.edit()
                .putLong(VOLUME_LOUD_SINCE_KEY, sessionStart)
                .putBoolean(VOLUME_REMINDER_SENT_KEY, false)
                .putBoolean(VOLUME_REMINDER_ACKNOWLEDGED_KEY, false)
                .apply()
        }
        val minutes = volumeGuard.elapsedMinutes(sessionStart, now)
        val state = volumeGuard.update(volumePercent, minutes, enabled)
        val shouldNotify = volumeGuard.shouldPostReminder(
            previouslySent = safetyPreferences.getBoolean(VOLUME_REMINDER_SENT_KEY, false),
            reminderDue = state.reminderDue
        )
        if (shouldNotify) {
            val reminder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sonora)
                .setContentTitle("Zeit für eine Hörpause")
                .setContentText("Du hörst seit einer Stunde bei hoher Medienlautstärke. Sonora ändert sie nicht automatisch.")
                .setContentIntent(buildOpenIntent())
                .setAutoCancel(true)
                .setSilent(true)
                .build()
            notificationManager.notify(VOLUME_REMINDER_NOTIFICATION_ID, reminder)
            safetyPreferences.edit().putBoolean(VOLUME_REMINDER_SENT_KEY, true).apply()
        }
    }

    private fun buildOpenIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(): Notification {
        val openApp = buildOpenIntent()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sonora)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private companion object {
        const val SETTINGS_PREFS = "sonora_settings_v1"
        const val MASTER_ENABLED_KEY = "master_enabled"
        const val VOLUME_CHECK_INTERVAL_MS = MINUTE_MS
        const val CHANNEL_ID = "sonora_background"
        const val NOTIFICATION_ID = 42
    }
}
