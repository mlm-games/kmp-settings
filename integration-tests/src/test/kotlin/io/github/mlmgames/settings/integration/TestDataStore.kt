package io.github.mlmgames.settings.integration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class TestDataStore private constructor(
    val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val directory: java.io.File,
) : AutoCloseable {
    override fun close() {
        scope.cancel()
        directory.deleteRecursively()
    }

    companion object {
        fun create(name: String): TestDataStore {
            val directory = Files.createTempDirectory("kmp-settings-$name").toFile()
            val file = directory.resolve("$name.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
            return TestDataStore(dataStore, scope, directory)
        }
    }
}
