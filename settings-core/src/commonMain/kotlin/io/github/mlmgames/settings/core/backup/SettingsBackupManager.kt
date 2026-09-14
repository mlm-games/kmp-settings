@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.mlmgames.settings.core.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class SettingsBackupManager<T>(
    private val dataStore: DataStore<Preferences>,
    private val schema: SettingsSchema<T>,
    private val appId: String,
    private val schemaVersion: Int,
    private val deviceInfoProvider: (() -> DeviceInfo)? = null,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Export all settings from DataStore.
     * Iterates schema fields and delegates encoding to each field.
     * Explicit nulls are exported (empty payload after the prefix); fields that
     * fail to encode are recorded per-field and abort the export so a backup is
     * never silently partial.
     */
    suspend fun export(): ExportResult {
        return try {
            val prefs = dataStore.data.first()
            val settingsMap = mutableMapOf<String, String>()
            val failed = mutableListOf<String>()

            for (field in schema.fields) {
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                if (!typedField.hasValue(prefs)) continue
                val value = if (typedField.isExplicitNull(prefs)) null else typedField.read(prefs)
                // Corrupt values (present, not explicit null, decode null) are
                // skipped rather than failing the whole export; absence of the
                // key in the backup means "leave current value" on import.
                if (value == null && !typedField.isExplicitNull(prefs)) continue
                try {
                    val encoded = typedField.encodeValue(value) ?: continue
                    settingsMap[field.keyName] = encoded
                } catch (e: Exception) {
                    failed.add(field.keyName)
                }
            }

            if (failed.isNotEmpty()) {
                return ExportResult.Error("Failed to encode: ${failed.joinToString()}")
            }

            val bundle = SettingsBundle(
                formatVersion = SettingsBundle.CURRENT_FORMAT_VERSION,
                schemaVersion = schemaVersion,
                appId = appId,
                exportedAt = Clock.System.now().toEpochMilliseconds(),
                deviceInfo = deviceInfoProvider?.invoke(),
                settings = settingsMap,
                checksum = calculateChecksum(settingsMap),
            )

            ExportResult.Success(json.encodeToString(bundle))
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Export failed")
        }
    }

    /**
     * Import settings from a JSON backup.
     * mergeMode is honored: OVERWRITE writes everything known, KEEP_EXISTING
     * skips keys already present, UPDATE_ONLY skips keys not already present.
     * Unknown keys are quarantined under a namespaced key so they can never
     * collide (DataStore keys compare by name only) with a future typed field.
     * Explicit nulls in the backup clear the stored value.
     */
    suspend fun import(jsonString: String, options: ImportOptions = ImportOptions()): ImportResult {
        return try {
            val bundle = json.decodeFromString<SettingsBundle>(jsonString)

            if (bundle.formatVersion > SettingsBundle.CURRENT_FORMAT_VERSION) {
                return ImportResult.Error(
                    ImportError.VERSION_TOO_NEW,
                    "Unsupported backup format ${bundle.formatVersion}",
                )
            }

            if (options.validateAppId && bundle.appId != appId) {
                return ImportResult.Error(ImportError.APP_MISMATCH, "Settings are from a different app: ${bundle.appId}")
            }

            if (options.validateChecksum) {
                val expectedChecksum = calculateChecksum(bundle.settings)
                if (bundle.checksum != expectedChecksum) {
                    return ImportResult.Error(ImportError.CHECKSUM_MISMATCH, "Settings file may be corrupted")
                }
            }

            if (bundle.schemaVersion > schemaVersion) {
                return ImportResult.Error(ImportError.VERSION_TOO_NEW, "Settings are from a newer app version (schema ${bundle.schemaVersion})")
            }

            val applied = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            val errors = mutableListOf<Pair<String, String>>()

            dataStore.edit { prefs ->
                for ((keyName, encodedValue) in bundle.settings) {
                    try {
                        val field = schema.fieldByKey(keyName)
                        if (field != null) {
                            @Suppress("UNCHECKED_CAST")
                            val typedField = field as SettingField<T, Any?>
                            when (options.mergeMode) {
                                MergeMode.KEEP_EXISTING if typedField.hasValue(prefs) -> {
                                    skipped.add(keyName)
                                    continue
                                }
                                MergeMode.UPDATE_ONLY if !typedField.hasValue(prefs) -> {
                                    skipped.add(keyName)
                                    continue
                                }
                                else -> {}
                            }
                            try {
                                val decoded = typedField.decodeValue(encodedValue)
                                if (decoded == null && !isExplicitNullPayload(encodedValue)) {
                                    // Unknown enum entries / corrupt payloads decode to
                                    // null without an explicit-null marker: skip, keep current.
                                    skipped.add(keyName)
                                } else {
                                    typedField.write(prefs, decoded)
                                    applied.add(keyName)
                                }
                            } catch (e: Exception) {
                                errors.add(keyName to (e.message ?: "Unknown error"))
                            }
                        } else {
                            // Quarantine: namespaced so a future typed field with the
                            // same base name never throws ClassCastException on get().
                            prefs[stringPreferencesKey(UNKNOWN_KEY_PREFIX + keyName)] = encodedValue
                            applied.add(keyName)
                        }
                    } catch (e: Exception) {
                        errors.add(keyName to (e.message ?: "Unknown error"))
                    }
                }
            }

            ImportResult.Success(applied.size, skipped.size, errors)
        } catch (e: Exception) {
            ImportResult.Error(ImportError.PARSE_ERROR, e.message ?: "Failed to parse settings")
        }
    }

    /**
     * Validate a backup without importing.
     */
    fun validate(jsonString: String): ValidationResult {
        return try {
            val bundle = json.decodeFromString<SettingsBundle>(jsonString)
            val issues = mutableListOf<String>()
            var valid = true

            if (bundle.formatVersion > SettingsBundle.CURRENT_FORMAT_VERSION) {
                issues.add("Unsupported backup format: ${bundle.formatVersion}")
                valid = false
            }

            if (bundle.appId != appId) {
                issues.add("Different app ID: ${bundle.appId}")
                valid = false
            }

            if (bundle.schemaVersion > schemaVersion) {
                issues.add("Newer schema version: ${bundle.schemaVersion} > $schemaVersion")
                valid = false
            }

            val checksum = calculateChecksum(bundle.settings)
            if (checksum != bundle.checksum) {
                issues.add("Checksum mismatch - file may be corrupted")
                valid = false
            }

            // Count known vs unknown keys
            val knownKeys = bundle.settings.keys.count { key ->
                schema.fieldByKey(key) != null
            }
            val unknownKeys = bundle.settings.size - knownKeys
            if (unknownKeys > 0) {
                issues.add("$unknownKeys unknown settings will be quarantined (not applied)")
            }

            ValidationResult(
                isValid = valid,
                settingsCount = bundle.settings.size,
                schemaVersion = bundle.schemaVersion,
                exportedAt = bundle.exportedAt,
                issues = issues,
                deviceInfo = bundle.deviceInfo,
            )
        } catch (e: Exception) {
            ValidationResult(false, 0, 0, 0, listOf("Parse error: ${e.message}"), null)
        }
    }

    /**
     * Export settings for specific fields only.
     */
    suspend fun exportFields(fieldNames: Collection<String>): ExportResult {
        return try {
            val prefs = dataStore.data.first()
            val settingsMap = mutableMapOf<String, String>()
            val failed = mutableListOf<String>()

            for (fieldName in fieldNames) {
                val field = schema.fieldByName(fieldName) ?: continue
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                if (!typedField.hasValue(prefs)) continue
                val value = if (typedField.isExplicitNull(prefs)) null else typedField.read(prefs)
                if (value == null && !typedField.isExplicitNull(prefs)) continue
                try {
                    val encoded = typedField.encodeValue(value) ?: continue
                    settingsMap[field.keyName] = encoded
                } catch (e: Exception) {
                    failed.add(field.keyName)
                }
            }

            if (failed.isNotEmpty()) {
                return ExportResult.Error("Failed to encode: ${failed.joinToString()}")
            }

            val bundle = SettingsBundle(
                formatVersion = SettingsBundle.CURRENT_FORMAT_VERSION,
                schemaVersion = schemaVersion,
                appId = appId,
                exportedAt = Clock.System.now().toEpochMilliseconds(),
                deviceInfo = deviceInfoProvider?.invoke(),
                settings = settingsMap,
                checksum = calculateChecksum(settingsMap),
            )

            ExportResult.Success(json.encodeToString(bundle))
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Export failed")
        }
    }

    /**
     * SHA-256 over the canonical key-sorted encoding. Replaces the previous
     * 32-bit `hashCode()` which collided after ~65k backups (birthday bound)
     * and admitted negative hex strings.
     */
    internal fun calculateChecksum(settings: Map<String, String>): String =
        settings.entries.sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }
            .encodeUtf8().sha256().hex()

    companion object {
        /** Namespace for unknown backup keys; never collides with typed fields. */
        const val UNKNOWN_KEY_PREFIX = "__unknown_backup__:"

        /**
         * Empty payload after the type prefix means explicit null
         * ("b:", "l:", "s:", ...). Non-empty payloads that decode to null are
         * unknown/corrupt values and must be skipped, not applied.
         */
        fun isExplicitNullPayload(encoded: String): Boolean =
            encoded.substringAfter(':', missingDelimiterValue = "") .isEmpty()
    }
}

data class ImportOptions(
    val validateAppId: Boolean = true,
    val validateChecksum: Boolean = true,
    val mergeMode: MergeMode = MergeMode.OVERWRITE,
)

enum class MergeMode {
    /** Overwrite all existing settings */
    OVERWRITE,
    /** Only import settings that don't exist */
    KEEP_EXISTING,
    /** Only update existing settings, don't add new ones */
    UPDATE_ONLY,
}

sealed class ExportResult {
    data class Success(val json: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

sealed class ImportResult {
    data class Success(val appliedCount: Int, val skippedCount: Int, val errors: List<Pair<String, String>>) : ImportResult()
    data class Error(val error: ImportError, val message: String) : ImportResult()
}

enum class ImportError { PARSE_ERROR, APP_MISMATCH, VERSION_TOO_NEW, CHECKSUM_MISMATCH }

data class ValidationResult(
    val isValid: Boolean,
    val settingsCount: Int,
    val schemaVersion: Int,
    val exportedAt: Long,
    val issues: List<String>,
    val deviceInfo: DeviceInfo? = null,
)
