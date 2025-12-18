package io.github.mlmgames.settings.core.annotations

/**
 * Marks an object as a setting category.
 *
 * Usage:
 * ```
 * @CategoryDefinition(order = 0)
 * object General
 *
 * @CategoryDefinition(order = 1, titleRes = R.string.category_appearance)
 * object Appearance
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CategoryDefinition(
    /** Display order (lower = first) */
    val order: Int = 0,
    /** Optional string resource ID for localized title */
    val titleRes: Int = 0
)

/**
 * Marker interface for category objects.
 */
interface SettingCategoryMarker {
    val order: Int get() = 0
    val titleRes: Int get() = 0
}