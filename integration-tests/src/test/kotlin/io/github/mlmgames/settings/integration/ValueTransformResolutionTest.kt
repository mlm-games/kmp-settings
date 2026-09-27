package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.PreferenceKind
import io.github.mlmgames.settings.core.managers.MigrationManager
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValueTransformResolutionTest {
    private fun manager(name: String, version: Int = 3) =
        MigrationManager(TestDataStore.create(name).dataStore, version, PreviewSettingsSchema)

    private fun MigrationManager.transform(target: String) =
        addValueTransform(2, 3, "gone", PreferenceKind.BOOLEAN, target) { "On" }

    @Test
    fun acceptsThePropertyName() {
        manager("resolve-name").transform("previews")
    }

    /** "longLevel" and "long_level" are one field under its two spellings. */
    @Test
    fun acceptsEitherSpellingWhenTheyDiffer() {
        val schema = MigrationManager(
            TestDataStore.create("resolve-long-name").dataStore,
            currentVersion = 1,
            schema = IntegrationSettingsSchema,
        )
        schema.addValueTransform(0, 1, "old", PreferenceKind.LONG, "longLevel") { "7" }

        val key = MigrationManager(
            TestDataStore.create("resolve-long-key").dataStore,
            currentVersion = 1,
            schema = IntegrationSettingsSchema,
        )
        key.addValueTransform(0, 1, "old", PreferenceKind.LONG, "long_level") { "7" }
    }

    @Test
    fun anUnknownFieldListsTheResolvableNames() {
        val error = assertFailsWith<IllegalArgumentException> { manager("resolve-miss").transform("nope") }
        val message = error.message.orEmpty()
        assertTrue("previews" in message, message)
        assertTrue("property name" in message, message)
    }

    @Test
    fun anAbsentSchemaIsReportedSeparately() {
        val error = assertFailsWith<IllegalArgumentException> {
            MigrationManager(TestDataStore.create("resolve-no-schema").dataStore, 3)
                .addValueTransform(2, 3, "gone", PreferenceKind.BOOLEAN, "previews") { "On" }
        }
        assertTrue("without a schema" in error.message.orEmpty(), error.message.orEmpty())
    }
}
