package io.github.mlmgames.settings.integration

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.mlmgames.settings.core.PreferenceKind
import io.github.mlmgames.settings.core.SettingFieldStorage
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.managers.Migration
import io.github.mlmgames.settings.core.managers.MigrationManager
import io.github.mlmgames.settings.core.managers.MigrationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val LEGACY_KEY = "block_media_previews"

class ValueTransformMigrationTest {
    private fun legacyNamespaced() =
        booleanPreferencesKey(SettingFieldStorage.valueKeyName(LEGACY_KEY, "boolean"))

    private fun manager(store: TestDataStore) = MigrationManager(
        dataStore = store.dataStore,
        currentVersion = 3,
        schema = PreviewSettingsSchema,
    ).addValueTransform(
        fromVersion = 2,
        toVersion = 3,
        oldKey = LEGACY_KEY,
        oldKind = PreferenceKind.BOOLEAN,
        newField = "previews",
    ) { blocked -> if (blocked == true) "Off" else "On" }

    @Test
    fun convertsLegacyBooleanIntoEnumAndClearsBothPhysicalKeys() = runTest {
        val store = TestDataStore.create("value-transform")
        try {
            store.dataStore.edit {
                it[legacyNamespaced()] = true
                it[booleanPreferencesKey(LEGACY_KEY)] = true
            }

            assertIs<MigrationResult.Success>(manager(store).migrate())
            assertEquals(
                PreviewMode.Off,
                SettingsRepository(store.dataStore, PreviewSettingsSchema).get<PreviewMode>("previews"),
            )
            assertTrue(legacyNamespaced() !in store.dataStore.data.first())
            assertTrue(booleanPreferencesKey(LEGACY_KEY) !in store.dataStore.data.first())
        } finally {
            store.close()
        }
    }

    @Test
    fun readsLegacyPlainKeyWhenTheNamespacedOneIsAbsent() = runTest {
        val store = TestDataStore.create("value-transform-legacy")
        try {
            store.dataStore.edit { it[booleanPreferencesKey(LEGACY_KEY)] = true }

            assertIs<MigrationResult.Success>(manager(store).migrate())
            assertEquals(
                PreviewMode.Off,
                SettingsRepository(store.dataStore, PreviewSettingsSchema).get<PreviewMode>("previews"),
            )
        } finally {
            store.close()
        }
    }

    /**
     * A platform that never ran a manager has no recorded version, so the first registered
     * migration starts above it. That leading span precedes the chain, so the migration must
     * still run.
     */
    @Test
    fun fillsLeadingGapForAStoreWithNoRecordedVersion() = runTest {
        val store = TestDataStore.create("value-transform-gap")
        try {
            store.dataStore.edit { it[legacyNamespaced()] = true }

            assertIs<MigrationResult.Success>(manager(store).migrate())
            assertEquals(
                PreviewMode.Off,
                SettingsRepository(store.dataStore, PreviewSettingsSchema).get<PreviewMode>("previews"),
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun fillsLeadingGapForAStoreWithRecordedHistory() = runTest {
        val store = TestDataStore.create("value-transform-upgrade")
        try {
            store.dataStore.edit {
                it[intPreferencesKey("__schema_version__")] = 1
                it[legacyNamespaced()] = true
            }

            assertIs<MigrationResult.Success>(manager(store).migrate())
            assertEquals(
                PreviewMode.Off,
                SettingsRepository(store.dataStore, PreviewSettingsSchema).get<PreviewMode>("previews"),
            )
        } finally {
            store.close()
        }
    }

    /**
     * An interior gap means a migration between two registered ones was forgotten. Stepping
     * over it would run the chain with a hole in it, so it stays an error.
     */
    @Test
    fun reportsInteriorGapAsChainBroken() = runTest {
        val store = TestDataStore.create("value-transform-interior-gap")
        try {
            store.dataStore.edit { it[legacyNamespaced()] = true }
            val manager = MigrationManager(
                dataStore = store.dataStore,
                currentVersion = 3,
                schema = PreviewSettingsSchema,
            )
                .addMigration(object : Migration {
                    override val fromVersion = 0
                    override val toVersion = 1

                    override suspend fun migrate(prefs: MutablePreferences) = Unit
                })
                .addValueTransform(
                    fromVersion = 2,
                    toVersion = 3,
                    oldKey = LEGACY_KEY,
                    oldKind = PreferenceKind.BOOLEAN,
                    newField = "previews",
                ) { blocked -> if (blocked == true) "Off" else "On" }

            val result = manager.migrate()
            assertIs<MigrationResult.ChainBroken>(result)
            assertTrue(result.gaps.any { it.startsWith("gap 1->2") }, "gaps: ${result.gaps}")
        } finally {
            store.close()
        }
    }

    @Test
    fun leavesPreferencesAloneWhenTransformDeclinesTheValue() = runTest {
        val store = TestDataStore.create("value-transform-declined")
        try {
            store.dataStore.edit { it[legacyNamespaced()] = true }
            val manager = MigrationManager(
                dataStore = store.dataStore,
                currentVersion = 3,
                schema = PreviewSettingsSchema,
            ).addValueTransform(
                fromVersion = 2,
                toVersion = 3,
                oldKey = LEGACY_KEY,
                oldKind = PreferenceKind.BOOLEAN,
                newField = "previews",
            ) { null }

            assertIs<MigrationResult.Success>(manager.migrate())
            val repository = SettingsRepository(store.dataStore, PreviewSettingsSchema)
            assertEquals(PreviewMode.On, repository.get<PreviewMode>("previews"))
            assertTrue(legacyNamespaced() in store.dataStore.data.first())
        } finally {
            store.close()
        }
    }

    @Test
    fun doesNothingWhenTheLegacyValueIsAbsent() = runTest {
        val store = TestDataStore.create("value-transform-absent")
        try {
            assertIs<MigrationResult.Success>(manager(store).migrate())
            assertEquals(
                PreviewMode.On,
                SettingsRepository(store.dataStore, PreviewSettingsSchema).get<PreviewMode>("previews"),
            )
        } finally {
            store.close()
        }
    }
}
