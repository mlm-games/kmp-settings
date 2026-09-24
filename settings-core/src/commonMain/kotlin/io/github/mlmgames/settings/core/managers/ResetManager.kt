@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import io.github.mlmgames.settings.core.annotations.SettingPlatform
import io.github.mlmgames.settings.core.platform.currentPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.reflect.KClass
import kotlin.time.Clock

class ResetManager<T>(
    private val dataStore: DataStore<Preferences>,
    private val schema: SettingsSchema<T>,
) {
    suspend fun resetField(fieldName: String): Boolean {
        val operations = resetOperations(listOf(fieldName))
        if (operations.isEmpty()) return false
        return resetOperation(operations.single())
    }

    suspend fun resetFields(fieldNames: Collection<String>): Int {
        val operations = resetOperations(fieldNames)
        var reset = 0
        for (operation in operations) {
            if (resetOperation(operation)) reset++
        }
        return reset
    }

    private suspend fun resetOperation(operation: ResetOperation<T>): Boolean {
        return try {
            dataStore.edit { prefs ->
                check(operation.field.writeNullable(prefs, operation.defaultValue)) {
                    "Field ${operation.field.name} rejected its reset value"
                }
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun resetCategory(categoryClass: KClass<*>): Int = resetCategory(categoryClass, currentPlatform)

    suspend fun resetCategory(
        categoryClass: KClass<*>,
        platform: SettingPlatform = currentPlatform,
    ): Int {
        val fields = schema.visibleUiFields(platform).filter {
            it.meta?.category == categoryClass && it.isPersisted && it.isResettable && it.meta?.noReset != true
        }
        return resetFields(fields.map { it.name })
    }

    suspend fun resetUISettings(): Int = resetUISettings(currentPlatform)

    suspend fun resetUISettings(platform: SettingPlatform = currentPlatform): Int {
        val fields = schema.visibleUiFields(platform).filter { it.isPersisted && it.isResettable && it.meta?.noReset != true }
        return resetFields(fields.map { it.name })
    }

    suspend fun resetAll(): Int = resetFields(schema.resettableFields().map { it.name })

    suspend fun createSnapshot(): SettingsSnapshot {
        val prefs = dataStore.data.first()
        val values = linkedMapOf<String, SnapshotValue<Any?>>()
        val corruptFields = linkedSetOf<String>()

        for (field in schema.fields) {
            if (!field.isPersisted) continue

            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            val value = try {
                when {
                    !typedField.hasValue(prefs) -> SnapshotValue.Absent
                    typedField.isExplicitNull(prefs) -> SnapshotValue.ExplicitNull
                    else -> {
                        val read = typedField.read(prefs)
                        if (read == null) {
                            corruptFields += field.name
                            SnapshotValue.Absent
                        } else {
                            SnapshotValue.Present(read)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                corruptFields += field.name
                SnapshotValue.Absent
            }
            values[field.name] = value
        }

        return SettingsSnapshot(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            values = values.toMap(),
            corruptFields = corruptFields.toSet(),
        )
    }

    suspend fun restoreSnapshot(snapshot: SettingsSnapshot): RestoreResult {
        val operations = mutableListOf<RestoreOperation<T>>()
        val errors = mutableListOf<String>()
        var skipped = 0

        for ((fieldName, snapshotValue) in snapshot.snapshotValues) {
            val field = schema.fieldByName(fieldName)
            if (field == null) {
                skipped++
                continue
            }
            if (!field.isPersisted) {
                skipped++
                continue
            }
            if (fieldName in snapshot.corruptFields) {
                errors += "$fieldName: corrupt value"
                skipped++
                continue
            }

            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            val action = when (snapshotValue) {
                SnapshotValue.Absent -> RestoreAction.Clear
                SnapshotValue.ExplicitNull -> {
                    if (typedField.supportsExplicitNull) RestoreAction.Null
                    else {
                        errors += "$fieldName: field does not support explicit null"
                        skipped++
                        continue
                    }
                }
                is SnapshotValue.Present -> {
                    val value = snapshotValue.value
                    when {
                        value == null && typedField.supportsExplicitNull -> RestoreAction.Null
                        value == null -> {
                            errors += "$fieldName: null is not valid for this field"
                            skipped++
                            continue
                        }
                        else -> RestoreAction.Value(value)
                    }
                }
            }

            try {
                validateRestoreAction(typedField, action)
                operations += RestoreOperation(typedField, action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "$fieldName: ${e.message ?: "invalid snapshot value"}"
                skipped++
            }
        }

        if (operations.isNotEmpty()) {
            try {
                dataStore.edit { prefs ->
                    operations.forEach { operation ->
                        applyRestoreAction(operation.field, prefs, operation.action)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "transaction: ${e.message ?: "restore failed"}"
                return RestoreResult(
                    restoredCount = 0,
                    skippedCount = skipped + operations.size,
                    errors = errors.toList(),
                )
            }
        }

        return RestoreResult(
            restoredCount = operations.size,
            skippedCount = skipped,
            errors = errors.toList(),
        )
    }

    private fun resetOperations(fieldNames: Collection<String>): List<ResetOperation<T>> {
        val operations = mutableListOf<ResetOperation<T>>()
        for (fieldName in fieldNames.distinct()) {
            val field = schema.fieldByName(fieldName) ?: continue
            if (!field.isPersisted || !field.isResettable || field.meta?.noReset == true) continue

            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            val defaultValue = typedField.get(schema.default)
            if (defaultValue == null && !typedField.supportsExplicitNull) {
                throw IllegalArgumentException("Field $fieldName has a null non-nullable default")
            }
            operations += ResetOperation(typedField, defaultValue)
        }
        return operations
    }

    private fun validateRestoreAction(field: SettingField<T, Any?>, action: RestoreAction) {
        val probe = emptyPreferences().toMutablePreferences()
        applyRestoreAction(field, probe, action)
        when (action) {
            RestoreAction.Clear -> Unit
            RestoreAction.Null -> check(field.isExplicitNull(probe)) {
                "Field ${field.name} did not persist explicit null"
            }
            is RestoreAction.Value -> {
                check(field.hasValue(probe)) { "Field ${field.name} did not persist a value" }
                check(!field.isExplicitNull(probe)) { "Field ${field.name} persisted null" }
                check(equivalent(field.read(probe), action.value)) {
                    "Field ${field.name} did not round-trip its snapshot value"
                }
            }
        }
    }

    private fun applyRestoreAction(
        field: SettingField<T, Any?>,
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        action: RestoreAction,
    ) {
        when (action) {
            RestoreAction.Clear -> field.clear(prefs)
            RestoreAction.Null -> check(field.writeNullable(prefs, null)) {
                "Field ${field.name} does not support explicit null"
            }
            is RestoreAction.Value -> check(field.writeNullable(prefs, action.value)) {
                "Field ${field.name} rejected snapshot value"
            }
        }
    }

    private fun equivalent(first: Any?, second: Any?): Boolean = when {
        first is Float && second is Float -> first == second || first.isNaN() && second.isNaN()
        first is Double && second is Double -> first == second || first.isNaN() && second.isNaN()
        else -> first == second
    }

    private data class ResetOperation<T>(
        val field: SettingField<T, Any?>,
        val defaultValue: Any?,
    )

    private data class RestoreOperation<T>(
        val field: SettingField<T, Any?>,
        val action: RestoreAction,
    )

    private sealed interface RestoreAction {
        data object Clear : RestoreAction
        data object Null : RestoreAction
        data class Value(val value: Any?) : RestoreAction
    }
}

sealed interface SnapshotValue<out V> {
    data object Absent : SnapshotValue<Nothing>
    data object ExplicitNull : SnapshotValue<Nothing>
    data class Present<V>(val value: V) : SnapshotValue<V>
}

data class SettingsSnapshot(
    val timestamp: Long,
    val values: Map<String, Any?>,
) {
    private var snapshotEntries: Map<String, SnapshotValue<Any?>>? = null
    private var snapshotCorruptFields: Set<String> = emptySet()

    val snapshotValues: Map<String, SnapshotValue<Any?>>
        get() = snapshotEntries ?: values.mapValues { (_, value) -> legacySnapshotValue(value) }

    val corruptFields: Set<String>
        get() = snapshotCorruptFields

    constructor(
        timestamp: Long,
        values: Map<String, SnapshotValue<Any?>>,
        corruptFields: Set<String>,
    ) : this(
        timestamp = timestamp,
        values = values.mapValues { (_, value) ->
            when (value) {
                SnapshotValue.Absent, SnapshotValue.ExplicitNull -> value
                is SnapshotValue.Present -> value.value
            }
        },
    ) {
        snapshotEntries = values
        snapshotCorruptFields = corruptFields
    }

    constructor(
        timestamp: Long,
        values: Map<String, Any?>,
        legacy: Boolean,
    ) : this(timestamp, values)

    companion object {
        fun fromLegacy(timestamp: Long, values: Map<String, Any?>): SettingsSnapshot =
            SettingsSnapshot(timestamp, values)
    }
}

private fun legacySnapshotValue(value: Any?): SnapshotValue<Any?> = when (value) {
    null -> SnapshotValue.Absent
    is SnapshotValue<*> -> value
    else -> SnapshotValue.Present(value)
}

data class RestoreResult(
    val restoredCount: Int,
    val skippedCount: Int,
    val errors: List<String>,
)
