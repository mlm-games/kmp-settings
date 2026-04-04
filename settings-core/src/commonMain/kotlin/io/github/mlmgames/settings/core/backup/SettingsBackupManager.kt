@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.mlmgames.settings.core.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
     */
    suspend fun export(): ExportResult {
        return try {
            val prefs = dataStore.data.first()
            val settingsMap = mutableMapOf<String, String>()

            for (field in schema.fields) {
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                val value = typedField.read(prefs) ?: continue
                val encoded = typedField.encodeValue(value) ?: continue
                settingsMap[field.keyName] = encoded
            }

            val bundle = SettingsBundle(
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
     */
    suspend fun import(jsonString: String, options: ImportOptions = ImportOptions()): ImportResult {
        return try {
            val bundle = json.decodeFromString<SettingsBundle>(jsonString)

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
                            val decoded = (field as SettingField<T, Any?>).decodeValue(encodedValue)
                            if (decoded != null) {
                                field.write(prefs, decoded)
                                applied.add(keyName)
                            } else {
                                skipped.add(keyName)
                            }
                        } else {
                            // Unknown key: write as raw string to preserve forward compatibility
                            prefs[androidx.datastore.preferences.core.stringPreferencesKey(keyName)] = encodedValue
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

            if (bundle.appId != appId) {
                issues.add("Different app ID: ${bundle.appId}")
            }

            if (bundle.schemaVersion > schemaVersion) {
                issues.add("Newer schema version: ${bundle.schemaVersion} > $schemaVersion")
            }

            val checksum = calculateChecksum(bundle.settings)
            if (checksum != bundle.checksum) {
                issues.add("Checksum mismatch - file may be corrupted")
            }

            // Count known vs unknown keys
            val knownKeys = bundle.settings.keys.count { key ->
                schema.fieldByKey(key) != null
            }
            val unknownKeys = bundle.settings.size - knownKeys
            if (unknownKeys > 0) {
                issues.add("$unknownKeys unknown settings will be imported anyway")
            }

            ValidationResult(
                isValid = issues.none { it.contains("corrupted") || it.contains("Newer schema") },
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

            for (fieldName in fieldNames) {
                val field = schema.fieldByName(fieldName) ?: continue
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                val value = typedField.read(prefs) ?: continue
                val encoded = typedField.encodeValue(value) ?: continue
                settingsMap[field.keyName] = encoded
            }

            val bundle = SettingsBundle(
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

    private fun calculateChecksum(settings: Map<String, String>): String =
        settings.entries.sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }
            .hashCode().toString(16)
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
