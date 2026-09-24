@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.mlmgames.settings.ui.util

import androidx.compose.runtime.*
import io.github.mlmgames.settings.core.FieldChangeListener
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Observe a specific setting field in Compose.
 */
@Composable
fun <T, V> SettingsRepository<T>.observeFieldAsState(
    fieldName: String,
    initial: V
): State<V> {
    val flow = remember(this, fieldName) { observeField<V>(fieldName) }
    return flow.collectAsState(initial = initial)
}

/**
 * React to setting changes with a side effect.
 */
@Composable
fun <T> OnSettingChanged(
    repository: SettingsRepository<T>,
    fieldName: String,
    onChange: (oldValue: Any?, newValue: Any?) -> Unit
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val scope = rememberCoroutineScope()

    DisposableEffect(repository, fieldName) {
        val gate = ListenerGate()
        val events = Channel<Pair<Any?, Any?>>(Channel.UNLIMITED)
        val callbackJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            for ((oldValue, newValue) in events) {
                if (!gate.isActive()) continue
                try {
                    currentOnChange(oldValue, newValue)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
        }
        val listener = FieldChangeListener<Any?> { oldValue, newValue ->
            if (gate.isActive()) events.trySend(oldValue to newValue)
        }

        repository.addFieldListener(fieldName, listener)
        onDispose {
            gate.deactivate()
            events.close()
            callbackJob.cancel()
            repository.removeFieldListener(fieldName, listener)
        }
    }
}

private class ListenerGate {
    private val active = AtomicBoolean(true)

    fun isActive(): Boolean = active.load()

    fun deactivate() {
        active.store(false)
    }
}
