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

    /** Convert value to UI slider float. Returns null if not applicable. */
    fun toUiSliderValue(model: T): Float? = null

    /** Convert UI slider float back to value. Returns null if not applicable. */
    fun fromUiSliderValue(value: Float): V? = null

    /** Convert value to UI dropdown index. Returns null if not applicable. */
    fun toUiDropdownIndex(model: T): Int? = null

    /** Convert UI dropdown index back to value. Returns null if not applicable. */
    fun fromUiDropdownIndex(index: Int): V? = null

    /** Get dropdown options. Returns null if not applicable. */
    fun getDropdownOptions(): List<String>? = null

    /** Encode a typed value to a type-prefixed string for backup export. */
    fun encodeValue(value: V): String? = null

    /** Decode a type-prefixed string back to a typed value for backup import. */
    fun decodeValue(encoded: String): V? = null
}