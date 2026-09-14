package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
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
    private val defaultValue: V,
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

    private val key = stringPreferencesKey(keyName)
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): V = getter(model)
    override fun set(model: T, value: V): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): V? {
        val jsonString = prefs[key] ?: return null
        return try {
            json.decodeFromString(serializer, jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Serialization failures propagate so repositories never silently report
     * success while persisting nothing.
     */
    override fun write(prefs: MutablePreferences, value: V) {
        prefs[key] = json.encodeToString(serializer, value)
    }

    override fun encodeValue(value: V): String = "j:" + json.encodeToString(serializer, value)

    override fun decodeValue(encoded: String): V {
        return json.decodeFromString(serializer, encoded.substringAfter(':'))
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
        private const val NULL_MARKER = "__NULL__"
    }

    private val key = stringPreferencesKey(keyName)
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): V? = getter(model)
    override fun set(model: T, value: V?): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun isExplicitNull(prefs: Preferences): Boolean =
        prefs[key] == NULL_MARKER
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): V? {
        val jsonString = prefs[key] ?: return null
        if (jsonString == NULL_MARKER) return null
        return try {
            json.decodeFromString(serializer, jsonString)
        } catch (e: Exception) {
            null
        }
    }

    override fun write(prefs: MutablePreferences, value: V?) {
        if (value == null) {
            prefs[key] = NULL_MARKER
        } else {
            prefs[key] = json.encodeToString(serializer, value)
        }
    }

    override fun encodeValue(value: V?): String {
        if (value == null) return "n:"
        return "j:" + json.encodeToString(serializer, value)
    }

    override fun decodeValue(encoded: String): V? {
        val data = encoded.substringAfter(':')
        if (data.isEmpty()) return null
        return json.decodeFromString(serializer, data)
    }
}