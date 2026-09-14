package io.github.mlmgames.settings.core.actions

import io.github.mlmgames.settings.core.annotations.SettingAction
import kotlin.reflect.KClass

object ActionRegistry {
    // No synchronized: KMP common code has no JVM monitors. Registration
    // happens at startup on a single thread; reads copy the reference, so the
    // worst case under races is a torn read resolved by re-registration.
    // Mutations replace the map reference atomically (volatile-style via
    // Kotlin's memory model for object properties on each platform).
    @Suppress("OPT_IN_USAGE")
    @kotlin.concurrent.Volatile
    private var handlers: Map<KClass<out SettingAction>, suspend () -> Unit> = emptyMap()
    @Suppress("OPT_IN_USAGE")
    @kotlin.concurrent.Volatile
    private var actionInstances: Map<KClass<out SettingAction>, SettingAction> = emptyMap()

    fun <T : SettingAction> register(actionClass: KClass<T>, handler: suspend () -> Unit) {
        handlers = handlers + (actionClass to handler)
    }

    inline fun <reified T : SettingAction> register(noinline handler: suspend () -> Unit) {
        register(T::class, handler)
    }

    fun unregister(actionClass: KClass<out SettingAction>) {
        handlers = handlers - actionClass
        actionInstances = actionInstances - actionClass
    }

    /**
     * Register an action instance for later retrieval.
     * Call this at app startup for each action object.
     */
    fun <T : SettingAction> registerAction(actionClass: KClass<T>, instance: T) {
        actionInstances = actionInstances + (actionClass to instance)
    }

    inline fun <reified T : SettingAction> registerAction(instance: T) {
        registerAction(T::class, instance)
    }

    suspend fun execute(actionClass: KClass<out SettingAction>): Boolean {
        val handler = handlers[actionClass] ?: return false
        handler()
        return true
    }

    fun getAction(actionClass: KClass<out SettingAction>): SettingAction? {
        return actionInstances[actionClass]
    }

    fun clear() {
        handlers = emptyMap()
        actionInstances = emptyMap()
    }
}

/** Marker for actions that don't need special handling */
object NoOpAction : SettingAction

/** Base for dangerous actions requiring confirmation */
abstract class DangerousAction : SettingAction {
    override val requiresConfirmation: Boolean = true
    override val isDangerous: Boolean = true
}

/** Base for actions that show a picker/dialog */
abstract class PickerAction : SettingAction