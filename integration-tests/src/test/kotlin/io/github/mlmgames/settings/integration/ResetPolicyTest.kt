package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.managers.ResetManager
import io.github.mlmgames.settings.core.managers.SettingsSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ResetPolicyTest {
    @Test
    fun persistedNoResetFieldsAreNotResettable() = runTest {
        val store = TestDataStore.create("reset-policy")
        try {
            val manager = ResetManager(store.dataStore, IntegrationSettingsSchema)

            assertFalse(manager.resetField("revision"))
        } finally {
            store.close()
        }
    }

    @Test
    fun treatsNullEntriesInLegacySnapshotsAsAbsent() = runTest {
        val store = TestDataStore.create("legacy-snapshot")
        try {
            val repository = SettingsRepository(store.dataStore, IntegrationSettingsSchema)
            repository.set("enabled", true)
            val manager = ResetManager(store.dataStore, IntegrationSettingsSchema)

            manager.restoreSnapshot(SettingsSnapshot.fromLegacy(0, mapOf("enabled" to null)))

            assertEquals(false, repository.get<Boolean>("enabled"))
        } finally {
            store.close()
        }
    }
}
