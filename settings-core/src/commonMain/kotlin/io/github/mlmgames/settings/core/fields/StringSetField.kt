package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta
import kotlinx.serialization.json.Json

class StringSetField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Set<String>,
    private val setter: (T, Set<String>) -> T,
    private val json: Json = SerializedField.DefaultJson,
) : SettingField<T, Set<String>> {
    private val key = stringSetPreferencesKey(storageKeyName(keyName, "string_set"))
    private val legacyKey = stringSetPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): Set<String> = getter(model)
    override fun set(model: T, value: Set<String>): T = setter(model, value)
    override fun read(prefs: Preferences): Set<String>? = prefs.safeGet(key) ?: prefs.safeGet(legacyKey)
    override fun write(prefs: MutablePreferences, value: Set<String>) {
        prefs[key] = value
        prefs[legacyKey] = value
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun encodeValue(value: Set<String>): String =
        FieldEncoding.encode(FieldEncoding.STRING_SET, json.encodeToString(value.sorted().toList()))
    override fun decodeValue(encoded: String): Set<String> {
        val tagged = FieldEncoding.tagged(encoded, FieldEncoding.STRING_SET, FieldEncoding.SERIALIZED)
        return json.decodeFromString<List<String>>(tagged.payload).toSet()
    }
}
