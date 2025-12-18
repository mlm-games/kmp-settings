package io.github.mlmgames.settings.core.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
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

    suspend fun export(): ExportResult {
        return try {
            val prefs = dataStore.data.first()
            val settingsMap = mutableMapOf<String, String>()

            for (field in schema.fields) {
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                val value = typedField.read(prefs)
                if (value != null) {
                    settingsMap[field.keyName] = encodeValue(value)
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

    suspend fun import(jsonString: String, options: ImportOptions = ImportOptions()): ImportResult {
        return try {
            val bundle = json.decodeFromString<SettingsBundle>(jsonString)

            if (options.validateAppId && bundle.appId != appId) {
                return ImportResult.Error(ImportError.APP_MISMATCH, "Settings are from a different app")
            }

            if (options.validateChecksum) {
                val expectedChecksum = calculateChecksum(bundle.settings)
                if (bundle.checksum != expectedChecksum) {
                    return ImportResult.Error(ImportError.CHECKSUM_MISMATCH, "Settings file may be corrupted")
                }
            }

            if (bundle.schemaVersion > schemaVersion) {
                return ImportResult.Error(ImportError.VERSION_TOO_NEW, "Settings are from a newer app version")
            }

            val applied = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            val errors = mutableListOf<Pair<String, String>>()

            dataStore.edit { prefs ->
                for ((key, encodedValue) in bundle.settings) {
                    val field = schema.fieldByKey(key)

                    if (field == null) {
                        skipped.add(key)
                        continue
                    }

                    try {
                        @Suppress("UNCHECKED_CAST")
                        val typedField = field as SettingField<T, Any?>
                        val value = decodeValue(encodedValue)

                        if (value != null) {
                            typedField.write(prefs, value)
                            applied.add(key)
                        } else {
                            skipped.add(key)
                        }
                    } catch (e: Exception) {
                        errors.add(key to (e.message ?: "Unknown error"))
                    }
                }
            }

            ImportResult.Success(applied.size, skipped.size, errors)
        } catch (e: Exception) {
            ImportResult.Error(ImportError.PARSE_ERROR, e.message ?: "Failed to parse settings")
        }
    }

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

            val unknownKeys = bundle.settings.keys.filter { key ->
                schema.fieldByKey(key) == null
            }
            if (unknownKeys.isNotEmpty()) {
                issues.add("Unknown settings: ${unknownKeys.joinToString()}")
            }

            val checksum = calculateChecksum(bundle.settings)
            if (checksum != bundle.checksum) {
                issues.add("Checksum mismatch - file may be corrupted")
            }

            ValidationResult(
                isValid = issues.isEmpty(),
                settingsCount = bundle.settings.size,
                schemaVersion = bundle.schemaVersion,
                exportedAt = bundle.exportedAt,
                issues = issues,
            )
        } catch (e: Exception) {
            ValidationResult(false, 0, 0, 0, listOf("Parse error: ${e.message}"))
        }
    }

    private fun encodeValue(value: Any): String = when (value) {
        is String -> "s:$value"
        is Boolean -> "b:$value"
        is Int -> "i:$value"
        is Long -> "l:$value"
        is Float -> "f:$value"
        is Double -> "d:$value"
        is Set<*> -> "ss:" + (value.filterIsInstance<String>()).joinToString("\u0000")
        else -> "j:" + json.encodeToString(value)
    }

    private fun decodeValue(encoded: String): Any? {
        val prefix = encoded.substringBefore(":")
        val data = encoded.substringAfter(":")

        return when (prefix) {
            "s" -> data
            "b" -> data.toBooleanStrictOrNull()
            "i" -> data.toIntOrNull()
            "l" -> data.toLongOrNull()
            "f" -> data.toFloatOrNull()
            "d" -> data.toDoubleOrNull()
            "ss" -> if (data.isEmpty()) emptySet() else data.split("\u0000").toSet()
            else -> null
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
    val skipUnknownFields: Boolean = true,
)

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
)