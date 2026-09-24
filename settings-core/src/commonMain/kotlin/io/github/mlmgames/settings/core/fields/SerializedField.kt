package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class SerializedField<T, V>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> V,
    private val setter: (T, V) -> T,
    private val serializer: KSerializer<V>,
    @Suppress("unused") private val defaultValue: V,
    private val json: Json = DefaultJson,
) : SettingField<T, V> {
    companion object {
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            coerceInputValues = true
        }
    }

    private val key = stringPreferencesKey(storageKeyName(keyName, "serialized"))
    private val legacyKey = stringPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): V = getter(model)
    override fun set(model: T, value: V): T = setter(model, value)
    override fun read(prefs: Preferences): V? {
        val stored = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return null
        return try {
            json.decodeFromString(serializer, stored)
        } catch (_: Exception) {
            null
        }
    }
    override fun write(prefs: MutablePreferences, value: V) {
        val encoded = json.encodeToString(serializer, value)
        prefs[key] = encoded
        prefs[legacyKey] = encoded
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun encodeValue(value: V): String =
        FieldEncoding.encode(FieldEncoding.SERIALIZED, json.encodeToString(serializer, value))
    override fun decodeValue(encoded: String): V {
        val tagged = FieldEncoding.tagged(encoded, FieldEncoding.SERIALIZED)
        return json.decodeFromString(serializer, tagged.payload)
    }
}

class NullableSerializedField<T, V : Any>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> V?,
    private val setter: (T, V?) -> T,
    private val serializer: KSerializer<V>,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, V?> {
    companion object {
        internal const val NULL_MARKER = "__NULL__"
    }

    private val key = stringPreferencesKey(storageKeyName(keyName, "nullable_serialized"))
    private val nullKey = booleanPreferencesKey(nullStorageKeyName(keyName, "nullable_serialized"))
    private val legacyKey = stringPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, nullKey, legacyKey)

    override fun get(model: T): V? = getter(model)
    override fun set(model: T, value: V?): T = setter(model, value)
    override fun read(prefs: Preferences): V? {
        if (nullKey in prefs) return null
        val stored = if (key in prefs) {
            prefs.safeGet(key) ?: return null
        } else {
            val legacy = prefs.safeGet(legacyKey) ?: return null
            if (legacy == NULL_MARKER) return null
            legacy
        }
        return try {
            json.decodeFromString(serializer, stored)
        } catch (_: Exception) {
            null
        }
    }
    override fun write(prefs: MutablePreferences, value: V?) {
        prefs.removeAny(physicalKeys)
        if (value == null) {
            prefs[nullKey] = true
            prefs[legacyKey] = NULL_MARKER
        } else {
            val encoded = json.encodeToString(serializer, value)
            prefs[key] = encoded
            prefs[legacyKey] = encoded
        }
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isExplicitNull(prefs: Preferences): Boolean =
        nullKey in prefs || (key !in prefs && prefs.safeGet(legacyKey) == NULL_MARKER)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override val supportsExplicitNull: Boolean
        get() = true
    override fun encodeValue(value: V?): String = when (value) {
        null -> FieldEncoding.encode(FieldEncoding.NULL, "")
        else -> FieldEncoding.encode(FieldEncoding.NULLABLE_SERIALIZED, json.encodeToString(serializer, value))
    }
    override fun decodeValue(encoded: String): V? {
        val tagged = FieldEncoding.tagged(
            encoded,
            FieldEncoding.NULL,
            FieldEncoding.NULLABLE_SERIALIZED,
            FieldEncoding.SERIALIZED,
        )
        return when (tagged.tag) {
            FieldEncoding.NULL -> {
                if (tagged.payload.isEmpty()) null
                else throw IllegalArgumentException("Invalid null payload: $encoded")
            }
            FieldEncoding.NULLABLE_SERIALIZED -> json.decodeFromString(serializer, tagged.payload)
            FieldEncoding.SERIALIZED -> when (tagged.payload) {
                "", NULL_MARKER -> null
                else -> json.decodeFromString(serializer, tagged.payload)
            }
            else -> throw IllegalArgumentException("Invalid nullable serialized value: $encoded")
        }
    }
}
