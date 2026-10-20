package de.sonora.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SoundProfile(
    val id: String,
    val name: String,
    val levels: List<Float>,
    val preset: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ProfileFile(
    val schemaVersion: Int = 1,
    val activeProfileId: String = "flat",
    val profiles: List<SoundProfile> = emptyList()
)

object Presets {
    val defaults: List<SoundProfile> = listOf(
        SoundProfile("flat", "Neutral", List(10) { 0f }, "flat"),
        SoundProfile("bass", "Bass", listOf(6f, 5f, 4f, 2f, 0f, -1f, -1f, 0f, 1f, 1f), "bass"),
        SoundProfile("voice", "Sprache", listOf(-3f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f), "voice"),
        SoundProfile("bright", "Klar und hell", listOf(-1f, 0f, 0f, 1f, 2f, 3f, 4f, 4f, 3f, 2f), "bright"),
        SoundProfile("warm", "Warm", listOf(3f, 3f, 2f, 1f, 0f, 0f, -1f, -2f, -2f, -2f), "warm"),
        SoundProfile("night", "Nacht", listOf(-2f, -1f, 0f, 2f, 3f, 2f, 0f, -2f, -3f, -4f), "night")
    )
}

class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("sonora_profiles_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): ProfileFile {
        val raw = prefs.getString(KEY_FILE, null)
            ?: return ProfileFile(profiles = Presets.defaults, activeProfileId = "flat")
        return try {
            val parsed = json.decodeFromString(ProfileFile.serializer(), raw)
            if (parsed.profiles.isEmpty()) {
                ProfileFile(profiles = Presets.defaults, activeProfileId = "flat")
            } else {
                parsed
            }
        } catch (_: Throwable) {
            ProfileFile(profiles = Presets.defaults, activeProfileId = "flat")
        }
    }

    fun save(file: ProfileFile) {
        prefs.edit()
            .putString(KEY_FILE, json.encodeToString(ProfileFile.serializer(), file))
            .apply()
    }

    fun onboarded(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    private companion object {
        const val KEY_FILE = "profile_file"
        const val KEY_ONBOARDED = "onboarded"
    }
}
