package io.github.mlmgames.settings.integration

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.mlmgames.settings.core.managers.Migration
import io.github.mlmgames.settings.core.managers.MigrationManager
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationConcurrencyTest {
    @Test
    fun appliesMigrationOnceAcrossManagers() = runTest {
        val store = TestDataStore.create("migration-race")
        try {
            val invocations = AtomicInteger()
            val migration = object : Migration {
                override val fromVersion = 0
                override val toVersion = 1

                override suspend fun migrate(prefs: MutablePreferences) {
                    invocations.incrementAndGet()
                    prefs[intPreferencesKey("legacyCounter")] = 1
                }
            }
            val first = MigrationManager(store.dataStore, 1).addMigration(migration)
            val second = MigrationManager(store.dataStore, 1).addMigration(migration)

            awaitAll(async { first.migrate() }, async { second.migrate() })

            assertEquals(1, invocations.get())
            assertEquals(1, store.dataStore.data.first()[intPreferencesKey("legacyCounter")])
        } finally {
            store.close()
        }
    }
}
