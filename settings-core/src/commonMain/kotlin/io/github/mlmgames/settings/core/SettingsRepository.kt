package io.github.mlmgames.settings.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Listener for setting changes.
 */
fun interface SettingChangeListener<T> {
    suspend fun onChanged(field: SettingField<T, *>, oldValue: Any?, newValue: Any?)
}

/**
 * Listener for specific field changes.
 */
fun interface FieldChangeListener<V> {
    suspend fun onChanged(oldValue: V, newValue: V)
}

/**
 * Repository for reading/writing settings via DataStore.
 */
class SettingsRepository<T>(
    private val dataStore: DataStore<Preferences>,
    val schema: SettingsSchema<T>,
) {
    private val changeListeners = mutableListOf<SettingChangeListener<T>>()
    private val fieldListeners = mutableMapOf<String, MutableList<FieldChangeListener<*>>>()

    /** Flow of current settings model */
    val flow: Flow<T> = dataStore.data
        .map { prefs ->
            var model = schema.default
            for (field in schema.fields) {
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                val value = typedField.read(prefs)
                if (value != null) {
                    model = typedField.set(model, value)
                }
            }
            model
        }
        .distinctUntilChanged()

    /** Add a global change listener */
    fun addChangeListener(listener: SettingChangeListener<T>) {
        changeListeners.add(listener)
    }

    fun removeChangeListener(listener: SettingChangeListener<T>) {
        changeListeners.remove(listener)
    }

    /** Add a listener for a specific field */
    fun <V> addFieldListener(fieldName: String, listener: FieldChangeListener<V>) {
        fieldListeners.getOrPut(fieldName) { mutableListOf() }.add(listener)
    }

    fun removeFieldListener(fieldName: String, listener: FieldChangeListener<*>) {
        fieldListeners[fieldName]?.remove(listener)
    }

    /** Observe a specific field as a Flow */
    @Suppress("UNCHECKED_CAST")
    fun <V> observeField(fieldName: String): Flow<V> {
        val field = schema.fieldByName(fieldName)
            ?: throw IllegalArgumentException("Unknown field: $fieldName")

        return flow.map { model ->
            (field as SettingField<T, V>).get(model)
        }.distinctUntilChanged()
    }

    /** Update settings with a transform function */
    suspend fun update(transform: (T) -> T) {
        val current = flow.first()
        val updated = transform(current)

        dataStore.edit { prefs ->
            for (field in schema.fields) {
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                val oldValue = typedField.get(current)
                val newValue = typedField.get(updated)
                if (oldValue != newValue) {
                    typedField.write(prefs, newValue)
                    notifyChange(field, oldValue, newValue)
                }
            }
        }
    }

    /** Set a single field by name */
    suspend fun set(name: String, value: Any) {
        val field = schema.fieldByName(name) ?: return
        val current = flow.first()

        @Suppress("UNCHECKED_CAST")
        val typedField = field as SettingField<T, Any?>
        val oldValue = typedField.get(current)

        if (oldValue == value) return

        dataStore.edit { prefs ->
            typedField.write(prefs, value)
        }

        notifyChange(field, oldValue, value)
    }

    /** Get current value of a field */
    suspend fun <V> get(name: String): V? {
        val field = schema.fieldByName(name) ?: return null
        val current = flow.first()

        @Suppress("UNCHECKED_CAST")
        return (field as SettingField<T, V>).get(current)
    }

    private suspend fun notifyChange(field: SettingField<T, *>, oldValue: Any?, newValue: Any?) {
        changeListeners.forEach { listener ->
            listener.onChanged(field, oldValue, newValue)
        }

        fieldListeners[field.name]?.forEach { listener ->
            @Suppress("UNCHECKED_CAST")
            (listener as FieldChangeListener<Any?>).onChanged(oldValue, newValue)
        }
    }
}