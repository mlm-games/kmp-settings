package io.github.mlmgames.settings.core

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

/**
 * Represents a single setting field with type-safe get/set operations.
 */
interface SettingField<T, V> {
    /** Property name in the data class */
    val name: String

    /** DataStore key name */
    val keyName: String

    /** UI metadata (null for @Persisted-only fields) */
    val meta: SettingMeta?

    /** Get value from model */
    fun get(model: T): V

    /** Set value in model (returns new model) */
    fun set(model: T, value: V): T

    /** Read from preferences */
    fun read(prefs: Preferences): V?

    /** Write to preferences */
    fun write(prefs: MutablePreferences, value: V)
}