package io.github.mlmgames.settings.core.annotations

/**
 * Requires confirmation before changing this setting.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class RequiresConfirmation(
    val title: String = "Confirm Change",
    val message: String = "Are you sure you want to change this setting?",
    val titleRes: Int = 0,
    val messageRes: Int = 0,
    val confirmText: String = "Confirm",
    val confirmTextRes: Int = 0,
    val cancelText: String = "Cancel",
    val cancelTextRes: Int = 0,
    val isDangerous: Boolean = false
)