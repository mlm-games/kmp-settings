package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta

class StringSetField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Set<String>,
    private val setter: (T, Set<String>) -> T,
) : SettingField<T, Set<String>> {
    private val key = stringSetPreferencesKey(keyName)

    override fun get(model: T): Set<String> = getter(model)
    override fun set(model: T, value: Set<String>): T = setter(model, value)
    override fun read(prefs: Preferences): Set<String>? = prefs[key]
    override fun write(prefs: MutablePreferences, value: Set<String>) { prefs[key] = value }
}