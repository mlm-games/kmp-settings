package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.SettingPlatform
import io.github.mlmgames.settings.core.locale.AppLanguage
import io.github.mlmgames.settings.core.resources.NoOpStringResourceProvider
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

    @Test
    fun carriesLocalizationKeys() {
        assertEquals("settings.category.general", DropdownLabelSettingsSchema.categoryTitleKeys[General::class])
        val language = requireNotNull(DropdownLabelSettingsSchema.fieldByName("language")).meta
        assertEquals("settings.language.title", language?.titleKey)
        assertEquals("settings.language.options", language?.optionsKey)
    }

    @Test
    fun restrictsLanguageDropdownToDeclaredTags() {
        val field = requireNotNull(DropdownLabelSettingsSchema.fieldByName("appLanguage"))
        assertEquals(listOf("en", "de", "fr"), field.meta?.languages)
        assertEquals(
            listOf("System", "English", "Deutsch", "Français"),
            field.getDropdownOptions(NoOpStringResourceProvider),
        )
        assertEquals(0, field.toUiDropdownIndex(DropdownLabelSettings()))
        assertEquals(AppLanguage.English, field.fromUiDropdownIndex(1))
    }
}
