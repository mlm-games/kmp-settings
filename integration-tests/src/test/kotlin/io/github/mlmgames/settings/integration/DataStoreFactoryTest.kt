package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.datastore.createSettingsDataStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DataStoreFactoryTest {
    @Test
    fun reusesDataStoreForTheSameCanonicalPath() {
        val name = "cache-${UUID.randomUUID()}"

        assertSame(createSettingsDataStore(name), createSettingsDataStore(name))
    }

    @Test
    fun rejectsPathLikeNames() {
        assertFailsWith<IllegalArgumentException> {
            createSettingsDataStore("../settings")
        }
    }
}
