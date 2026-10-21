package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRuleTest {
    private fun source(packageName: String, playing: Boolean = true) = NowPlaying(packageName, null, "App", playing)

    @Test fun `bekannte musik-apps schlagen ein musikprofil vor`() {
        assertEquals("bass", MediaRule.profileHint(source("com.spotify.music")))
        assertEquals("bass", MediaRule.profileHint(source("com.deezer.player")))
    }

    @Test fun `bekannte podcast-app schlaegt sprachprofil vor`() {
        assertEquals("voice", MediaRule.profileHint(source("au.com.shiftyjelly.pocketcasts")))
    }

    @Test fun `unbekannte oder pausierte quelle aendert das profil nicht`() {
        assertNull(MediaRule.profileHint(source("com.example.unknown")))
        assertNull(MediaRule.profileHint(source("com.spotify.music", playing = false)))
    }
}
