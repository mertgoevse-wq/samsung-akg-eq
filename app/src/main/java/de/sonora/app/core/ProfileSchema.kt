package de.sonora.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

const val PROFILE_SCHEMA_VERSION = 2

@Serializable
data class StoredProfile(
    val id: String,
    val name: String,
    val levelsDb: List<Float> = emptyList(),
    val bandCount: String = "10",
    val preset: String? = null,
    val createdAt: Long = 0L
) {
    fun bandCountEnum(): BandCount = parseBandCount(bandCount)
}

@Serializable
data class StoredProfileFile(
    val schemaVersion: Int = PROFILE_SCHEMA_VERSION,
    val activeProfileId: String = "flat",
    val profiles: List<StoredProfile> = emptyList()
)

@Serializable
private data class LegacyProfile(
    val id: String,
    val name: String,
    val levels: List<Float> = emptyList(),
    val preset: String? = null,
    val createdAt: Long = 0L
)

@Serializable
private data class LegacyProfileFile(
    val schemaVersion: Int = 1,
    val activeProfileId: String = "flat",
    val profiles: List<LegacyProfile> = emptyList()
)

data class MigrationResult(
    val file: StoredProfileFile,
    val migrated: Boolean,
    val note: String? = null,
    val backup: StoredProfileFile? = null
)

data class ProfileValidation(val profile: StoredProfile?, val error: String?) {
    val isValid: Boolean get() = profile != null && error == null
}

fun parseBandCount(value: String?): BandCount = when (value) {
    "31" -> BandCount.THIRTY_ONE
    "64", "64+" -> BandCount.SIXTY_FOUR
    else -> BandCount.TEN
}

object ProfileSchema {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(raw: String): Result<StoredProfileFile> = runCatching {
        val tree = json.parseToJsonElement(raw) as? JsonObject
            ?: error("Die Profildatei hat kein gültiges Objektformat.")
        val version = tree["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        when (version) {
            1 -> {
                val legacy = json.decodeFromJsonElement(LegacyProfileFile.serializer(), tree)
                StoredProfileFile(
                    schemaVersion = 1,
                    activeProfileId = legacy.activeProfileId,
                    profiles = legacy.profiles.map { old ->
                        StoredProfile(old.id, old.name, old.levels, "10", old.preset, old.createdAt)
                    }
                )
            }
            PROFILE_SCHEMA_VERSION -> json.decodeFromJsonElement(StoredProfileFile.serializer(), tree)
            else -> error("Profilschema $version wird nicht unterstützt.")
        }
    }

    fun encode(file: StoredProfileFile): String =
        json.encodeToString(StoredProfileFile.serializer(), file.copy(schemaVersion = PROFILE_SCHEMA_VERSION))

    fun migrate(file: StoredProfileFile): MigrationResult {
        if (file.schemaVersion >= PROFILE_SCHEMA_VERSION) return MigrationResult(file, migrated = false)
        val backup = file
        val migrated = file.copy(
            schemaVersion = PROFILE_SCHEMA_VERSION,
            profiles = file.profiles.map { profile ->
                profile.copy(
                    name = profile.name.trim().take(60).ifBlank { "Mein Profil" },
                    levelsDb = EqCurve(profile.bandCountEnum(), profile.levelsDb).normalized().levelsDb,
                    bandCount = profile.bandCount.ifBlank { "10" }
                )
            }
        )
        return MigrationResult(
            file = migrated,
            migrated = true,
            note = "Deine Profile wurden aktualisiert. Eine Sicherung der vorherigen Fassung wurde behalten.",
            backup = backup
        )
    }

    fun validateImport(file: StoredProfileFile): ProfileValidation {
        if (file.profiles.size != 1) return ProfileValidation(null, "Die Datei muss genau ein Profil enthalten.")
        val imported = file.profiles.single()
        val name = imported.name.trim()
        if (name.isEmpty()) return ProfileValidation(null, "Das Profil hat keinen Namen.")
        if (name.length > 60) return ProfileValidation(null, "Der Profilname ist länger als 60 Zeichen.")
        if (imported.levelsDb.size != imported.bandCountEnum().size) {
            return ProfileValidation(null, "Die Anzahl der Klangwerte passt nicht zur Bandzahl.")
        }
        if (imported.levelsDb.any { !it.isFinite() || it !in EqModel.MIN_DB..EqModel.MAX_DB }) {
            return ProfileValidation(null, "Klangwerte müssen zwischen −12 und +12 dB liegen.")
        }
        return ProfileValidation(imported.copy(name = name, id = "imported"), null)
    }
}
