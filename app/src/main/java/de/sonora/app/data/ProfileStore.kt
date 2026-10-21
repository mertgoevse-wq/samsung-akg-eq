package de.sonora.app.data

import android.content.Context
import de.sonora.app.core.BandCount
import de.sonora.app.core.PROFILE_SCHEMA_VERSION
import de.sonora.app.core.parseBandCount
import de.sonora.app.core.ProfileSchema
import de.sonora.app.core.StoredProfile
import de.sonora.app.core.StoredProfileFile
import kotlinx.serialization.Serializable
import java.util.UUID

data class ProfileImportResult(
    val success: Boolean,
    val message: String,
    val profile: SoundProfile? = null,
    val allProfiles: List<SoundProfile>
)

private fun SoundProfile.toStored() = StoredProfile(
    id = id,
    name = name,
    levelsDb = levels,
    bandCount = bandCount,
    preset = preset,
    createdAt = createdAt
)

private fun StoredProfile.toSoundProfile() = SoundProfile(
    id = id,
    name = name,
    levels = levelsDb,
    preset = preset,
    createdAt = createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
    bandCount = bandCount
)

private fun ProfileFile.toStored() = StoredProfileFile(
    schemaVersion = schemaVersion,
    activeProfileId = activeProfileId,
    profiles = profiles.map { profile ->
        StoredProfile(profile.id, profile.name, profile.levels, profile.bandCount, profile.preset, profile.createdAt)
    }
)

private fun StoredProfileFile.toProfileFile() = ProfileFile(
    schemaVersion = PROFILE_SCHEMA_VERSION,
    activeProfileId = activeProfileId,
    profiles = profiles.map(StoredProfile::toSoundProfile)
)

@Serializable
data class SoundProfile(
    val id: String,
    val name: String,
    val levels: List<Float>,
    val preset: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val bandCount: String = "10"
) {
    fun levelsFor(count: BandCount): List<Float> =
        if (parseBandCount(bandCount) == count) {
            List(count.size) { index -> levels.getOrElse(index) { 0f }.coerceIn(-12f, 12f) }
        } else {
            de.sonora.app.core.EqCurve(parseBandCount(bandCount), levels).withBandCount(count).levelsDb
        }
}

@Serializable
data class ProfileFile(
    val schemaVersion: Int = PROFILE_SCHEMA_VERSION,
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
    var migrationNotice: String? = null
        private set

    fun consumeMigrationNotice(): String? = migrationNotice.also { migrationNotice = null }

    fun load(): ProfileFile {
        val raw = prefs.getString(KEY_FILE, null)
            ?: return ProfileFile(profiles = Presets.defaults, activeProfileId = "flat")
        val decoded = ProfileSchema.decode(raw).getOrNull()
            ?: return ProfileFile(profiles = Presets.defaults, activeProfileId = "flat")
        val migration = ProfileSchema.migrate(decoded)
        if (migration.migrated) {
            prefs.edit().putString(KEY_BACKUP_V1, raw).apply()
            migrationNotice = migration.note
        }
        val file = migration.file.toProfileFile()
        if (migration.migrated || decoded.schemaVersion != file.schemaVersion) save(file)
        return if (file.profiles.isEmpty()) {
            ProfileFile(profiles = Presets.defaults, activeProfileId = "flat")
        } else file
    }

    fun save(file: ProfileFile) {
        val normalized = file.copy(schemaVersion = PROFILE_SCHEMA_VERSION)
        prefs.edit()
            .putString(KEY_FILE, ProfileSchema.encode(normalized.toStored()))
            .apply()
    }

    fun exportProfile(id: String): String? {
        val profile = load().profiles.firstOrNull { it.id == id } ?: return null
        return ProfileSchema.encode(
            StoredProfileFile(
                activeProfileId = profile.id,
                profiles = listOf(profile.toStored())
            )
        )
    }

    fun importProfile(raw: String): ProfileImportResult {
        val current = load()
        val decoded = ProfileSchema.decode(raw).getOrElse { error ->
            return ProfileImportResult(false, error.message ?: "Die Datei ist beschädigt.", allProfiles = current.profiles)
        }
        val migrated = ProfileSchema.migrate(decoded).file
        val validation = ProfileSchema.validateImport(migrated)
        val imported = validation.profile
            ?: return ProfileImportResult(false, validation.error ?: "Das Profil ist ungültig.", allProfiles = current.profiles)
        val uniqueId = UUID.randomUUID().toString()
        val renamed = if (current.profiles.any { it.name.equals(imported.name, ignoreCase = true) }) {
            val base = imported.name.take(52)
            "$base (Import)"
        } else imported.name
        val profile = imported.copy(id = uniqueId, name = renamed).toSoundProfile()
        val updated = current.copy(profiles = current.profiles + profile)
        save(updated)
        return ProfileImportResult(true, "Profil „${profile.name}“ importiert.", profile, updated.profiles)
    }

    fun onboarded(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    private companion object {
        const val KEY_FILE = "profile_file"
        const val KEY_BACKUP_V1 = "profile_backup_v1"
        const val KEY_ONBOARDED = "onboarded"
    }
}
