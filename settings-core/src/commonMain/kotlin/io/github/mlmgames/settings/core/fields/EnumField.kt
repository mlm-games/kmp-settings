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
    private val defaultValue: E,
) : SettingField<T, E> {
    private val key = stringPreferencesKey(keyName)

    override fun get(model: T): E = getter(model)
    override fun set(model: T, value: E): T = setter(model, value)

    override fun read(prefs: Preferences): E? {
        val name = prefs[key] ?: return null
        return enumValues.firstOrNull { it.name == name } ?: defaultValue
    }

    override fun write(prefs: MutablePreferences, value: E) {
        prefs[key] = value.name
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

    private val key = stringPreferencesKey(keyName)

    override fun get(model: T): E? = getter(model)
    override fun set(model: T, value: E?): T = setter(model, value)

    override fun read(prefs: Preferences): E? {
        val name = prefs[key] ?: return null
        if (name == NULL_MARKER) return null
        return enumValues.firstOrNull { it.name == name }
    }

    override fun write(prefs: MutablePreferences, value: E?) {
        prefs[key] = value?.name ?: NULL_MARKER
    }
}

class EnumOrdinalField<T, E : Enum<E>>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> E,
    private val setter: (T, E) -> T,
    private val enumValues: Array<E>,
    private val defaultValue: E,
) : SettingField<T, E> {
    private val key = intPreferencesKey(keyName)

    override fun get(model: T): E = getter(model)
    override fun set(model: T, value: E): T = setter(model, value)

    override fun read(prefs: Preferences): E? {
        val ordinal = prefs[key] ?: return null
        return enumValues.getOrNull(ordinal) ?: defaultValue
    }

    override fun write(prefs: MutablePreferences, value: E) {
        prefs[key] = value.ordinal
    }
}