package io.github.mlmgames.settings.core.annotations

import kotlin.reflect.KClass

/**
 * Validates numeric range for Int/Long/Float/Double fields.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Range(
    val min: Double = Double.MIN_VALUE,
    val max: Double = Double.MAX_VALUE,
    val errorMessage: String = "Value out of range",
    val errorMessageRes: Int = 0
)

/**
 * Validates string length.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Length(
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val errorMessage: String = "Invalid length",
    val errorMessageRes: Int = 0
)

/**
 * Validates string pattern.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Pattern(
    val regex: String,
    val errorMessage: String = "Invalid format",
    val errorMessageRes: Int = 0
)

/**
 * Marks field as required.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Required(
    val errorMessage: String = "This field is required",
    val errorMessageRes: Int = 0
)

/**
 * Custom validation using a validator class.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ValidatedBy(
    val validator: KClass<out SettingValidator<*>>
)

/**
 * Base validator interface.
 */
interface SettingValidator<T> {
    fun validate(value: T): ValidationResult
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(
        val message: String,
        val messageRes: Int = 0
    ) : ValidationResult()
}