package io.github.mlmgames.settings.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

fun createSettingsDataStore(context: Context, name: String): DataStore<Preferences> =
    createDataStore(
        producePath = {
            context.preferencesDataStoreFile(name).absolutePath
        }
    )