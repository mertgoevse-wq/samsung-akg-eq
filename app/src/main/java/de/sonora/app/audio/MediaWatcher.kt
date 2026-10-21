package de.sonora.app.audio

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import de.sonora.app.core.NowPlaying

class MediaWatcher(
    private val context: Context,
    private val onChange: (NowPlaying?) -> Unit
) {
    private val listenerComponent = ComponentName(context, SonoraNotificationListener::class.java)
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        val active = controllers.orEmpty()
            .mapNotNull { controller ->
                val state = controller.playbackState
                val playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
                if (!playing) return@mapNotNull null
                val packageName = controller.packageName
                val title = controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                val label = runCatching {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                }.getOrDefault(packageName)
                NowPlaying(packageName, title, label, true)
            }
            .firstOrNull()
        onChange(active)
    }

    fun isListenerEnabled(): Boolean {
        val granted = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        return TextUtils.SimpleStringSplitter(':').run {
            setString(granted)
            any { ComponentName.unflattenFromString(it) == listenerComponent }
        }
    }

    fun start(): Boolean {
        if (!isListenerEnabled() || sessions == null) return false
        return try {
            sessions.addOnActiveSessionsChangedListener(listener, listenerComponent)
            listener.onActiveSessionsChanged(sessions.getActiveSessions(listenerComponent))
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun stop() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(listener) }
        onChange(null)
    }

    fun currentNowPlaying(): NowPlaying? {
        val manager = sessions ?: return null
        if (!isListenerEnabled()) return null
        return try {
            manager.getActiveSessions(listenerComponent).firstNotNullOfOrNull { controller ->
                val playing = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                if (!playing) return@firstNotNullOfOrNull null
                val packageName = controller.packageName
                val label = runCatching {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                }.getOrDefault(packageName)
                NowPlaying(
                    packageName = packageName,
                    title = controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
                    appLabel = label,
                    playing = true
                )
            }
        } catch (_: SecurityException) {
            null
        }
    }
}

class SonoraNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        // MediaSession access is granted by Android only after explicit user approval.
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit
}
