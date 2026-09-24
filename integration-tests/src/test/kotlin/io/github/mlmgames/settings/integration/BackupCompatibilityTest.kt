package io.github.mlmgames.settings.integration

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.backup.ExportResult
import io.github.mlmgames.settings.core.backup.ImportResult
import io.github.mlmgames.settings.core.backup.SettingsBackupManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupCompatibilityTest {
    @Test
    fun roundTripsExplicitNullValues() = runTest {
        val source = TestDataStore.create("null-source")
        val destination = TestDataStore.create("null-destination")
        try {
            val sourceRepository = SettingsRepository(source.dataStore, IntegrationSettingsSchema)
            sourceRepository.set("optionalInt", null)
            sourceRepository.set("optionalText", null)
            val exporter = SettingsBackupManager(
                dataStore = source.dataStore,
                schema = IntegrationSettingsSchema,
                appId = "io.github.mlmgames.settings.integration",
                schemaVersion = 1,
            )
            val exported = assertIs<ExportResult.Success>(exporter.export()).json
            val importer = SettingsBackupManager(
                dataStore = destination.dataStore,
                schema = IntegrationSettingsSchema,
                appId = "io.github.mlmgames.settings.integration",
                schemaVersion = 1,
            )

            assertIs<ImportResult.Success>(importer.import(exported))

            val destinationRepository = SettingsRepository(destination.dataStore, IntegrationSettingsSchema)
            assertEquals(null, destinationRepository.get<Int?>("optionalInt"))
            assertEquals(null, destinationRepository.get<String?>("optionalText"))
        } finally {
            source.close()
            destination.close()
        }
    }

    @Test
    fun importsLegacyChecksumAndUnknownValues() = runTest {
        val store = TestDataStore.create("legacy-backup")
        try {
            val repository = SettingsRepository(store.dataStore, IntegrationSettingsSchema)
            val manager = SettingsBackupManager(
                dataStore = store.dataStore,
                schema = IntegrationSettingsSchema,
                appId = "io.github.mlmgames.settings.integration",
                schemaVersion = 1,
            )
            val json = requireNotNull(javaClass.getResource("/legacy-backup.json")).readText()

            val result = manager.import(json)

            assertIs<ImportResult.Success>(result)
            assertTrue(result.errors.isEmpty())
            assertEquals(true, repository.get<Boolean>("enabled"))
            assertTrue(assertIs<ExportResult.Success>(manager.export()).json.contains("future"))
        } finally {
            store.close()
        }
    }

    @Test
    fun preservesLegacyNullableStringMarkerValues() = runTest {
        val store = TestDataStore.create("legacy-null-string")
        try {
            store.dataStore.edit { it[stringPreferencesKey("optional_text_nullable")] = "__NULL__" }
            val repository = SettingsRepository(store.dataStore, IntegrationSettingsSchema)
            val manager = SettingsBackupManager(
                dataStore = store.dataStore,
                schema = IntegrationSettingsSchema,
                appId = "io.github.mlmgames.settings.integration",
                schemaVersion = 1,
            )

            assertEquals("__NULL__", repository.get<String?>("optionalText"))
            assertTrue(assertIs<ExportResult.Success>(manager.export()).json.contains("s1:__NULL__"))
        } finally {
            store.close()
        }
    }

    @Test
    fun quarantinesInvalidKnownEnumValues() = runTest {
        val store = TestDataStore.create("future-enum")
        try {
            store.dataStore.edit { it[stringPreferencesKey("mode")] = "FUTURE" }
            val manager = SettingsBackupManager(
                dataStore = store.dataStore,
                schema = IntegrationSettingsSchema,
                appId = "io.github.mlmgames.settings.integration",
                schemaVersion = 1,
            )

            val exported = assertIs<ExportResult.Success>(manager.export()).json
            assertTrue(exported.contains("quarantinedSettings"))
            assertTrue(exported.contains("FUTURE"))
        } finally {
            store.close()
        }
    }
}
