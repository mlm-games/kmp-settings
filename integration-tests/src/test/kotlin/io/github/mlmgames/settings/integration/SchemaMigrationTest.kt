package io.github.mlmgames.settings.integration

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.managers.Migration
import io.github.mlmgames.settings.core.managers.MigrationManager
import io.github.mlmgames.settings.core.managers.MigrationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SchemaMigrationTest {
    @Test
    fun appliesGeneratedFieldRename() = runTest {
        val store = TestDataStore.create("schema-rename")
        try {
            store.dataStore.edit { it[intPreferencesKey("old_count")] = 7 }
            val manager = MigrationManager(
                dataStore = store.dataStore,
                currentVersion = AliasSettingsSchema.schemaVersion,
                schema = AliasSettingsSchema,
            )

            assertIs<MigrationResult.Success>(manager.migrate())
            assertEquals(7, SettingsRepository(store.dataStore, AliasSettingsSchema).get<Int>("count"))
        } finally {
            store.close()
        }
    }

    @Test
    fun runsExplicitMigrationsBeforeGeneratedRenames() = runTest {
        val store = TestDataStore.create("schema-rename-order")
        try {
            store.dataStore.edit {
                it[intPreferencesKey("old_count")] = 9
            }
            val manager = MigrationManager(
                dataStore = store.dataStore,
                currentVersion = AliasSettingsSchema.schemaVersion,
                schema = AliasSettingsSchema,
            ).addMigration(object : Migration {
                override val fromVersion = 0
                override val toVersion = 1

                override suspend fun migrate(prefs: androidx.datastore.preferences.core.MutablePreferences) {
                    prefs[intPreferencesKey("seen_before_rename")] = prefs[intPreferencesKey("old_count")] ?: -1
                }
            })

            assertIs<MigrationResult.Success>(manager.migrate())
            assertEquals(9, store.dataStore.data.first()[intPreferencesKey("seen_before_rename")])
            assertEquals(9, SettingsRepository(store.dataStore, AliasSettingsSchema).get<Int>("count"))
        } finally {
            store.close()
        }
    }
}
