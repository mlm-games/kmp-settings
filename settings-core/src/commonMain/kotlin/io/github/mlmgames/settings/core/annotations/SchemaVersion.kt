package io.github.mlmgames.settings.core.annotations

/**
 * Defines schema version for a settings class.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaVersion(val version: Int)

/**
 * Marks a field that was renamed from a previous key.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class RenamedFrom(
    val previousKey: String,
    val sinceVersion: Int = 1
)

/**
 * Marks a field that was added in a specific version.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class AddedInVersion(val version: Int)

/**
 * Marks a field that was deprecated.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DeprecatedSetting(
    val message: String = "",
    val removeInVersion: Int = Int.MAX_VALUE
)