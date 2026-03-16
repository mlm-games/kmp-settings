package io.github.mlmgames.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

private val wasmStores = mutableMapOf<String, DataStore<Preferences>>()

fun createSettingsDataStore(name: String): DataStore<Preferences> =
    wasmStores.getOrPut(name) {
        createDataStore(
            producePath = { "/$name.preferences_pb" }
        )
    }