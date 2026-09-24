@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.mlmgames.settings.core.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class SettingsBackupManager<T>(
    private val dataStore: DataStore<Preferences>,
    private val schema: SettingsSchema<T>,
    private val appId: String,
    private val schemaVersion: Int = schema.schemaVersion,
    private val deviceInfoProvider: (() -> DeviceInfo)? = null,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun export(): ExportResult {
        return try {
            val preferences = dataStore.data.first()
            val quarantined = linkedMapOf<String, String>()
            val settings = readSettings(preferences, schema.fields, quarantined)
            quarantined.putAll(readQuarantinedSettings(preferences))
            ExportResult.Success(json.encodeToString(createBundle(settings, quarantined)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Export failed")
        }
    }

    suspend fun import(
        jsonString: String,
        options: ImportOptions = ImportOptions(),
    ): ImportResult {
        return try {
            val bundle = json.decodeFromString<SettingsBundle>(jsonString)

            if (bundle.formatVersion !in SettingsBundle.LEGACY_FORMAT_VERSION..SettingsBundle.CURRENT_FORMAT_VERSION) {
                return ImportResult.Error(
                    ImportError.VERSION_TOO_NEW,
                    "Unsupported backup format ${bundle.formatVersion}",
                )
            }
            if (bundle.schemaVersion < 0) {
                return ImportResult.Error(ImportError.PARSE_ERROR, "Invalid schema version ${bundle.schemaVersion}")
            }
            if (options.validateAppId && bundle.appId != appId) {
                return ImportResult.Error(
                    ImportError.APP_MISMATCH,
                    "Settings are from a different app: ${bundle.appId}",
                )
            }
            if (options.validateChecksum && !checksumMatches(bundle)) {
                return ImportResult.Error(ImportError.CHECKSUM_MISMATCH, "Settings file may be corrupted")
            }
            if (bundle.schemaVersion > schemaVersion) {
                return ImportResult.Error(
                    ImportError.VERSION_TOO_NEW,
                    "Settings are from a newer app version (schema ${bundle.schemaVersion})",
                )
            }

            data class KnownPlan(
                val keyName: String,
                val field: SettingField<*, *>,
                val value: Any?,
            )

            val knownPlans = mutableListOf<KnownPlan>()
            val unknownPlans = mutableListOf<Pair<String, String>>()
            val skipped = mutableListOf<String>()
            val errors = mutableListOf<Pair<String, String>>()

            val incomingSettings = mergeIncomingSettings(bundle)
            for ((keyName, encodedValue) in incomingSettings) {
                val field = schema.fieldByKey(keyName)
                if (field == null) {
                    unknownPlans += keyName.removePrefix(UNKNOWN_KEY_PREFIX) to encodedValue
                    continue
                }

                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                when (val decoded = decodeBackupValue(typedField, encodedValue)) {
                    is BackupDecodeResult.Present -> knownPlans += KnownPlan(keyName, typedField, decoded.value)
                    BackupDecodeResult.ExplicitNull -> knownPlans += KnownPlan(keyName, typedField, null)
                    BackupDecodeResult.Skipped -> skipped += keyName
                    is BackupDecodeResult.Invalid -> {
                        errors += keyName to decoded.message
                        unknownPlans += keyName.removePrefix(UNKNOWN_KEY_PREFIX) to encodedValue
                    }
                }
            }

            var appliedCount = 0
            var skippedCount = skipped.size

            try {
                dataStore.edit { prefs ->
                    val applied = mutableListOf<String>()
                    val transactionSkipped = skipped.toMutableList()

                    for (plan in knownPlans) {
                        @Suppress("UNCHECKED_CAST")
                        val typedField = plan.field as SettingField<T, Any?>
                        if (shouldSkipImport(typedField, prefs, options.mergeMode)) {
                            transactionSkipped += plan.keyName
                            continue
                        }
                        try {
                            typedField.write(prefs, plan.value)
                            prefs.remove(stringPreferencesKey(UNKNOWN_KEY_PREFIX + plan.keyName))
                            applied += plan.keyName
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw ImportWriteException(plan.keyName, e)
                        }
                    }

                    for ((keyName, encodedValue) in unknownPlans) {
                        val quarantineKey = stringPreferencesKey(UNKNOWN_KEY_PREFIX + keyName)
                        if (shouldSkipImport(prefs, quarantineKey, options.mergeMode)) {
                            transactionSkipped += keyName
                            continue
                        }
                        prefs[quarantineKey] = encodedValue
                        applied += keyName
                    }

                    appliedCount = applied.size
                    skippedCount = transactionSkipped.size
                }
            } catch (e: ImportWriteException) {
                return ImportResult.Error(
                    ImportError.PARSE_ERROR,
                    "Failed to write settings: ${e.message}",
                )
            }

            ImportResult.Success(appliedCount, skippedCount, errors.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportResult.Error(ImportError.PARSE_ERROR, e.message ?: "Failed to parse settings")
        }
    }

    fun validate(jsonString: String): ValidationResult {
        return try {
            val bundle = json.decodeFromString<SettingsBundle>(jsonString)
            val issues = mutableListOf<String>()
            var valid = true

            if (bundle.formatVersion !in SettingsBundle.LEGACY_FORMAT_VERSION..SettingsBundle.CURRENT_FORMAT_VERSION) {
                issues.add("Unsupported backup format: ${bundle.formatVersion}")
                valid = false
            }
            if (bundle.appId != appId) {
                issues.add("Different app ID: ${bundle.appId}")
                valid = false
            }
            if (bundle.schemaVersion < 0) {
                issues.add("Invalid schema version: ${bundle.schemaVersion}")
                valid = false
            } else if (bundle.schemaVersion > schemaVersion) {
                issues.add("Newer schema version: ${bundle.schemaVersion} > $schemaVersion")
                valid = false
            }
            if (!checksumMatches(bundle)) {
                issues.add("Checksum mismatch - file may be corrupted")
                valid = false
            }

            var unknownKeys = 0
            val incomingSettings = mergeIncomingSettings(bundle)
            for ((keyName, encodedValue) in incomingSettings) {
                val field = schema.fieldByKey(keyName)
                if (field == null) {
                    unknownKeys++
                    continue
                }
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                when (val decoded = decodeBackupValue(typedField, encodedValue)) {
                    BackupDecodeResult.ExplicitNull,
                    is BackupDecodeResult.Present,
                    -> Unit
                    BackupDecodeResult.Skipped -> {
                        issues.add("Invalid value for setting: $keyName")
                        valid = false
                    }
                    is BackupDecodeResult.Invalid -> {
                        issues.add("Invalid value for setting $keyName: ${decoded.message}")
                        valid = false
                    }
                }
            }
            if (unknownKeys > 0) {
                issues.add("$unknownKeys unknown settings will be preserved in quarantine")
            }

            ValidationResult(
                isValid = valid,
                settingsCount = incomingSettings.size,
                schemaVersion = bundle.schemaVersion,
                exportedAt = bundle.exportedAt,
                issues = issues,
                deviceInfo = bundle.deviceInfo,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ValidationResult(false, 0, 0, 0, listOf("Parse error: ${e.message}"), null)
        }
    }

    suspend fun exportFields(fieldNames: Collection<String>): ExportResult {
        return try {
            val fields = fieldNames.mapNotNull { schema.fieldByName(it) }
            val quarantined = linkedMapOf<String, String>()
            val settings = readSettings(dataStore.data.first(), fields, quarantined)
            ExportResult.Success(json.encodeToString(createBundle(settings, quarantined)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Export failed")
        }
    }

    internal fun calculateChecksum(
        settings: Map<String, String>,
        quarantinedSettings: Map<String, String> = emptyMap(),
    ): String = canonicalV2(settings, quarantinedSettings).encodeUtf8().sha256().hex()

    internal fun calculateLegacyChecksum(settings: Map<String, String>): String =
        canonicalV1(settings).hashCode().toString(16)

    private fun createBundle(
        settings: Map<String, String>,
        quarantinedSettings: Map<String, String> = emptyMap(),
    ): SettingsBundle = SettingsBundle(
        formatVersion = SettingsBundle.CURRENT_FORMAT_VERSION,
        schemaVersion = schemaVersion,
        appId = appId,
        exportedAt = Clock.System.now().toEpochMilliseconds(),
        deviceInfo = deviceInfoProvider?.invoke(),
        settings = settings,
        checksum = calculateChecksum(settings, quarantinedSettings),
        quarantinedSettings = quarantinedSettings,
    )

    private fun readQuarantinedSettings(prefs: Preferences): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val knownPhysicalKeys = schema.fields
            .flatMap { it.physicalKeys }
            .mapTo(hashSetOf()) { it.name }
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (name in INTERNAL_KEYS || name in knownPhysicalKeys) continue
            val encoded = value as? String ?: continue
            val original = if (name.startsWith(UNKNOWN_KEY_PREFIX)) {
                name.removePrefix(UNKNOWN_KEY_PREFIX)
            } else {
                name
            }
            if (original.isBlank() || original in knownPhysicalKeys) continue
            result[UNKNOWN_KEY_PREFIX + original] = encoded
        }
        return result
    }

    private fun readSettings(
        prefs: Preferences,
        fields: List<SettingField<T, *>>,
        quarantined: MutableMap<String, String> = linkedMapOf(),
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val failed = mutableListOf<String>()

        for (field in fields) {
            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            try {
                if (!typedField.hasValue(prefs)) continue
                if (!typedField.isStoredValueValid(prefs)) {
                    val raw = typedField.rawStoredValue(prefs)
                    if (raw != null) quarantined[field.keyName] = raw else failed += field.keyName
                    continue
                }
                val explicitNull = isStoredExplicitNull(typedField, prefs)
                val value = if (explicitNull) null else typedField.read(prefs)
                if (!explicitNull && value == null) {
                    val raw = typedField.rawStoredValue(prefs)
                    if (raw != null) {
                        quarantined[field.keyName] = raw
                    } else {
                        failed += field.keyName
                    }
                    continue
                }
                val encoded = if (explicitNull) {
                    NULL_PAYLOAD
                } else {
                    typedField.encodeValue(value)
                        ?: throw IllegalStateException("Field returned no backup value")
                }
                result[field.keyName] = encoded
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += field.keyName
            }
        }

        if (failed.isNotEmpty()) {
            throw ExportFieldException(failed.distinct())
        }
        return result
    }

    private fun decodeBackupValue(
        field: SettingField<T, Any?>,
        encoded: String,
    ): BackupDecodeResult {
        return try {
            if (isNullableStringPayload(field, encoded)) {
                if (isLegacyNullPayload(field, encoded)) return BackupDecodeResult.ExplicitNull
                val stringValue = field.decodeValue(encoded)
                return if (stringValue == null) {
                    BackupDecodeResult.Skipped
                } else {
                    BackupDecodeResult.Present(stringValue)
                }
            }
            val explicitNull = isExplicitNullPayload(field, encoded)
            if (explicitNull) {
                if (isNullableField(field)) return BackupDecodeResult.ExplicitNull
                val value = field.decodeValue(encoded)
                return if (value == null) BackupDecodeResult.ExplicitNull
                else BackupDecodeResult.Present(value)
            }
            val value = field.decodeValue(encoded)
            if (value == null) {
                if (explicitNull) BackupDecodeResult.Invalid("null is not valid for this field")
                else BackupDecodeResult.Skipped
            } else {
                BackupDecodeResult.Present(value)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupDecodeResult.Invalid(e.message ?: "Invalid value")
        }
    }

    private fun isStoredExplicitNull(
        field: SettingField<*, *>,
        prefs: Preferences,
    ): Boolean {
        if (field.isExplicitNull(prefs)) return true
        return field.isNullableString &&
            prefs[stringPreferencesKey("${field.keyName}_nullable")] == LEGACY_NULL_MARKER
    }

    private fun isExplicitNullPayload(
        field: SettingField<*, *>,
        encoded: String,
    ): Boolean {
        if (isNullableStringPayload(field, encoded) && !isLegacyNullPayload(field, encoded)) return false
        val separator = encoded.indexOf(':')
        if (separator > 0 && encoded.substring(0, separator) == "n" &&
            encoded.substring(separator + 1).isEmpty()
        ) return true
        if (!isNullableField(field)) return false
        return isEmptyValuePayload(encoded) || isLegacyNullPayload(field, encoded)
    }

    private fun isNullableStringPayload(
        field: SettingField<*, *>,
        encoded: String,
    ): Boolean {
        if (!field.isNullableString) return false
        val separator = encoded.indexOf(':')
        if (separator <= 0) return false
        val prefix = encoded.substring(0, separator)
        return prefix == "s" || prefix == "s1"
    }

    private fun isLegacyNullPayload(
        field: SettingField<*, *>,
        encoded: String,
    ): Boolean {
        if (!isNullableField(field)) return false
        val separator = encoded.indexOf(':')
        if (separator <= 0) return false
        val prefix = encoded.substring(0, separator)
        val payload = encoded.substring(separator + 1)
        return when (prefix) {
            "i" -> payload == Long.MIN_VALUE.toString()
            "s" -> payload == LEGACY_NULL_MARKER
            "n", "j" -> payload == NULL_MARKER
            "b" -> payload == NULL_MARKER
            "l" -> payload == NULL_MARKER
            else -> false
        }
    }

    private fun shouldSkipImport(
        field: SettingField<T, Any?>,
        prefs: Preferences,
        mergeMode: MergeMode,
    ): Boolean = when (mergeMode) {
        MergeMode.KEEP_EXISTING -> field.hasValue(prefs)
        MergeMode.UPDATE_ONLY -> !field.hasValue(prefs)
        MergeMode.OVERWRITE -> false
    }

    private fun shouldSkipImport(
        prefs: MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<*>,
        mergeMode: MergeMode,
    ): Boolean = when (mergeMode) {
        MergeMode.KEEP_EXISTING -> key in prefs
        MergeMode.UPDATE_ONLY -> key !in prefs
        MergeMode.OVERWRITE -> false
    }

    private fun mergeIncomingSettings(bundle: SettingsBundle): Map<String, String> {
        val result = linkedMapOf<String, String>()
        bundle.settings.forEach { (key, value) ->
            val normalized = key.removePrefix(UNKNOWN_KEY_PREFIX)
            if (normalized.isNotEmpty()) result[normalized] = value
        }
        bundle.quarantinedSettings.forEach { (key, value) ->
            val normalized = key.removePrefix(UNKNOWN_KEY_PREFIX)
            if (normalized.isNotEmpty() && normalized !in result) result[normalized] = value
        }
        return result
    }

    private fun checksumMatches(bundle: SettingsBundle): Boolean {
        val expected = when {
            bundle.formatVersion == SettingsBundle.CURRENT_FORMAT_VERSION ->
                setOf(calculateChecksum(bundle.settings, bundle.quarantinedSettings))
            bundle.quarantinedSettings.isNotEmpty() ->
                setOf(calculateChecksum(bundle.settings, bundle.quarantinedSettings))
            bundle.formatVersion == SettingsBundle.LEGACY_FORMAT_VERSION ||
                bundle.formatVersion == SettingsBundle.LEGACY_HASH_FORMAT_VERSION ->
                setOf(
                    calculateLegacyChecksum(bundle.settings),
                    calculateLegacySha256Checksum(bundle.settings),
                    calculateChecksum(bundle.settings),
                )
            else -> emptySet()
        }
        return expected.any { value ->
            bundle.checksum == value || bundle.checksum == "$SHA256_PREFIX$value"
        }
    }

    private fun calculateLegacySha256Checksum(settings: Map<String, String>): String =
        canonicalV1(settings).encodeUtf8().sha256().hex()

    private fun canonicalV1(settings: Map<String, String>): String =
        settings.entries.sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }

    private fun canonicalV2(
        settings: Map<String, String>,
        quarantinedSettings: Map<String, String> = emptyMap(),
    ): String = buildString {
        append("kmp-settings:backup:v2\n")
        append("settings\n")
        appendCanonicalEntries(settings)
        append("quarantined\n")
        appendCanonicalEntries(quarantinedSettings)
    }

    private fun StringBuilder.appendCanonicalEntries(entries: Map<String, String>) {
        for (entry in entries.entries.sortedBy { it.key }) {
            append(entry.key.encodeUtf8().size)
            append(':')
            append(entry.key)
            append(entry.value.encodeUtf8().size)
            append(':')
            append(entry.value)
            append('\n')
        }
    }

    private fun isEmptyValuePayload(encoded: String): Boolean {
        val separator = encoded.indexOf(':')
        if (separator <= 0) return false
        val prefix = encoded.substring(0, separator)
        return prefix in VALUE_PREFIXES && encoded.substring(separator + 1).isEmpty()
    }

    private fun isNullableField(field: SettingField<*, *>): Boolean = field.supportsExplicitNull

    private class ImportWriteException(
        keyName: String,
        cause: Throwable,
    ) : Exception("Failed to write $keyName: ${cause.message}", cause)

    private class ExportFieldException(fields: List<String>) :
        Exception("Failed to encode: ${fields.joinToString()}")

    companion object {
        const val UNKNOWN_KEY_PREFIX = "__unknown_backup__:"
        const val NULL_PAYLOAD = "n:"
        const val SHA256_PREFIX = "sha256:"
        const val NULL_MARKER = "__NULL__"
        const val LEGACY_NULL_MARKER = "\u0000__NULL__\u0000"

        private val INTERNAL_KEYS = setOf(
            "__schema_version__",
            "__settings_lock_enabled__",
            "__settings_pin_hash__",
            "__kmp_settings_v2__:pin_hash",
            "__settings_lock_timeout__",
            "__settings_last_unlock__",
        )

        private val VALUE_PREFIXES = setOf(
            "b", "i", "l", "f", "d", "s", "j", "ss", "n",
            "b1", "i1", "l1", "f1", "d1", "s1", "e", "eo", "j1", "ls", "li", "ll", "m",
        )

        fun isExplicitNullPayload(encoded: String): Boolean {
            val separator = encoded.indexOf(':')
            if (separator <= 0) return false
            val prefix = encoded.substring(0, separator)
            return prefix in VALUE_PREFIXES && encoded.substring(separator + 1).isEmpty()
        }
    }
}

private sealed interface BackupDecodeResult {
    data class Present(val value: Any?) : BackupDecodeResult
    data object ExplicitNull : BackupDecodeResult
    data object Skipped : BackupDecodeResult
    data class Invalid(val message: String) : BackupDecodeResult
}

data class ImportOptions(
    val validateAppId: Boolean = true,
    val validateChecksum: Boolean = true,
    val mergeMode: MergeMode = MergeMode.OVERWRITE,
)

enum class MergeMode {
    OVERWRITE,
    KEEP_EXISTING,
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
