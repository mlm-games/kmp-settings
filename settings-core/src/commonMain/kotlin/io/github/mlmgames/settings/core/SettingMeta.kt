package io.github.mlmgames.settings.core

import io.github.mlmgames.settings.core.annotations.SettingAction
import io.github.mlmgames.settings.core.annotations.SettingPlatform
import io.github.mlmgames.settings.core.annotations.SettingValidator
import io.github.mlmgames.settings.core.annotations.ValidationResult
import io.github.mlmgames.settings.core.resources.StringResourceProvider
import io.github.mlmgames.settings.core.resources.resolveString
import io.github.mlmgames.settings.core.resources.resolveStringArray
import io.github.mlmgames.settings.core.types.SettingTypes
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException

/**
 * Kind of backing value for UI types.
 */
enum class ValueKind {
    NONE,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    ENUM,
}

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

    // Value kind (backing type for UI)
    val valueKind: ValueKind = ValueKind.NONE,
    val enumTypeName: String? = null,

    // Persistence
    val key: String,

    // Dependencies
    val dependsOn: String,

    // Slider config
    val min: Float,
    val max: Float,
    val step: Float,

    // Dropdown config
    // For enums these are display-label overrides aligned by entry index
    // (raw labels default to humanized entry names); for Int/String fields
    // they are the option labels proper.
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

    val platforms: Set<SettingPlatform> = setOf(SettingPlatform.ALL),

    val titleKey: String = "",
    val descriptionKey: String = "",
    val optionsKey: String = "",
    val confirmResetKey: String = "",
    ) {
    val isBuiltInType: Boolean get() = SettingTypes.isBuiltIn(type)

    fun resolvedTitle(provider: StringResourceProvider): String =
        provider.resolveString(titleKey, titleRes, title)

    fun resolvedDescription(provider: StringResourceProvider): String =
        provider.resolveString(descriptionKey, descriptionRes, description)

    fun resolvedOptions(provider: StringResourceProvider): List<String> =
        provider.resolveStringArray(optionsKey, optionsRes, options)

    /**
     * Display labels for a dropdown row/dialog.
     *
     * Precedence: explicit `options`/`optionsRes`/`optionsKey` overrides (aligned
     * by index, so enum labels can be renamed/localized without touching storage)
     * win;
     * otherwise the field's own labels (enums: humanized entry names).
     */
    fun dropdownLabels(
        field: SettingField<*, *>,
        provider: StringResourceProvider,
    ): List<String> {
        val overrides = resolvedOptions(provider)
        if (overrides.isNotEmpty()) return overrides
        return field.getDropdownOptions().orEmpty()
    }

    fun isVisibleOnPlatform(currentPlatform: SettingPlatform): Boolean {
        if (platforms.contains(SettingPlatform.ALL)) return true
        if (platforms.contains(currentPlatform)) return true

        // DESKTOP is a group alias for JVM + Linux: a DESKTOP-marked setting is
        // visible on JVM/Linux, and a JVM/LINUX-marked setting is visible when
        // the runtime reports DESKTOP. WEB is standalone and never aliases.
        if (currentPlatform == SettingPlatform.JVM || currentPlatform == SettingPlatform.LINUX) {
            if (platforms.contains(SettingPlatform.DESKTOP)) return true
        }
        if (currentPlatform == SettingPlatform.DESKTOP) {
            if (platforms.contains(SettingPlatform.JVM)) return true
            if (platforms.contains(SettingPlatform.LINUX)) return true
        }

        return false
    }

    fun validate(value: Any?, provider: StringResourceProvider): ValidationResult {
        val rules = validation ?: return ValidationResult.Valid

        if (rules.required) {
            val isEmpty = when (value) {
                null -> true
                is String -> value.isBlank()
                is Collection<*> -> value.isEmpty()
                is Map<*, *> -> value.isEmpty()
                else -> false
            }
            if (isEmpty) {
                return ValidationResult.Invalid(
                    message = resolveErrorMessage(rules, provider).ifBlank { "This field is required" },
                    messageKey = rules.errorMessageKey,
                )
            }
        }

        rules.range?.let { range ->
            // Non-numeric values skip range (applicability is enforced at KSP
            // time); they are not range violations.
            val numValue = (value as? Number)?.toDouble() ?: return@let
            if (numValue.isNaN() || numValue !in range) {
                return ValidationResult.Invalid(
                    message = resolveErrorMessage(rules, provider).ifBlank { "Value out of range" },
                    messageKey = rules.errorMessageKey,
                )
            }
        }

        rules.length?.let { lengthRange ->
            val strValue = value as? String ?: return@let
            if (strValue.length !in lengthRange) {
                return ValidationResult.Invalid(
                    message = resolveErrorMessage(rules, provider).ifBlank { "Invalid length" },
                    messageKey = rules.errorMessageKey,
                )
            }
        }

        rules.pattern?.let { pattern ->
            val strValue = value as? String ?: return@let
            if (!pattern.matches(strValue)) {
                return ValidationResult.Invalid(
                    message = resolveErrorMessage(rules, provider).ifBlank { "Invalid format" },
                    messageKey = rules.errorMessageKey,
                )
            }
        }

        for (validator in rules.customValidators) {
            @Suppress("UNCHECKED_CAST")
            val result = try {
                (validator as SettingValidator<Any?>).validate(value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ValidationResult.Invalid("Validation failed: ${error.message ?: "unknown error"}")
            }
            if (result is ValidationResult.Invalid) {
                val resolved = provider.resolveString(result.messageKey, result.messageRes, result.message)
                return ValidationResult.Invalid(
                    message = resolved,
                    messageRes = result.messageRes,
                    messageKey = result.messageKey,
                )
            }
        }

        return ValidationResult.Valid
    }

    private fun resolveErrorMessage(rules: ValidationRules, provider: StringResourceProvider): String =
        provider.resolveString(rules.errorMessageKey, rules.errorMessageRes, rules.errorMessage)
}

data class ValidationRules(
    val range: ClosedFloatingPointRange<Double>? = null,
    val length: IntRange? = null,
    val pattern: Regex? = null,
    val required: Boolean = false,
    val errorMessage: String = "",
    val errorMessageRes: Int = 0,
    val customValidators: List<SettingValidator<*>> = emptyList(),
    val errorMessageKey: String = "",
) {
    constructor(
        range: ClosedFloatingPointRange<Double>?,
        length: IntRange?,
        pattern: Regex?,
        required: Boolean,
        errorMessage: String,
        errorMessageRes: Int,
    ) : this(
        range = range,
        length = length,
        pattern = pattern,
        required = required,
        errorMessage = errorMessage,
        errorMessageRes = errorMessageRes,
        customValidators = emptyList(),
    )
}

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
    val titleKey: String = "",
    val messageKey: String = "",
    val confirmTextKey: String = "",
    val cancelTextKey: String = "",
) {
    fun resolvedTitle(provider: StringResourceProvider): String =
        provider.resolveString(titleKey, titleRes, title)

    fun resolvedMessage(provider: StringResourceProvider): String =
        provider.resolveString(messageKey, messageRes, message)

    fun resolvedConfirmText(provider: StringResourceProvider): String =
        provider.resolveString(confirmTextKey, confirmTextRes, confirmText)

    fun resolvedCancelText(provider: StringResourceProvider): String =
        provider.resolveString(cancelTextKey, cancelTextRes, cancelText)
}