package io.github.mlmgames.settings.core.annotations

/**
 * Marks a field that should NOT be reset.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class NoReset

/**
 * Marks a field that requires confirmation before reset.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ConfirmReset(
    val message: String = "Are you sure you want to reset this setting?"
)