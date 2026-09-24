package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.SettingPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SchemaCompilationTest {
    @Test
    fun runsOnTheSupportedJvmBaseline() {
        check(Runtime.version().feature() >= 17)
    }

    @Test
    fun generatesStableSchema() {
        assertEquals("enabled", IntegrationSettingsSchema.fieldByName("enabled")?.name)
        assertEquals("level", IntegrationSettingsSchema.fieldByKey("level")?.name)
        assertEquals("when", IntegrationSettingsSchema.fieldByName("when")?.name)
        assertEquals(16, IntegrationSettingsSchema.fields.size)
        assertFalse(IntegrationSettingsSchema.resettableFields().any { it.name == "revision" })
        assertEquals(7, IntegrationSettingsSchema.visibleUiFields(SettingPlatform.JVM).size)
    }
}
