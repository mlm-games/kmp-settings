package io.github.iremote.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

fun createSettingsDataStore(name: String): DataStore<Preferences> =
  createDataStore(
    producePath = {
      File(System.getProperty("java.io.tmpdir"), "$name.preferences_pb").absolutePath
    }
  )