package io.github.mlmgames.settings.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepository<T>(
  private val dataStore: DataStore<Preferences>,
  private val schema: SettingsSchema<T>,
) {
  val flow: Flow<T> =
    dataStore.data
      .map { prefs ->
        var model = schema.default
        for (f in schema.fields) {
          @Suppress("UNCHECKED_CAST")
          val ff = f as SettingField<T, Any?>
          val v = ff.read(prefs)
          if (v != null) model = ff.set(model, v)
        }
        model
      }
      .distinctUntilChanged()

  suspend fun update(transform: (T) -> T) {
    val current = flow.first()
    val updated = transform(current)

    dataStore.updateData { prefs ->
      prefs
    }
    edit { mutablePrefs ->
      for (f in schema.fields) {
        @Suppress("UNCHECKED_CAST")
        val ff = f as SettingField<T, Any?>
        val oldV = ff.get(current)
        val newV = ff.get(updated)
        if (oldV != newV) ff.write(mutablePrefs, newV)
      }
    }
  }

  suspend fun set(name: String, value: Any) {
    val field = schema.fieldByName(name) ?: return
    edit { mutablePrefs ->
      @Suppress("UNCHECKED_CAST")
      (field as SettingField<T, Any>).write(mutablePrefs, value)
    }
  }

  private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
    androidx.datastore.preferences.core.edit(dataStore, block)
  }
}