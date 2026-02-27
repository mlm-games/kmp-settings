package io.github.mlmgames.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createSettingsDataStore(name: String): DataStore<Preferences> =
    createDataStore(
        producePath = {
            "/$name.preferences_pb"
        }
    )
