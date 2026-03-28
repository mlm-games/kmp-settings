package io.github.mlmgames.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences
import kotlin.coroutines.CoroutineContext

internal expect fun createPreferencesStorage(path: String): Storage<Preferences>

internal expect val dataStoreContext: CoroutineContext

internal fun createDataStore(producePath: () -> String): DataStore<Preferences> {
    val storage = createPreferencesStorage(producePath())
    return DataStore.Builder(
        storage = storage,
        context = dataStoreContext
    ).build()
}
