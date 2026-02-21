@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.mlmgames.settings.core.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
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
     * Reads raw preferences to avoid serialization issues with complex types.
     */
    suspend fun export(): ExportResult {
        return try {
            val prefs = dataStore.data.first()
            val settingsMap = mutableMapOf<String, String>()

            // Export all preferences from DataStore directly
            prefs.asMap().forEach { (key, value) ->
                val encoded = encodePreferenceValue(value)
                if (encoded != null) {
                    settingsMap[key.name] = encoded
                }
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
                        val success = writePreferenceValue(prefs, keyName, encodedValue)
                        if (success) {
                            applied.add(keyName)
                        } else {
                            skipped.add(keyName)
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
                val keyName = field.keyName

                // Find the preference value by trying different key types
                val value = findPreferenceValue(prefs, keyName)
                if (value != null) {
                    val encoded = encodePreferenceValue(value)
                    if (encoded != null) {
                        settingsMap[keyName] = encoded
                    }
                }
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
     * Encode a preference value to a type-prefixed string.
     */
    private fun encodePreferenceValue(value: Any?): String? {
        return when (value) {
            is Boolean -> "b:$value"
            is Int -> "i:$value"
            is Long -> "l:$value"
            is Float -> "f:$value"
            is Double -> "d:$value"
            is String -> "s:$value"
            is Set<*> -> {
                // Encode string set as JSON array
                @Suppress("UNCHECKED_CAST")
                val stringSet = value as? Set<String> ?: return null
                "ss:" + json.encodeToString(stringSet.toList())
            }
            null -> null
            else -> null // Unknown types are skipped
        }
    }

    /**
     * Write an encoded value back to preferences.
     */
    private fun writePreferenceValue(prefs: MutablePreferences, keyName: String, encoded: String): Boolean {
        if (encoded.length < 2 || !encoded.contains(':')) {
            return false
        }

        val prefix = encoded.substringBefore(':')
        val data = encoded.substringAfter(':')

        return when (prefix) {
            "b" -> {
                prefs[booleanPreferencesKey(keyName)] = data.toBooleanStrict()
                true
            }
            "i" -> {
                prefs[intPreferencesKey(keyName)] = data.toInt()
                true
            }
            "l" -> {
                prefs[longPreferencesKey(keyName)] = data.toLong()
                true
            }
            "f" -> {
                prefs[floatPreferencesKey(keyName)] = data.toFloat()
                true
            }
            "d" -> {
                prefs[doublePreferencesKey(keyName)] = data.toDouble()
                true
            }
            "s" -> {
                prefs[stringPreferencesKey(keyName)] = data
                true
            }
            "ss" -> {
                val list = json.decodeFromString<List<String>>(data)
                prefs[stringSetPreferencesKey(keyName)] = list.toSet()
                true
            }
            else -> false
        }
    }

    /**
     * Find a preference value by key name, trying all possible key types.
     */
    private fun findPreferenceValue(prefs: Preferences, keyName: String): Any? {
        // Try each preference type
        prefs[booleanPreferencesKey(keyName)]?.let { return it }
        prefs[intPreferencesKey(keyName)]?.let { return it }
        prefs[longPreferencesKey(keyName)]?.let { return it }
        prefs[floatPreferencesKey(keyName)]?.let { return it }
        prefs[doublePreferencesKey(keyName)]?.let { return it }
        prefs[stringPreferencesKey(keyName)]?.let { return it }
        prefs[stringSetPreferencesKey(keyName)]?.let { return it }
        return null
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