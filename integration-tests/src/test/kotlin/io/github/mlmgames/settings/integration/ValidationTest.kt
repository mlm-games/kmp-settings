package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.resources.NoOpStringResourceProvider
import io.github.mlmgames.settings.core.resources.StringResourceProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidationTest {
    @Test
    fun runsCustomValidators() {
        val field = requireNotNull(ValidatedSettingsSchema.fieldByName("enabled"))

        assertIs<io.github.mlmgames.settings.core.annotations.ValidationResult.Invalid>(
            field.meta!!.validate(false, NoOpStringResourceProvider),
        )
        val optional = requireNotNull(ValidatedSettingsSchema.fieldByName("optional"))
        assertIs<io.github.mlmgames.settings.core.annotations.ValidationResult.Valid>(
            optional.meta!!.validate(null, NoOpStringResourceProvider),
        )
    }

    @Test
    fun resolvesCustomValidationKeys() {
        val field = requireNotNull(ValidatedSettingsSchema.fieldByName("enabled"))
        val provider = object : StringResourceProvider {
            override fun getString(resId: Int): String = "legacy"
            override fun getString(resId: Int, vararg formatArgs: Any): String = "legacy"
            override fun getStringArray(resId: Int): List<String> = emptyList()
            override fun getString(key: String): String =
                if (key == "settings.validation.enabled") "translated" else ""
        }

        val result = field.meta!!.validate(false, provider)
        assertIs<io.github.mlmgames.settings.core.annotations.ValidationResult.Invalid>(result)
        assertEquals("translated", result.message)
    }
}
