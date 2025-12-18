package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta
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
    private val key = stringPreferencesKey(keyName)
    private val serializer = ListSerializer(String.serializer())

    override fun get(model: T): List<String> = getter(model)
    override fun set(model: T, value: List<String>): T = setter(model, value)

    override fun read(prefs: Preferences): List<String>? {
        val jsonString = prefs[key] ?: return null
        return try { json.decodeFromString(serializer, jsonString) } catch (e: Exception) { null }
    }

    override fun write(prefs: MutablePreferences, value: List<String>) {
        prefs[key] = json.encodeToString(serializer, value)
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
    private val key = stringPreferencesKey(keyName)
    private val serializer = ListSerializer(Int.serializer())

    override fun get(model: T): List<Int> = getter(model)
    override fun set(model: T, value: List<Int>): T = setter(model, value)

    override fun read(prefs: Preferences): List<Int>? {
        val jsonString = prefs[key] ?: return null
        return try { json.decodeFromString(serializer, jsonString) } catch (e: Exception) { null }
    }

    override fun write(prefs: MutablePreferences, value: List<Int>) {
        prefs[key] = json.encodeToString(serializer, value)
    }
}

class StringMapField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Map<String, String>,
    private val setter: (T, Map<String, String>) -> T,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, Map<String, String>> {
    private val key = stringPreferencesKey(keyName)
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override fun get(model: T): Map<String, String> = getter(model)
    override fun set(model: T, value: Map<String, String>): T = setter(model, value)

    override fun read(prefs: Preferences): Map<String, String>? {
        val jsonString = prefs[key] ?: return null
        return try { json.decodeFromString(serializer, jsonString) } catch (e: Exception) { null }
    }

    override fun write(prefs: MutablePreferences, value: Map<String, String>) {
        prefs[key] = json.encodeToString(serializer, value)
    }
}

class StringLongMapField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Map<String, Long>,
    private val setter: (T, Map<String, Long>) -> T,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, Map<String, Long>> {
    private val key = stringPreferencesKey(keyName)
    private val serializer = MapSerializer(String.serializer(), Long.serializer())

    override fun get(model: T): Map<String, Long> = getter(model)
    override fun set(model: T, value: Map<String, Long>): T = setter(model, value)

    override fun read(prefs: Preferences): Map<String, Long>? {
        val jsonString = prefs[key] ?: return null
        return try { json.decodeFromString(serializer, jsonString) } catch (e: Exception) { null }
    }

    override fun write(prefs: MutablePreferences, value: Map<String, Long>) {
        prefs[key] = json.encodeToString(serializer, value)
    }
}

class StringIntMapField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Map<String, Int>,
    private val setter: (T, Map<String, Int>) -> T,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, Map<String, Int>> {
    private val key = stringPreferencesKey(keyName)
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    override fun get(model: T): Map<String, Int> = getter(model)
    override fun set(model: T, value: Map<String, Int>): T = setter(model, value)

    override fun read(prefs: Preferences): Map<String, Int>? {
        val jsonString = prefs[key] ?: return null
        return try { json.decodeFromString(serializer, jsonString) } catch (e: Exception) { null }
    }

    override fun write(prefs: MutablePreferences, value: Map<String, Int>) {
        prefs[key] = json.encodeToString(serializer, value)
    }
}