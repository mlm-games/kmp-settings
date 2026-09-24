@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.mlmgames.settings.core.actions

import io.github.mlmgames.settings.core.annotations.SettingAction
import kotlin.concurrent.atomics.AtomicReference
import kotlin.reflect.KClass

object ActionRegistry {
    private data class RegistryState(
        val handlers: Map<KClass<out SettingAction>, suspend () -> Unit> = emptyMap(),
        val instances: Map<KClass<out SettingAction>, SettingAction> = emptyMap(),
    )

    private val state = AtomicReference(RegistryState())

    private inline fun updateState(transform: (RegistryState) -> RegistryState) {
        while (true) {
            val current = state.load()
            val updated = transform(current)
            if (state.compareAndSet(current, updated)) return
        }
    }

    fun <T : SettingAction> register(actionClass: KClass<T>, handler: suspend () -> Unit) {
        updateState { current ->
            current.copy(handlers = current.handlers + (actionClass to handler))
        }
    }

    inline fun <reified T : SettingAction> register(noinline handler: suspend () -> Unit) {
        register(T::class, handler)
    }

    fun unregister(actionClass: KClass<out SettingAction>) {
        updateState { current ->
            current.copy(
                handlers = current.handlers - actionClass,
                instances = current.instances - actionClass,
            )
        }
    }

    fun <T : SettingAction> registerAction(actionClass: KClass<T>, instance: T) {
        updateState { current ->
            current.copy(instances = current.instances + (actionClass to instance))
        }
    }

    inline fun <reified T : SettingAction> registerAction(instance: T) {
        registerAction(T::class, instance)
    }

    suspend fun execute(actionClass: KClass<out SettingAction>): Boolean {
        val handler = state.load().handlers[actionClass] ?: return false
        handler()
        return true
    }

    fun getAction(actionClass: KClass<out SettingAction>): SettingAction? =
        state.load().instances[actionClass]

    fun clear() {
        updateState { RegistryState() }
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
