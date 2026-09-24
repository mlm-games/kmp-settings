@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.mlmgames.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.CoroutineContext

internal expect fun createPreferencesStorage(path: String): Storage<Preferences>

internal expect val dataStoreContext: CoroutineContext

internal expect fun canonicalDataStorePath(path: String): String?

private val dataStoreCache = AtomicReference<Map<String, DataStore<Preferences>>>(emptyMap())

internal fun createDataStore(producePath: () -> String): DataStore<Preferences> {
    val path = producePath()
    val canonicalPath = canonicalDataStorePath(path)
    if (canonicalPath == null) return buildDataStore(path)

    while (true) {
        val current = dataStoreCache.load()
        current[canonicalPath]?.let { return it }
        val created = buildDataStore(canonicalPath)
        if (dataStoreCache.compareAndSet(current, current + (canonicalPath to created))) {
            return created
        }
    }
}

private fun buildDataStore(path: String): DataStore<Preferences> {
    val storage = createPreferencesStorage(path)
    return DataStore.Builder(
        storage = storage,
        context = dataStoreContext,
    ).build()
}

internal fun requireSafeDataStoreName(name: String) {
    require(name.isNotBlank()) { "DataStore name must not be blank" }
    require(name != "." && name != "..") { "DataStore name must not be a path traversal segment" }
    require(name.none { it == '/' || it == '\\' || it.code < 0x20 }) {
        "DataStore name must be a single path segment"
    }
    require(!(name.length >= 2 && name[1] == ':' && name[0].isLetter())) {
        "DataStore name must not be an absolute path"
    }
}
