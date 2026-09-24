@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.mlmgames.settings.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference

/** Listener for setting changes. */
fun interface SettingChangeListener<T> {
    suspend fun onChanged(field: SettingField<T, *>, oldValue: Any?, newValue: Any?)
}

/** Listener for specific field changes. */
fun interface FieldChangeListener<V> {
    suspend fun onChanged(oldValue: V, newValue: V)
}

private class ChangeListenerEntry<T>(
    val listener: SettingChangeListener<T>,
) {
    private val active = AtomicBoolean(true)

    fun isActive(): Boolean = active.load()

    fun deactivate() {
        active.store(false)
    }
}

private class FieldListenerEntry(
    val listener: FieldChangeListener<*>,
) {
    private val active = AtomicBoolean(true)

    fun isActive(): Boolean = active.load()

    fun deactivate() {
        active.store(false)
    }
}

private data class ListenerState<T>(
    val changeListeners: List<ChangeListenerEntry<T>> = emptyList(),
    val fieldListeners: Map<String, List<FieldListenerEntry>> = emptyMap(),
)

class SettingsRepository<T>(
    private val dataStore: DataStore<Preferences>,
    val schema: SettingsSchema<T>,
) : AutoCloseable {
    private val listenerState = AtomicReference(ListenerState<T>())
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationJob = AtomicReference<Job?>(null)
    private val closed = AtomicBoolean(false)

    private fun ensureNotificationCollector() {
        if (closed.load() || notificationJob.load() != null) return
        val job = notificationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                var hasPrevious = false
                var previous: T? = null
                dataStore.data.collect { preferences ->
                    val current = buildModel(preferences)
                    if (hasPrevious) {
                        try {
                            dispatchChanges(previous, current)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                        }
                    }
                    previous = current
                    hasPrevious = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            } finally {
                val currentJob = coroutineContext[Job]
                if (currentJob != null && notificationJob.compareAndSet(currentJob, null) && !closed.load()) {
                    val state = listenerState.load()
                    if (state.changeListeners.isNotEmpty() || state.fieldListeners.isNotEmpty()) {
                        notificationScope.launch {
                            delay(100)
                            ensureNotificationCollector()
                        }
                    }
                }
            }
        }
        if (!notificationJob.compareAndSet(null, job)) job.cancel()
    }

    private fun stopNotificationCollectorIfIdle() {
        val current = listenerState.load()
        if (current.changeListeners.isNotEmpty() || current.fieldListeners.isNotEmpty()) return
        while (true) {
            val job = notificationJob.load() ?: return
            if (notificationJob.compareAndSet(job, null)) {
                job.cancel()
                return
            }
        }
    }

    private inline fun updateListenerState(transform: (ListenerState<T>) -> ListenerState<T>) {
        while (true) {
            val current = listenerState.load()
            val updated = transform(current)
            if (listenerState.compareAndSet(current, updated)) return
        }
    }

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
            model = typedField.set(model, value)
        }
        return model
    }

    /** Add a global change listener */
    fun addChangeListener(listener: SettingChangeListener<T>) {
        updateListenerState { current ->
            current.copy(changeListeners = current.changeListeners + ChangeListenerEntry(listener))
        }
        ensureNotificationCollector()
    }

    fun removeChangeListener(listener: SettingChangeListener<T>) {
        while (true) {
            val current = listenerState.load()
            val removed = current.changeListeners.filter { it.listener == listener }
            if (removed.isEmpty()) return
            removed.forEach { it.deactivate() }
            val updated = current.copy(changeListeners = current.changeListeners - removed.toSet())
            if (listenerState.compareAndSet(current, updated)) {
                stopNotificationCollectorIfIdle()
                return
            }
        }
    }

    /** Add a listener for a specific field */
    fun <V> addFieldListener(fieldName: String, listener: FieldChangeListener<V>) {
        updateListenerState { current ->
            val listeners = current.fieldListeners[fieldName].orEmpty()
            current.copy(
                fieldListeners = current.fieldListeners + (fieldName to listeners + FieldListenerEntry(listener)),
            )
        }
        ensureNotificationCollector()
    }

    fun removeFieldListener(fieldName: String, listener: FieldChangeListener<*>) {
        while (true) {
            val current = listenerState.load()
            val listeners = current.fieldListeners[fieldName].orEmpty()
            val removed = listeners.filter { it.listener == listener }
            if (removed.isEmpty()) return
            removed.forEach { it.deactivate() }
            val remaining = listeners - removed.toSet()
            val fieldListeners = if (remaining.isEmpty()) {
                current.fieldListeners - fieldName
            } else {
                current.fieldListeners + (fieldName to remaining)
            }
            val updated = current.copy(fieldListeners = fieldListeners)
            if (listenerState.compareAndSet(current, updated)) {
                stopNotificationCollectorIfIdle()
                return
            }
        }
    }

    fun clearListeners() {
        while (true) {
            val current = listenerState.load()
            if (current.changeListeners.isEmpty() && current.fieldListeners.isEmpty()) return
            current.changeListeners.forEach { it.deactivate() }
            current.fieldListeners.values.flatten().forEach { it.deactivate() }
            if (listenerState.compareAndSet(current, ListenerState())) {
                stopNotificationCollectorIfIdle()
                return
            }
        }
    }

    override fun close() {
        closed.store(true)
        clearListeners()
        notificationScope.cancel()
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
        dataStore.edit { prefs ->
            val current = buildModel(prefs)
            val oldValues = schema.fields.map { field ->
                @Suppress("UNCHECKED_CAST")
                snapshotValue((field as SettingField<T, Any?>).get(current))
            }
            val updated = transform(current)
            for ((index, field) in schema.fields.withIndex()) {
                @Suppress("UNCHECKED_CAST")
                val typedField = field as SettingField<T, Any?>
                val oldValue = oldValues[index]
                val newValue = typedField.get(updated)
                if (!valuesEqual(oldValue, newValue)) {
                    typedField.write(prefs, newValue)
                    if (!valuesEqual(typedField.read(prefs), newValue) && !typedField.isExplicitNull(prefs)) {
                        throw IllegalStateException("Write verification failed for ${field.name}")
                    }
                }
            }
        }
    }

    /** Set a single field by name. Unknown names throw instead of silently dropping. */
    suspend fun set(name: String, value: Any?) {
        val field = schema.fieldByName(name)
            ?: throw IllegalArgumentException("Unknown field: $name")

        @Suppress("UNCHECKED_CAST")
        val typedField = field as SettingField<T, Any?>
        dataStore.edit { prefs ->
            val current = buildModel(prefs)
            val oldValue = typedField.get(current)
            val hadStoredValue = typedField.hasValue(prefs)
            if (valuesEqual(oldValue, value) && (hadStoredValue || value != null)) return@edit

            validateWriteValue(typedField, value)
            typedField.write(prefs, value)
            if (!valuesEqual(typedField.read(prefs), value) && !typedField.isExplicitNull(prefs)) {
                throw IllegalStateException("Write verification failed for $name")
            }
        }
    }

    /** Get current value of a field */
    suspend fun <V> get(name: String): V? {
        val field = schema.fieldByName(name) ?: return null
        val current = flow.first()

        @Suppress("UNCHECKED_CAST")
        return (field as SettingField<T, V>).get(current)
    }

    private fun snapshotValue(value: Any?): Any? = when (value) {
        is List<*> -> value.map(::snapshotValue)
        is Set<*> -> value.map(::snapshotValue).toSet()
        is Map<*, *> -> value.entries.associate { (key, entryValue) ->
            snapshotValue(key) to snapshotValue(entryValue)
        }
        is BooleanArray -> value.copyOf()
        is ByteArray -> value.copyOf()
        is CharArray -> value.copyOf()
        is DoubleArray -> value.copyOf()
        is FloatArray -> value.copyOf()
        is IntArray -> value.copyOf()
        is LongArray -> value.copyOf()
        is ShortArray -> value.copyOf()
        is Array<*> -> value.map(::snapshotValue)
        else -> value
    }

    private fun valuesEqual(first: Any?, second: Any?): Boolean {
        if (first === second || first == second) return true
        if (first is Float && second is Float) return first.isNaN() && second.isNaN()
        if (first is Double && second is Double) return first.isNaN() && second.isNaN()
        if (first is List<*> && second is List<*>) {
            return first.size == second.size && first.indices.all { index ->
                valuesEqual(first[index], second[index])
            }
        }
        if (first is Set<*> && second is Set<*>) {
            if (first.size != second.size) return false
            val unmatched = second.toMutableList()
            first.forEach { value ->
                val index = unmatched.indexOfFirst { candidate -> valuesEqual(value, candidate) }
                if (index < 0) return false
                unmatched.removeAt(index)
            }
            return true
        }
        if (first is Map<*, *> && second is Map<*, *>) {
            if (first.size != second.size) return false
            return first.all { (key, value) ->
                val other = second.entries.firstOrNull { entry -> valuesEqual(key, entry.key) }
                other != null && valuesEqual(value, other.value)
            }
        }
        if (first is Array<*> && second is Array<*>) {
            return first.size == second.size && first.indices.all { index ->
                valuesEqual(first[index], second[index])
            }
        }
        return false
    }

    private fun validateWriteValue(field: SettingField<T, *>, value: Any?) {
        if (field.isUnit && value != Unit) {
            throw IllegalArgumentException("Field ${field.name} is a Button action and accepts only Unit")
        }
    }

    private suspend fun dispatchChanges(previous: T?, current: T) {
        if (previous == null) return
        for (field in schema.fields) {
            @Suppress("UNCHECKED_CAST")
            val typedField = field as SettingField<T, Any?>
            val oldValue = snapshotValue(typedField.get(previous))
            val newValue = snapshotValue(typedField.get(current))
            if (!valuesEqual(oldValue, newValue)) {
                notifyChange(field, oldValue, newValue)
            }
        }
    }

    private suspend fun notifyChange(field: SettingField<T, *>, oldValue: Any?, newValue: Any?) {
        val current = listenerState.load()

        current.changeListeners.forEach { entry ->
            if (entry.isActive()) {
                notifySafely { entry.listener.onChanged(field, oldValue, newValue) }
            }
        }
        current.fieldListeners[field.name].orEmpty().forEach { entry ->
            if (entry.isActive()) {
                @Suppress("UNCHECKED_CAST")
                notifySafely {
                    (entry.listener as FieldChangeListener<Any?>).onChanged(oldValue, newValue)
                }
            }
        }
    }

    private suspend fun notifySafely(callback: suspend () -> Unit) {
        try {
            callback()
        } catch (failure: Throwable) {
            when {
                failure is CancellationException -> currentCoroutineContext().ensureActive()
                failure is Error -> throw failure
            }
        }
    }
}
