package io.github.mlmgames.settings.core.annotations

import kotlin.reflect.KClass

/**
 * Defines an action handler for BUTTON type settings.
 *
 * Usage:
 * ```
 * @Setting(
 *     title = "Clear Cache",
 *     category = System::class,
 *     type = Button::class
 * )
 * @ActionHandler(ClearCacheAction::class)
 * val clearCache: Unit = Unit
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ActionHandler(
    val action: KClass<out SettingAction>
)

/**
 * Base interface for setting actions.
 */
interface SettingAction {
    /** Unique identifier for this action */
    val id: String get() = this::class.simpleName ?: "unknown"

    /** Whether this action requires confirmation */
    val requiresConfirmation: Boolean get() = false

    /** Confirmation dialog title */
    val confirmationTitle: String get() = "Confirm"

    /** Confirmation dialog message */
    val confirmationMessage: String get() = "Are you sure?"

    /** Whether this is a dangerous/destructive action */
    val isDangerous: Boolean get() = false
}