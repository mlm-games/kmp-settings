package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.SettingValidator
import io.github.mlmgames.settings.core.annotations.ValidatedBy
import io.github.mlmgames.settings.core.annotations.ValidationResult
import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.types.Toggle
import kotlinx.serialization.Serializable

class PositiveBooleanValidator : SettingValidator<Boolean> {
    override fun validate(value: Boolean): ValidationResult =
        if (value) ValidationResult.Valid else ValidationResult.Invalid("Must be enabled")
}

class NullableBooleanValidator : SettingValidator<Boolean?> {
    override fun validate(value: Boolean?): ValidationResult =
        if (value == null) ValidationResult.Valid else ValidationResult.Valid
}

@Serializable
data class ValidatedSettings(
    @Setting(title = "Validated", category = General::class, type = Toggle::class)
    @ValidatedBy(PositiveBooleanValidator::class)
    val enabled: Boolean = true,
    @Setting(title = "Optional", category = General::class, type = Toggle::class)
    @ValidatedBy(NullableBooleanValidator::class)
    val optional: Boolean? = null,
)
