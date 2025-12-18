package io.github.mlmgames.settings.core

import io.github.mlmgames.settings.core.annotations.SettingAction
import io.github.mlmgames.settings.core.annotations.ValidationResult
import io.github.mlmgames.settings.core.resources.StringResourceProvider
import io.github.mlmgames.settings.core.types.SettingTypes
import kotlin.reflect.KClass

/**
 * Runtime metadata for a setting field.
 */
data class SettingMeta(
    // Display
    val title: String,
    val description: String,
    val titleRes: Int,
    val descriptionRes: Int,

    // Classification
    val category: KClass<*>,
    val categoryOrder: Int,
    val type: KClass<*>,

    // Persistence
    val key: String,

    // Dependencies
    val dependsOn: String,

    // Slider config
    val min: Float,
    val max: Float,
    val step: Float,

    // Dropdown config
    val options: List<String>,
    val optionsRes: Int,

    // Action (for Button type)
    val actionClass: KClass<out SettingAction>? = null,

    // Validation
    val validation: ValidationRules? = null,

    // Confirmation
    val confirmation: ConfirmationConfig? = null,

    // Reset behavior
    val noReset: Boolean = false,
    val confirmReset: String? = null,
) {
    val isBuiltInType: Boolean get() = SettingTypes.isBuiltIn(type)

    fun resolvedTitle(provider: StringResourceProvider): String =
        if (titleRes != 0) provider.getString(titleRes) else title

    fun resolvedDescription(provider: StringResourceProvider): String =
        if (descriptionRes != 0) provider.getString(descriptionRes) else description

    fun resolvedOptions(provider: StringResourceProvider): List<String> =
        if (optionsRes != 0) provider.getStringArray(optionsRes) else options

    fun validate(value: Any?, provider: StringResourceProvider): ValidationResult {
        val rules = validation ?: return ValidationResult.Valid

        if (rules.required) {
            val isEmpty = when (value) {
                null -> true
                is String -> value.isBlank()
                is Collection<*> -> value.isEmpty()
                else -> false
            }
            if (isEmpty) {
                return ValidationResult.Invalid(resolveErrorMessage(rules, provider))
            }
        }

        rules.range?.let { range ->
            val numValue = (value as? Number)?.toDouble() ?: return@let
            if (numValue !in range) {
                return ValidationResult.Invalid(resolveErrorMessage(rules, provider))
            }
        }

        rules.length?.let { lengthRange ->
            val strValue = value as? String ?: return@let
            if (strValue.length !in lengthRange) {
                return ValidationResult.Invalid(resolveErrorMessage(rules, provider))
            }
        }

        rules.pattern?.let { pattern ->
            val strValue = value as? String ?: return@let
            if (!pattern.matches(strValue)) {
                return ValidationResult.Invalid(resolveErrorMessage(rules, provider))
            }
        }

        return ValidationResult.Valid
    }

    private fun resolveErrorMessage(rules: ValidationRules, provider: StringResourceProvider): String =
        if (rules.errorMessageRes != 0) provider.getString(rules.errorMessageRes)
        else rules.errorMessage
}

data class ValidationRules(
    val range: ClosedFloatingPointRange<Double>? = null,
    val length: IntRange? = null,
    val pattern: Regex? = null,
    val required: Boolean = false,
    val errorMessage: String = "",
    val errorMessageRes: Int = 0,
)

data class ConfirmationConfig(
    val title: String,
    val message: String,
    val titleRes: Int,
    val messageRes: Int,
    val confirmText: String,
    val confirmTextRes: Int,
    val cancelText: String,
    val cancelTextRes: Int,
    val isDangerous: Boolean,
) {
    fun resolvedTitle(provider: StringResourceProvider): String =
        if (titleRes != 0) provider.getString(titleRes) else title

    fun resolvedMessage(provider: StringResourceProvider): String =
        if (messageRes != 0) provider.getString(messageRes) else message

    fun resolvedConfirmText(provider: StringResourceProvider): String =
        if (confirmTextRes != 0) provider.getString(confirmTextRes) else confirmText

    fun resolvedCancelText(provider: StringResourceProvider): String =
        if (cancelTextRes != 0) provider.getString(cancelTextRes) else cancelText
}