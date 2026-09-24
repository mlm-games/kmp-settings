package io.github.mlmgames.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

private val wasmStores = mutableMapOf<String, DataStore<Preferences>>()

fun createSettingsDataStore(name: String): DataStore<Preferences> {
    requireSafeDataStoreName(name)
    return wasmStores.getOrPut(name) {
        createUncachedDataStore(name)
    }
}

internal actual fun createPreferencesStorage(path: String): Storage<Preferences> =
    WebLocalStorage(
        serializer = PreferencesSerializer,
        name = path
    )

internal actual val dataStoreContext: CoroutineContext =
    CoroutineScope(Dispatchers.Default + SupervisorJob()).coroutineContext

internal actual fun canonicalDataStorePath(path: String): String? = null

private fun createUncachedDataStore(path: String): DataStore<Preferences> {
    val storage = createPreferencesStorage(path)
    return DataStore.Builder(
        storage = storage,
        context = dataStoreContext
    ).build()
}