package io.github.mlmgames.settings.core

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

interface SettingField<T, V> {
  val name: String
  val keyName: String
  val meta: SettingMeta?

  fun get(model: T): V
  fun set(model: T, value: V): T

  fun read(prefs: Preferences): V?
  fun write(prefs: MutablePreferences, value: V)
}