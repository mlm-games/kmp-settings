@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.collections.iterator
import kotlin.reflect.KClass

class ResetManager<T>(
    private val dataStore: DataStore<Preferences>,
    private val schema: SettingsSchema<T>,
) {
    suspend fun resetField(fieldName: String): Boolean {
        val field = schema.fieldByName(fieldName) ?: return false
        if (field.meta?.noReset == true) return false

        @Suppress("UNCHECKED_CAST")
        val typedField = field as SettingField<T, Any?>
        if (!typedField.isResettable) return false
        val defaultValue = typedField.get(schema.default)

        dataStore.edit { prefs -> typedField.write(prefs, defaultValue) }
        return true
    }

    /**
     * Resets each field in its own transaction with per-field error isolation:
     * a schema type change mid-list no longer aborts the remaining resets, and
     * the returned count reflects fields actually written. `noReset` and
     * non-persisted placeholders are skipped (counted as 0).
     */
    suspend fun resetFields(fieldNames: Collection<String>): Int {
        var count = 0

        for (fieldName in fieldNames) {
            val field = schema.fieldByName(fieldName) ?: continue
            if (field.meta?.noReset == true) continue

            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            if (!typedField.isResettable) continue
            val defaultValue = typedField.get(schema.default)

            try {
                dataStore.edit { prefs -> typedField.write(prefs, defaultValue) }
                count++
            } catch (e: Exception) {
                continue
            }
        }

        return count
    }

    suspend fun resetCategory(categoryClass: KClass<*>): Int {
        val categoryFields = schema.resettableFieldsInCategory(categoryClass)
        return resetFields(categoryFields.map { it.name })
    }

    suspend fun resetUISettings(): Int {
        val uiFields = schema.uiFields().filter { it.meta?.noReset != true }
        return resetFields(uiFields.map { it.name })
    }

    /**
     * Resets persisted fields (excludes non-persisted Button/Unit placeholders
     * and `noReset` fields). Previously included `@Persisted`-only fields while
     * category/UI resets excluded them; now every path uses the same
     * [SettingsSchema.resettableFields] predicate.
     */
    suspend fun resetAll(): Int {
        val resettable = schema.resettableFields()
        return resetFields(resettable.map { it.name })
    }

    /**
     * Snapshots distinguish explicit null from absent: [SettingsSnapshot.values]
     * holds [SnapshotValue.Present] / [SnapshotValue.ExplicitNull]; absent keys
     * are recorded as [SnapshotValue.Absent] so restore can clear them.
     */
    suspend fun createSnapshot(): SettingsSnapshot {
        val prefs = dataStore.data.first()
        val values = mutableMapOf<String, SnapshotValue<Any?>>()

        for (field in schema.fields) {
            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            values[field.name] = when {
                !typedField.hasValue(prefs) -> SnapshotValue.Absent
                typedField.isExplicitNull(prefs) -> SnapshotValue.ExplicitNull
                else -> SnapshotValue.Present(typedField.read(prefs))
            }
        }

        return SettingsSnapshot(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            values = values
        )
    }

    /**
     * Restores with the same per-field isolation as [resetFields]: unknown
     * fields and write failures are skipped, explicit nulls are re-applied,
     * absent entries clear the key. Returns counts instead of throwing midway.
     */
    suspend fun restoreSnapshot(snapshot: SettingsSnapshot): RestoreResult {
        var restored = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        for ((fieldName, snapshotValue) in snapshot.values) {
            val field = schema.fieldByName(fieldName) ?: run { skipped++; continue }

            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            try {
                dataStore.edit { prefs ->
                    when (snapshotValue) {
                        is SnapshotValue.Absent -> typedField.clear(prefs)
                        is SnapshotValue.ExplicitNull -> typedField.write(prefs, null)
                        is SnapshotValue.Present<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            typedField.write(prefs, snapshotValue.value as Any?)
                        }
                    }
                }
                restored++
            } catch (e: Exception) {
                errors.add(fieldName)
            }
        }

        return RestoreResult(restored, skipped, errors)
    }
}

/** Presence-aware snapshot entry: absent, explicit null, or a concrete value. */
sealed interface SnapshotValue<out V> {
    data object Absent : SnapshotValue<Nothing>
    data object ExplicitNull : SnapshotValue<Nothing>
    data class Present<V>(val value: V) : SnapshotValue<V>
}

data class SettingsSnapshot(
    val timestamp: Long,
    val values: Map<String, SnapshotValue<Any?>>,
)

data class RestoreResult(
    val restoredCount: Int,
    val skippedCount: Int,
    val errors: List<String>,
)
