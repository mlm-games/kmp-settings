package io.github.mlmgames.settings.integration

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryTest {
    @Test
    fun notifiesListenersForCommittedExternalChanges() = runTest {
        val store = TestDataStore.create("external-listener")
        try {
            val repository = SettingsRepository(store.dataStore, MutableIntegrationSettingsSchema)
            repository.get<Int>("count")
            val change = CompletableDeferred<Pair<Int, Int>>()
            repository.addFieldListener<Int>("count") { oldValue, newValue ->
                change.complete(oldValue to newValue)
            }

            store.dataStore.edit { it[intPreferencesKey("count")] = 4 }

            assertEquals(0 to 4, withContext(Dispatchers.Default) {
                withTimeout(2_000) { change.await() }
            })
        } finally {
            store.close()
        }
    }

    @Test
    fun keepsLegacyPhysicalKeysReadableAfterWrites() = runTest {
        val store = TestDataStore.create("downgrade-compatible-write")
        try {
            val repository = SettingsRepository(store.dataStore, IntegrationSettingsSchema)

            repository.set("enabled", true)

            assertEquals(true, store.dataStore.data.first()[booleanPreferencesKey("enabled")])
        } finally {
            store.close()
        }
    }

    @Test
    fun persistsExplicitNullWhenDefaultIsNull() = runTest {
        val store = TestDataStore.create("explicit-null")
        try {
            val repository = SettingsRepository(store.dataStore, ExplicitNullSettingsSchema)

            repository.set("value", null)

            assertTrue(requireNotNull(ExplicitNullSettingsSchema.fieldByName("value")).hasValue(store.dataStore.data.first()))
        } finally {
            store.close()
        }
    }

    @Test
    fun preservesNullableMarkerValues() = runTest {
        val store = TestDataStore.create("nullable-marker")
        try {
            val repository = SettingsRepository(store.dataStore, IntegrationSettingsSchema)

            repository.set("optionalText", "__NULL__")
            repository.set("optionalFloat", Float.NaN)

            assertEquals("__NULL__", repository.get<String?>("optionalText"))
            assertTrue(repository.get<Float?>("optionalFloat")!!.isNaN())
        } finally {
            store.close()
        }
    }

    @Test
    fun persistsNativeMapFields() = runTest {
        val store = TestDataStore.create("map-fields")
        try {
            val repository = SettingsRepository(store.dataStore, CollectionSettingsSchema)
            val values = mapOf(1 to "one", 2 to "two")

            repository.set("intValues", values)

            assertEquals(values, repository.get<Map<Int, String>>("intValues"))
        } finally {
            store.close()
        }
    }

    @Test
    fun persistsNullableSerializedCollections() = runTest {
        val store = TestDataStore.create("serialized-collection")
        try {
            val repository = SettingsRepository(store.dataStore, CollectionSettingsSchema)

            repository.set("optionalNames", listOf("one", "two"))

            assertEquals(listOf("one", "two"), repository.get<List<String>?>("optionalNames"))
        } finally {
            store.close()
        }
    }

    @Test
    fun persistsCustomSerializedValues() = runTest {
        val store = TestDataStore.create("custom-serializer")
        try {
            val repository = SettingsRepository(store.dataStore, CustomSettingsSchema)

            repository.set("config", CustomConfig(7))

            assertEquals(CustomConfig(7), repository.get<CustomConfig>("config"))
        } finally {
            store.close()
        }
    }

    @Test
    fun persistsInPlaceMutableTransforms() = runTest {
        val store = TestDataStore.create("mutable-update")
        try {
            val repository = SettingsRepository(store.dataStore, MutableIntegrationSettingsSchema)

            repository.update { settings ->
                settings.count = 7
                settings
            }

            assertEquals(7, repository.get<Int>("count"))
        } finally {
            store.close()
        }
    }
}
