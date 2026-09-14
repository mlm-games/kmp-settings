package io.github.mlmgames.settings.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 *
 * DataStore serializes all writes through a single actor, so the check-then-act
 * sequences below cannot interleave mid-transaction. Reads are taken from the
 * transaction snapshot (not a stale [flow] emission) to keep diffs consistent.
 * Listeners are invoked after the transaction commits so a re-entrant
 * repository call throws IllegalStateException from DataStore instead of
 * deadlocking, and so retries never produce duplicate callbacks.
 */
class SettingsRepository<T>(
    private val dataStore: DataStore<Preferences>,
    val schema: SettingsSchema<T>,
) {
    private val listenerMutex = Mutex()
    private val changeListeners = mutableListOf<SettingChangeListener<T>>()
    private val fieldListeners = mutableMapOf<String, MutableList<FieldChangeListener<*>>>()

    /** Flow of current settings model */
    val flow: Flow<T> = dataStore.data
        .map { prefs -> buildModel(prefs) }
        .distinctUntilChanged()

    private fun buildModel(prefs: Preferences): T {
        var model = schema.default
        for (field in schema.fields) {
            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            if (!typedField.hasValue(prefs)) continue
            if (typedField.isExplicitNull(prefs)) {
                model = typedField.set(model, null)
                continue
            }
            val value = typedField.read(prefs) ?: continue
            // Corrupt values decode to null with isExplicitNull()==false:
            // keep the schema default instead of persisting a silent reset.
            model = typedField.set(model, value)
        }
        return model
    }

    /** Add a global change listener */
    suspend fun addChangeListener(listener: SettingChangeListener<T>) {
        listenerMutex.withLock { changeListeners.add(listener) }
    }

    suspend fun removeChangeListener(listener: SettingChangeListener<T>) {
        listenerMutex.withLock { changeListeners.remove(listener) }
    }

    /** Add a listener for a specific field */
    suspend fun <V> addFieldListener(fieldName: String, listener: FieldChangeListener<V>) {
        listenerMutex.withLock {
            fieldListeners.getOrPut(fieldName) { mutableListOf() }.add(listener)
        }
    }

    suspend fun removeFieldListener(fieldName: String, listener: FieldChangeListener<*>) {
        listenerMutex.withLock { fieldListeners[fieldName]?.remove(listener) }
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
        // Snapshot of changes computed inside the transaction; notification after commit.
        val changes = mutableListOf<Triple<SettingField<T, *>, Any?, Any?>>()
        dataStore.edit { prefs ->
            val current = buildModel(prefs)
            val updated = transform(current)
            for (field in schema.fields) {
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                val oldValue = typedField.get(current)
                val newValue = typedField.get(updated)
                if (oldValue != newValue) {
                    typedField.write(prefs, newValue)
                    if (typedField.read(prefs) != newValue && !typedField.isExplicitNull(prefs)) {
                        throw IllegalStateException("Write verification failed for ${field.name}")
                    }
                    changes.add(Triple(field, oldValue, newValue))
                }
            }
        }
        changes.forEach { (field, oldValue, newValue) ->
            notifyChange(field, oldValue, newValue)
        }
    }

    /** Set a single field by name. Unknown names throw instead of silently dropping. */
    suspend fun set(name: String, value: Any?) {
        val field = schema.fieldByName(name)
            ?: throw IllegalArgumentException("Unknown field: $name")

        @Suppress("UNCHECKED_CAST")
        val typedField = field as SettingField<T, Any?>
        var appliedChange: Triple<SettingField<T, *>, Any?, Any?>? = null
        dataStore.edit { prefs ->
            val current = buildModel(prefs)
            val oldValue = typedField.get(current)
            if (oldValue == value) return@edit

            validateWriteValue(typedField, value)
            typedField.write(prefs, value)
            if (typedField.read(prefs) != value && !typedField.isExplicitNull(prefs)) {
                throw IllegalStateException("Write verification failed for $name")
            }
            appliedChange = Triple(field, oldValue, value)
        }
        appliedChange?.let { (field, oldValue, newValue) ->
            notifyChange(field, oldValue, newValue)
        }
    }

    /** Get current value of a field */
    suspend fun <V> get(name: String): V? {
        val field = schema.fieldByName(name) ?: return null
        val current = flow.first()

        @Suppress("UNCHECKED_CAST")
        return (field as SettingField<T, V>).get(current)
    }

    private fun validateWriteValue(field: SettingField<T, *>, value: Any?) {
        // Lightweight runtime guard: Unit placeholders accept only Unit.
        if (field is io.github.mlmgames.settings.core.fields.UnitField<*> && value != Unit) {
            throw IllegalArgumentException("Field ${field.name} is a Button action and accepts only Unit")
        }
    }

    private suspend fun notifyChange(field: SettingField<T, *>, oldValue: Any?, newValue: Any?) {
        val globals: List<SettingChangeListener<T>>
        val scoped: List<FieldChangeListener<*>>
        listenerMutex.withLock {
            globals = changeListeners.toList()
            scoped = fieldListeners[field.name]?.toList().orEmpty()
        }
        globals.forEach { listener ->
            listener.onChanged(field, oldValue, newValue)
        }
        scoped.forEach { listener ->
            @Suppress("UNCHECKED_CAST")
            (listener as FieldChangeListener<Any?>).onChanged(oldValue, newValue)
        }
    }
}
