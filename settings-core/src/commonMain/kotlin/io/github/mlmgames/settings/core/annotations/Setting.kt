package io.github.mlmgames.settings.core.annotations

import io.github.mlmgames.settings.core.types.Toggle
import kotlin.reflect.KClass

/**
 * Marks a property as a UI-visible setting.
 *
 * Usage:
 * ```
 * @Setting(
 *     title = "Dark Mode",
 *     category = Appearance::class,
 *     type = Toggle::class
 * )
 * val darkMode: Boolean = false
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Setting(
    /** Display title */
    val title: String = "",

    /** Optional description */
    val description: String = "",

    /** Category object class (must have @CategoryDefinition) */
    val category: KClass<*>,

    /** UI type object class (Toggle, Slider, Dropdown, Button, or custom) */
    val type: KClass<*> = Toggle::class,

    /** Stable persistence key. If empty, defaults to snake_case of property name. */
    val key: String = "",

    /** Property name this setting depends on. Setting is disabled when dependency is false. */
    val dependsOn: String = "",

    /** String resource ID for localized title */
    val titleRes: Int = 0,

    /** String resource ID for localized description */
    val descriptionRes: Int = 0,

    // Slider parameters
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,

    // Dropdown options
    val options: Array<String> = [],

    /** Resource array for localized dropdown options */
    val optionsRes: Int = 0,
)