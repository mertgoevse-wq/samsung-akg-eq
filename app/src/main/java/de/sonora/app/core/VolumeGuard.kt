package de.sonora.app.core

const val VOLUME_REMINDER_ENABLED_KEY = "volume_reminder_enabled"
const val VOLUME_LOUD_SINCE_KEY = "volume_loud_since_ms"
const val VOLUME_REMINDER_SENT_KEY = "volume_reminder_sent"
const val VOLUME_REMINDER_ACKNOWLEDGED_KEY = "volume_reminder_acknowledged"
const val VOLUME_REMINDER_NOTIFICATION_ID = 43
const val VOLUME_REMINDER_THRESHOLD_PERCENT = 80
const val VOLUME_REMINDER_AFTER_MINUTES = 60
const val MINUTE_MS = 60_000L

data class VolumeState(
    val minutesLoud: Int,
    val reminderDue: Boolean,
    val volumePercent: Int
)

class VolumeGuard(
    private val loudThresholdPercent: Int = VOLUME_REMINDER_THRESHOLD_PERCENT,
    private val sessionMinutes: Int = VOLUME_REMINDER_AFTER_MINUTES
) {
    init {
        require(loudThresholdPercent in 1..100)
        require(sessionMinutes > 0)
    }

    fun loudSessionStart(
        volumePercent: Int,
        activelyPlaying: Boolean,
        existingStartMs: Long?,
        nowMs: Long
    ): Long? = if (activelyPlaying && volumePercent >= loudThresholdPercent) existingStartMs ?: nowMs else null

    fun shouldPostReminder(previouslySent: Boolean, reminderDue: Boolean): Boolean =
        reminderDue && !previouslySent

    fun shouldShowReminder(reminderDue: Boolean, acknowledged: Boolean): Boolean =
        reminderDue && !acknowledged

    fun elapsedMinutes(startedAtMs: Long?, nowMs: Long): Int =
        startedAtMs?.let { ((nowMs - it).coerceAtLeast(0L) / MINUTE_MS).toInt() } ?: 0

    fun update(volumePercent: Int, elapsedMinutes: Int, reminderEnabled: Boolean): VolumeState {
        val safeVolume = volumePercent.coerceIn(0, 100)
        val minutes = if (safeVolume >= loudThresholdPercent) elapsedMinutes.coerceAtLeast(0) else 0
        return VolumeState(
            minutesLoud = minutes,
            reminderDue = reminderEnabled && minutes >= sessionMinutes,
            volumePercent = safeVolume
        )
    }
}
