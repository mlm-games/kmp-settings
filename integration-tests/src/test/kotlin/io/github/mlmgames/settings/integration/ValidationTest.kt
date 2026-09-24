package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.resources.NoOpStringResourceProvider
import kotlin.test.Test
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
}
