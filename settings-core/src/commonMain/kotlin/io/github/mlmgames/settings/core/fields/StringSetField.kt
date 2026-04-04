package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
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
    private val key = stringSetPreferencesKey(keyName)

    override fun get(model: T): Set<String> = getter(model)
    override fun set(model: T, value: Set<String>): T = setter(model, value)
    override fun read(prefs: Preferences): Set<String>? = prefs[key]
    override fun write(prefs: MutablePreferences, value: Set<String>) { prefs[key] = value }

    override fun encodeValue(value: Set<String>): String = "ss:" + json.encodeToString(value.toList())
    override fun decodeValue(encoded: String): Set<String> {
        val list = json.decodeFromString<List<String>>(encoded.substringAfter(':'))
        return list.toSet()
    }
}