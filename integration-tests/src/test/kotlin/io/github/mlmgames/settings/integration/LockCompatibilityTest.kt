package io.github.mlmgames.settings.integration

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.managers.SettingsLockManager
import io.github.mlmgames.settings.core.managers.UnlockResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LockCompatibilityTest {
    @Test
    fun acceptsLegacyPinAndLockedTimeout() = runTest {
        val store = TestDataStore.create("legacy-lock")
        try {
            store.dataStore.edit { preferences ->
                preferences[booleanPreferencesKey("__settings_lock_enabled__")] = true
                preferences[stringPreferencesKey("__settings_pin_hash__")] = "1234".hashCode().toString(16)
                preferences[longPreferencesKey("__settings_lock_timeout__")] = 0L
            }
            val manager = SettingsLockManager(store.dataStore)

            assertTrue(manager.isLocked.first())
            assertEquals(UnlockResult.InvalidPin, manager.unlock("1234"))
            assertTrue(manager.isLocked.first())
        } finally {
            store.close()
        }
    }

    @Test
    fun treatsMissingLegacyTimeoutAsLocked() = runTest {
        val store = TestDataStore.create("missing-timeout")
        try {
            store.dataStore.edit { preferences ->
                preferences[booleanPreferencesKey("__settings_lock_enabled__")] = true
            }
            assertTrue(SettingsLockManager(store.dataStore).isLocked.first())
        } finally {
            store.close()
        }
    }

    @Test
    fun emitsWhenTimeoutExpires() = runTest {
        val store = TestDataStore.create("lock-timeout")
        try {
            store.dataStore.edit { preferences ->
                preferences[booleanPreferencesKey("__settings_lock_enabled__")] = true
                preferences[longPreferencesKey("__settings_lock_timeout__")] = 50L
                preferences[longPreferencesKey("__settings_last_unlock__")] = System.currentTimeMillis()
            }
            val states = withTimeout(2_000) {
                SettingsLockManager(store.dataStore).isLocked.take(2).toList()
            }

            assertEquals(listOf(false, true), states)
        } finally {
            store.close()
        }
    }
}
