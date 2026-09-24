package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class StringListField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> List<String>,
    private val setter: (T, List<String>) -> T,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, List<String>> {
    private val key = stringPreferencesKey(storageKeyName(keyName, "string_list"))
    private val legacyKey = stringPreferencesKey(keyName)
    private val serializer = ListSerializer(String.serializer())
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): List<String> = getter(model)
    override fun set(model: T, value: List<String>): T = setter(model, value)
    override fun read(prefs: Preferences): List<String>? {
        val stored = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return null
        return try { json.decodeFromString(serializer, stored) } catch (_: Exception) { null }
    }
    override fun write(prefs: MutablePreferences, value: List<String>) {
        val encoded = json.encodeToString(serializer, value)
        prefs[key] = encoded
        prefs[legacyKey] = encoded
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun encodeValue(value: List<String>): String =
        FieldEncoding.encode(FieldEncoding.SERIALIZED, json.encodeToString(serializer, value))
    override fun decodeValue(encoded: String): List<String> {
        val tagged = FieldEncoding.tagged(encoded, FieldEncoding.STRING_LIST, FieldEncoding.SERIALIZED)
        return json.decodeFromString(serializer, tagged.payload)
    }
}

class IntListField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> List<Int>,
    private val setter: (T, List<Int>) -> T,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, List<Int>> {
    private val key = stringPreferencesKey(storageKeyName(keyName, "int_list"))
    private val legacyKey = stringPreferencesKey(keyName)
    private val serializer = ListSerializer(Int.serializer())
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): List<Int> = getter(model)
    override fun set(model: T, value: List<Int>): T = setter(model, value)
    override fun read(prefs: Preferences): List<Int>? {
        val stored = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return null
        return try { json.decodeFromString(serializer, stored) } catch (_: Exception) { null }
    }
    override fun write(prefs: MutablePreferences, value: List<Int>) {
        val encoded = json.encodeToString(serializer, value)
        prefs[key] = encoded
        prefs[legacyKey] = encoded
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun encodeValue(value: List<Int>): String =
        FieldEncoding.encode(FieldEncoding.SERIALIZED, json.encodeToString(serializer, value))
    override fun decodeValue(encoded: String): List<Int> {
        val tagged = FieldEncoding.tagged(encoded, FieldEncoding.INT_LIST, FieldEncoding.SERIALIZED)
        return json.decodeFromString(serializer, tagged.payload)
    }
}

class LongListField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> List<Long>,
    private val setter: (T, List<Long>) -> T,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, List<Long>> {
    private val key = stringPreferencesKey(storageKeyName(keyName, "long_list"))
    private val legacyKey = stringPreferencesKey(keyName)
    private val serializer = ListSerializer(Long.serializer())
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): List<Long> = getter(model)
    override fun set(model: T, value: List<Long>): T = setter(model, value)
    override fun read(prefs: Preferences): List<Long>? {
        val stored = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return null
        return try { json.decodeFromString(serializer, stored) } catch (_: Exception) { null }
    }
    override fun write(prefs: MutablePreferences, value: List<Long>) {
        val encoded = json.encodeToString(serializer, value)
        prefs[key] = encoded
        prefs[legacyKey] = encoded
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun encodeValue(value: List<Long>): String =
        FieldEncoding.encode(FieldEncoding.SERIALIZED, json.encodeToString(serializer, value))
    override fun decodeValue(encoded: String): List<Long> {
        val tagged = FieldEncoding.tagged(encoded, FieldEncoding.LONG_LIST, FieldEncoding.SERIALIZED)
        return json.decodeFromString(serializer, tagged.payload)
    }
}

abstract class BaseMapField<T, K, V>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Map<K, V>,
    private val setter: (T, Map<K, V>) -> T,
    private val serializer: KSerializer<Map<K, V>>,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, Map<K, V>> {
    private val key = stringPreferencesKey(storageKeyName(keyName, "map"))
    private val legacyKey = stringPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): Map<K, V> = getter(model)
    override fun set(model: T, value: Map<K, V>): T = setter(model, value)
    override fun read(prefs: Preferences): Map<K, V>? {
        val stored = prefs.safeGet(key) ?: prefs.safeGet(legacyKey) ?: return null
        return try { json.decodeFromString(serializer, stored) } catch (_: Exception) { null }
    }
    override fun write(prefs: MutablePreferences, value: Map<K, V>) {
        val encoded = json.encodeToString(serializer, value)
        prefs[key] = encoded
        prefs[legacyKey] = encoded
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun encodeValue(value: Map<K, V>): String =
        FieldEncoding.encode(FieldEncoding.SERIALIZED, json.encodeToString(serializer, value))
    override fun decodeValue(encoded: String): Map<K, V> {
        val tagged = FieldEncoding.tagged(encoded, FieldEncoding.SERIALIZED)
        return json.decodeFromString(serializer, tagged.payload)
    }
}

class StringMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<String, String>,
    setter: (T, Map<String, String>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, String, String>(
    name, keyName, meta, getter, setter,
    MapSerializer(String.serializer(), String.serializer()), json,
)

class StringIntMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<String, Int>,
    setter: (T, Map<String, Int>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, String, Int>(
    name, keyName, meta, getter, setter,
    MapSerializer(String.serializer(), Int.serializer()), json,
)

class StringLongMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<String, Long>,
    setter: (T, Map<String, Long>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, String, Long>(
    name, keyName, meta, getter, setter,
    MapSerializer(String.serializer(), Long.serializer()), json,
)

class StringFloatMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<String, Float>,
    setter: (T, Map<String, Float>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, String, Float>(
    name, keyName, meta, getter, setter,
    MapSerializer(String.serializer(), Float.serializer()), json,
)

class StringDoubleMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<String, Double>,
    setter: (T, Map<String, Double>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, String, Double>(
    name, keyName, meta, getter, setter,
    MapSerializer(String.serializer(), Double.serializer()), json,
)

class StringBooleanMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<String, Boolean>,
    setter: (T, Map<String, Boolean>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, String, Boolean>(
    name, keyName, meta, getter, setter,
    MapSerializer(String.serializer(), Boolean.serializer()), json,
)

class IntStringMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<Int, String>,
    setter: (T, Map<Int, String>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, Int, String>(
    name, keyName, meta, getter, setter,
    MapSerializer(Int.serializer(), String.serializer()), json,
)

class IntIntMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<Int, Int>,
    setter: (T, Map<Int, Int>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, Int, Int>(
    name, keyName, meta, getter, setter,
    MapSerializer(Int.serializer(), Int.serializer()), json,
)

class IntLongMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<Int, Long>,
    setter: (T, Map<Int, Long>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, Int, Long>(
    name, keyName, meta, getter, setter,
    MapSerializer(Int.serializer(), Long.serializer()), json,
)

class LongStringMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<Long, String>,
    setter: (T, Map<Long, String>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, Long, String>(
    name, keyName, meta, getter, setter,
    MapSerializer(Long.serializer(), String.serializer()), json,
)

class LongLongMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<Long, Long>,
    setter: (T, Map<Long, Long>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, Long, Long>(
    name, keyName, meta, getter, setter,
    MapSerializer(Long.serializer(), Long.serializer()), json,
)

class LongIntMapField<T>(
    name: String,
    keyName: String,
    meta: SettingMeta?,
    getter: (T) -> Map<Long, Int>,
    setter: (T, Map<Long, Int>) -> T,
    json: Json = SerializedField.DefaultJson,
) : BaseMapField<T, Long, Int>(
    name, keyName, meta, getter, setter,
    MapSerializer(Long.serializer(), Int.serializer()), json,
)
