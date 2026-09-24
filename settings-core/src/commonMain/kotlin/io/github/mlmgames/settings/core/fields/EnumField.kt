package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingFieldCapability
import io.github.mlmgames.settings.core.SettingMeta
import io.github.mlmgames.settings.core.formatEnumDisplayName

class EnumField<T, E : Enum<E>>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> E,
    private val setter: (T, E) -> T,
    private val enumValues: Array<E>,
    private val defaultValue: E? = null,
) : SettingField<T, E> {
    internal val key = stringPreferencesKey(storageKeyName(keyName, "enum"))
    private val legacyKey = stringPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): E = getter(model)
    override fun set(model: T, value: E): T = setter(model, value)
    override fun read(prefs: Preferences): E? {
        val stored = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return null
        return enumValues.firstOrNull { it.name == stored } ?: defaultValue
    }
    override fun write(prefs: MutablePreferences, value: E) {
        prefs[key] = value.name
        prefs[legacyKey] = value.name
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isStoredValueValid(prefs: Preferences): Boolean {
        val stored = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return true
        return enumValues.any { it.name == stored }
    }
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun toUiDropdownIndex(model: T): Int? = enumValues.indexOf(getter(model)).takeIf { it >= 0 }
    override fun fromUiDropdownIndex(index: Int): E? = enumValues.getOrNull(index) ?: defaultValue
    override fun getDropdownOptions(): List<String> = enumValues.map { formatEnumDisplayName(it.name) }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: E): String = FieldEncoding.encode(FieldEncoding.ENUM, value.name)
    override fun decodeValue(encoded: String): E? {
        val tagged = FieldEncoding.tagged(encoded, FieldEncoding.ENUM, FieldEncoding.STRING)
        if (tagged.payload.isEmpty()) {
            throw IllegalArgumentException("Invalid enum value: $encoded")
        }
        return when (tagged.tag) {
            FieldEncoding.ENUM -> enumValues.firstOrNull { it.name == tagged.payload }
                ?: throw IllegalArgumentException("Unknown enum value: $encoded")
            FieldEncoding.STRING -> enumValues.firstOrNull { it.name == tagged.payload } ?: defaultValue
            else -> throw IllegalArgumentException("Unknown enum tag: $encoded")
        }
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
        internal const val NULL_MARKER = "__NULL__"
    }

    private val key = stringPreferencesKey(storageKeyName(keyName, "nullable_enum"))
    private val nullKey = booleanPreferencesKey(nullStorageKeyName(keyName, "nullable_enum"))
    private val legacyKey = stringPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, nullKey, legacyKey)

    override fun get(model: T): E? = getter(model)
    override fun set(model: T, value: E?): T = setter(model, value)
    override fun read(prefs: Preferences): E? {
        if (nullKey in prefs) return null
        val stored = if (key in prefs) {
            prefs.safeGet(key) ?: return null
        } else {
            val legacy = prefs.safeGet(legacyKey) ?: return null
            if (legacy == NULL_MARKER) return null
            legacy
        }
        return enumValues.firstOrNull { it.name == stored }
    }
    override fun write(prefs: MutablePreferences, value: E?) {
        prefs.removeAny(physicalKeys)
        if (value == null) {
            prefs[nullKey] = true
            prefs[legacyKey] = NULL_MARKER
        } else {
            prefs[key] = value.name
            prefs[legacyKey] = value.name
        }
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isExplicitNull(prefs: Preferences): Boolean =
        nullKey in prefs || (key !in prefs && prefs.safeGet(legacyKey) == NULL_MARKER)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override val supportsExplicitNull: Boolean
        get() = true
    override fun toUiDropdownIndex(model: T): Int? = getter(model)?.let { enumValues.indexOf(it) }?.takeIf { it >= 0 }
    override fun fromUiDropdownIndex(index: Int): E? = enumValues.getOrNull(index)
    override fun getDropdownOptions(): List<String> = enumValues.map { formatEnumDisplayName(it.name) }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: E?): String = when {
        value == null -> FieldEncoding.encode(FieldEncoding.NULL, "")
        else -> FieldEncoding.encode(FieldEncoding.ENUM, value.name)
    }
    override fun decodeValue(encoded: String): E? {
        val tagged = FieldEncoding.tagged(
            encoded,
            FieldEncoding.NULL,
            FieldEncoding.ENUM,
            FieldEncoding.STRING,
        )
        return when (tagged.tag) {
            FieldEncoding.NULL -> {
                if (tagged.payload.isEmpty()) null
                else throw IllegalArgumentException("Invalid null payload: $encoded")
            }
            FieldEncoding.ENUM -> {
                if (tagged.payload.isEmpty()) {
                    throw IllegalArgumentException("Invalid enum value: $encoded")
                }
                enumValues.firstOrNull { it.name == tagged.payload }
            }
            FieldEncoding.STRING -> when (tagged.payload) {
                "", NULL_MARKER -> null
                else -> enumValues.firstOrNull { it.name == tagged.payload }
            }
            else -> throw IllegalArgumentException("Invalid nullable enum value: $encoded")
        }
    }
}

class EnumOrdinalField<T, E : Enum<E>>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> E,
    private val setter: (T, E) -> T,
    private val enumValues: Array<E>,
    private val defaultValue: E? = null,
) : SettingField<T, E> {
    private val key = intPreferencesKey(storageKeyName(keyName, "enum_ordinal"))
    private val legacyKey = intPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): E = getter(model)
    override fun set(model: T, value: E): T = setter(model, value)
    override fun read(prefs: Preferences): E? {
        val ordinal = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return null
        return enumValues.getOrNull(ordinal) ?: defaultValue
    }
    override fun write(prefs: MutablePreferences, value: E) {
        prefs[key] = value.ordinal
        prefs[legacyKey] = value.ordinal
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isStoredValueValid(prefs: Preferences): Boolean {
        val stored = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return true
        return stored in enumValues.indices
    }
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun toUiDropdownIndex(model: T): Int? = enumValues.indexOf(getter(model)).takeIf { it >= 0 }
    override fun fromUiDropdownIndex(index: Int): E? = enumValues.getOrNull(index) ?: defaultValue
    override fun getDropdownOptions(): List<String> = enumValues.map { formatEnumDisplayName(it.name) }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: E): String = FieldEncoding.encode(FieldEncoding.ENUM_ORDINAL, value.ordinal.toString())
    override fun decodeValue(encoded: String): E? {
        val tagged = FieldEncoding.tagged(encoded, FieldEncoding.ENUM_ORDINAL, FieldEncoding.INT)
        val ordinal = tagged.payload.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid enum ordinal: $encoded")
        return when (tagged.tag) {
            FieldEncoding.ENUM_ORDINAL -> enumValues.getOrNull(ordinal)
                ?: throw IllegalArgumentException("Unknown enum ordinal: $encoded")
            FieldEncoding.INT -> enumValues.getOrNull(ordinal) ?: defaultValue
            else -> throw IllegalArgumentException("Unknown enum ordinal tag: $encoded")
        }
    }
}
