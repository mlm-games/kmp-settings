package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import io.github.mlmgames.settings.core.SettingFieldStorage
import io.github.mlmgames.settings.core.SettingMeta

internal fun storageKeyName(keyName: String, kind: String): String =
    SettingFieldStorage.valueKeyName(keyName, kind)

internal fun nullStorageKeyName(keyName: String, kind: String): String =
    SettingFieldStorage.nullKeyName(keyName, kind)

internal fun <T> Preferences.safeGet(key: Preferences.Key<T>): T? =
    try {
        this[key]
    } catch (_: ClassCastException) {
        null
    }

internal fun Preferences.containsAny(keys: Iterable<Preferences.Key<*>>): Boolean =
    keys.any { it in this }

internal fun MutablePreferences.removeAny(keys: Iterable<Preferences.Key<*>>) {
    keys.forEach { remove(it) }
}

internal fun sliderInputAllowed(meta: SettingMeta?, value: Float): Boolean {
    if (!value.isFinite()) return false
    val min = meta?.min ?: return true
    val max = meta.max
    return value in min..max
}

internal fun dropdownOptions(meta: SettingMeta?): List<String> = meta?.options.orEmpty()

internal fun dropdownIndexAllowed(meta: SettingMeta?, index: Int): Boolean {
    if (index < 0) return false
    val options = dropdownOptions(meta)
    return options.isEmpty() || index < options.size
}

internal fun uiFloatToDouble(value: Float): Double = value.toString().toDouble()
