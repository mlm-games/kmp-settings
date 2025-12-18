package io.github.mlmgames.settings.core.actions

import io.github.mlmgames.settings.core.annotations.SettingAction
import kotlin.reflect.KClass

object ActionRegistry {
    private val handlers = mutableMapOf<KClass<out SettingAction>, suspend () -> Unit>()
    private val actionInstances = mutableMapOf<KClass<out SettingAction>, SettingAction>()

    fun <T : SettingAction> register(actionClass: KClass<T>, handler: suspend () -> Unit) {
        handlers[actionClass] = handler
    }

    inline fun <reified T : SettingAction> register(noinline handler: suspend () -> Unit) {
        register(T::class, handler)
    }

    /**
     * Register an action instance for later retrieval.
     * Call this at app startup for each action object.
     */
    fun <T : SettingAction> registerAction(actionClass: KClass<T>, instance: T) {
        actionInstances[actionClass] = instance
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
        handlers.clear()
        actionInstances.clear()
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