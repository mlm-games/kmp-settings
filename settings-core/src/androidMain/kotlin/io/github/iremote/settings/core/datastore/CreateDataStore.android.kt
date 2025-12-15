package io.github.iremote.settings.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createSettingsDataStore(context: Context, name: String): DataStore<Preferences> =
  createDataStore(
    producePath = {
      context.filesDir.resolve("$name.preferences_pb").absolutePath
    }
  )