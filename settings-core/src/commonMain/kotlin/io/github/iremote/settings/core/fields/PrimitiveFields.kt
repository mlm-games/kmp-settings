package io.github.iremote.settings.core.fields

import androidx.datastore.preferences.core.*
import io.github.iremote.settings.core.SettingField
import io.github.iremote.settings.core.SettingMeta

class BooleanField<T>(
  override val name: String,
  override val keyName: String,
  override val meta: SettingMeta?,
  private val getter: (T) -> Boolean,
  private val setter: (T, Boolean) -> T,
) : SettingField<T, Boolean> {
  private val key = booleanPreferencesKey(keyName)
  override fun get(model: T) = getter(model)
  override fun set(model: T, value: Boolean) = setter(model, value)
  override fun read(prefs: Preferences) = prefs[key]
  override fun write(prefs: MutablePreferences, value: Boolean) { prefs[key] = value }
}

class IntField<T>(
  override val name: String,
  override val keyName: String,
  override val meta: SettingMeta?,
  private val getter: (T) -> Int,
  private val setter: (T, Int) -> T,
) : SettingField<T, Int> {
  private val key = intPreferencesKey(keyName)
  override fun get(model: T) = getter(model)
  override fun set(model: T, value: Int) = setter(model, value)
  override fun read(prefs: Preferences) = prefs[key]
  override fun write(prefs: MutablePreferences, value: Int) { prefs[key] = value }
}

class LongField<T>(
  override val name: String,
  override val keyName: String,
  override val meta: SettingMeta?,
  private val getter: (T) -> Long,
  private val setter: (T, Long) -> T,
) : SettingField<T, Long> {
  private val key = longPreferencesKey(keyName)
  override fun get(model: T) = getter(model)
  override fun set(model: T, value: Long) = setter(model, value)
  override fun read(prefs: Preferences) = prefs[key]
  override fun write(prefs: MutablePreferences, value: Long) { prefs[key] = value }
}

class FloatField<T>(
  override val name: String,
  override val keyName: String,
  override val meta: SettingMeta?,
  private val getter: (T) -> Float,
  private val setter: (T, Float) -> T,
) : SettingField<T, Float> {
  private val key = floatPreferencesKey(keyName)
  override fun get(model: T) = getter(model)
  override fun set(model: T, value: Float) = setter(model, value)
  override fun read(prefs: Preferences) = prefs[key]
  override fun write(prefs: MutablePreferences, value: Float) { prefs[key] = value }
}

class StringField<T>(
  override val name: String,
  override val keyName: String,
  override val meta: SettingMeta?,
  private val getter: (T) -> String,
  private val setter: (T, String) -> T,
) : SettingField<T, String> {
  private val key = stringPreferencesKey(keyName)
  override fun get(model: T) = getter(model)
  override fun set(model: T, value: String) = setter(model, value)
  override fun read(prefs: Preferences) = prefs[key]
  override fun write(prefs: MutablePreferences, value: String) { prefs[key] = value }
}