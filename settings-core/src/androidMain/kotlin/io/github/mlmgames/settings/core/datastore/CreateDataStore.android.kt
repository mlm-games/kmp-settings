package io.github.mlmgames.settings.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun createSettingsDataStore(context: Context, name: String): DataStore<Preferences> =
    createDataStore(
        producePath = {
            context.preferencesDataStoreFile(name).absolutePath
        }
    )

internal actual fun createPreferencesStorage(path: String): Storage<Preferences> =
    FileStorage(
        serializer = PreferencesFileSerializer,
        produceFile = { File(path) }
    )

internal actual val dataStoreContext: CoroutineContext =
    CoroutineScope(Dispatchers.IO + SupervisorJob()).coroutineContext

