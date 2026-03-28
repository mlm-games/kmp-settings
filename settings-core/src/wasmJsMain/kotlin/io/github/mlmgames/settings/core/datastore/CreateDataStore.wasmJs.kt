package io.github.mlmgames.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.WebStorage
import androidx.datastore.core.okio.WebStorageType
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

private val wasmStores = mutableMapOf<String, DataStore<Preferences>>()

fun createSettingsDataStore(name: String): DataStore<Preferences> =
    wasmStores.getOrPut(name) {
        createDataStore(
            producePath = { "/$name.preferences_pb" }
        )
    }

internal actual fun createPreferencesStorage(path: String): Storage<Preferences> =
    WebStorage(
        serializer = PreferencesSerializer,
        name = path,
        storageType = WebStorageType.LOCAL
    )

internal actual val dataStoreContext: CoroutineContext =
    CoroutineScope(Dispatchers.Default + SupervisorJob()).coroutineContext