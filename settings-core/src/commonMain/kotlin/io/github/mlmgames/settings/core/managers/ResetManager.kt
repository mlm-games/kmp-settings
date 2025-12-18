package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlin.collections.iterator
import kotlin.reflect.KClass

class ResetManager<T>(
    private val dataStore: DataStore<Preferences>,
    private val schema: SettingsSchema<T>,
) {
    suspend fun resetField(fieldName: String): Boolean {
        val field = schema.fieldByName(fieldName) ?: return false

        @Suppress("UNCHECKED_CAST")
        val typedField = field as SettingField<T, Any?>
        val defaultValue = typedField.get(schema.default)

        dataStore.edit { prefs -> typedField.write(prefs, defaultValue) }
        return true
    }

    suspend fun resetFields(fieldNames: Collection<String>): Int {
        var count = 0

        dataStore.edit { prefs ->
            for (fieldName in fieldNames) {
                val field = schema.fieldByName(fieldName) ?: continue

                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                val defaultValue = typedField.get(schema.default)

                typedField.write(prefs, defaultValue)
                count++
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

    suspend fun resetAll(): Int {
        val resettable = schema.resettableFields()
        return resetFields(resettable.map { it.name })
    }

    suspend fun createSnapshot(): SettingsSnapshot {
        val prefs = dataStore.data.first()
        val values = mutableMapOf<String, Any?>()

        for (field in schema.fields) {
            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            values[field.name] = typedField.read(prefs)
        }

        return SettingsSnapshot(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            values = values
        )
    }

    suspend fun restoreSnapshot(snapshot: SettingsSnapshot) {
        dataStore.edit { prefs ->
            for ((fieldName, value) in snapshot.values) {
                if (value == null) continue
                val field = schema.fieldByName(fieldName) ?: continue

                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                typedField.write(prefs, value)
            }
        }
    }
}

data class SettingsSnapshot(
    val timestamp: Long,
    val values: Map<String, Any?>,
)