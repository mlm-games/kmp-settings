    package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta

class EnumField<T, E : Enum<E>>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> E,
    private val setter: (T, E) -> T,
    private val enumValues: Array<E>,
    @Suppress("unused") private val defaultValue: E? = null,
) : SettingField<T, E> {
    internal val key = stringPreferencesKey(keyName)
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): E = getter(model)
    override fun set(model: T, value: E): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    /**
     * Unknown persisted names (renamed/removed enum entries) decode to null so
     * callers fall back to the schema default explicitly instead of silently
     * substituting the default inside the field. Never count them as applied.
     */
    override fun read(prefs: Preferences): E? {
        val name = prefs[key] ?: return null
        return enumValues.firstOrNull { it.name == name }
    }

    override fun write(prefs: MutablePreferences, value: E) {
        prefs[key] = value.name
    }

    override fun toUiDropdownIndex(model: T): Int {
        return getter(model).ordinal
    }

    /**
     * Out-of-range indices are rejected (null) so the UI can surface an error
     * instead of silently resetting to the default.
     */
    override fun fromUiDropdownIndex(index: Int): E? {
        return enumValues.getOrNull(index)
    }

    override fun getDropdownOptions(): List<String> {
        return enumValues.map { it.name }
    }

    override fun encodeValue(value: E): String = "s:${value.name}"
    override fun decodeValue(encoded: String): E? {
        val name = encoded.substringAfter(':')
        return enumValues.firstOrNull { it.name == name }
    }
}

class NullableEnumField<T, E : Enum<E>>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> E?,
    private val setter: (T, E?) -> T,
    private val enumValues: Array<E>,
) : SettingField<T, E?> {
    companion object {
        private const val NULL_MARKER = "__NULL__"
    }

    internal val key = stringPreferencesKey(keyName)
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): E? = getter(model)
    override fun set(model: T, value: E?): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun isExplicitNull(prefs: Preferences): Boolean =
        prefs[key] == NULL_MARKER
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): E? {
        val name = prefs[key] ?: return null
        if (name == NULL_MARKER) return null
        return enumValues.firstOrNull { it.name == name }
    }

    override fun write(prefs: MutablePreferences, value: E?) {
        prefs[key] = value?.name ?: NULL_MARKER
    }

    override fun toUiDropdownIndex(model: T): Int? = getter(model)?.ordinal

    override fun fromUiDropdownIndex(index: Int): E? = enumValues.getOrNull(index)

    override fun getDropdownOptions(): List<String> = enumValues.map { it.name }

    override fun encodeValue(value: E?): String {
        if (value == null) return "s:"
        return "s:${value.name}"
    }

    override fun decodeValue(encoded: String): E? {
        val name = encoded.substringAfter(':')
        if (name.isEmpty()) return null
        return enumValues.firstOrNull { it.name == name }
    }
}

class EnumOrdinalField<T, E : Enum<E>>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> E,
    private val setter: (T, E) -> T,
    private val enumValues: Array<E>,
) : SettingField<T, E> {
    internal val key = intPreferencesKey(keyName)
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): E = getter(model)
    override fun set(model: T, value: E): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): E? {
        val ordinal = prefs[key] ?: return null
        return enumValues.getOrNull(ordinal)
    }

    override fun write(prefs: MutablePreferences, value: E) {
        prefs[key] = value.ordinal
    }

    override fun toUiDropdownIndex(model: T): Int = getter(model).ordinal

    override fun fromUiDropdownIndex(index: Int): E? = enumValues.getOrNull(index)

    override fun getDropdownOptions(): List<String> = enumValues.map { it.name }

    override fun encodeValue(value: E): String = "i:${value.ordinal}"
    override fun decodeValue(encoded: String): E? {
        val ordinal = encoded.substringAfter(':').toIntOrNull() ?: return null
        return enumValues.getOrNull(ordinal)
    }
}