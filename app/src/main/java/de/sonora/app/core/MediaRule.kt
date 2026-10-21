package de.sonora.app.core

data class NowPlaying(
    val packageName: String,
    val title: String?,
    val appLabel: String,
    val playing: Boolean
)

data class MediaPermission(val granted: Boolean, val enabled: Boolean)

object MediaRule {
    private val musicPackages = setOf(
        "com.spotify.music", "com.deezer.player", "com.bandcamp.android",
        "com.soundcloud.android", "com.amazon.mp3", "com.pandora.android"
    )
    private val voicePackages = setOf(
        "fm.castbox.audiobook.radio.podcast", "au.com.shiftyjelly.pocketcasts",
        "com.audible.application", "com.google.android.apps.podcasts", "com.spotify.lite"
    )

    fun profileHint(nowPlaying: NowPlaying): String? {
        if (!nowPlaying.playing) return null
        val packageName = nowPlaying.packageName.lowercase()
        return when {
            packageName in musicPackages -> "bass"
            packageName in voicePackages -> "voice"
            else -> null
        }
    }
}
