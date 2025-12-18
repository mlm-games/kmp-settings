package io.github.mlmgames.settings.ui.util

import androidx.compose.runtime.*
import io.github.mlmgames.settings.core.FieldChangeListener
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.flow.collectLatest

/**
 * Observe a specific setting field in Compose.
 */
@Composable
fun <T, V> SettingsRepository<T>.observeFieldAsState(
    fieldName: String,
    initial: V
): State<V> {
    val flow = remember(fieldName) { observeField<V>(fieldName) }
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
    DisposableEffect(repository, fieldName) {
        val listener = FieldChangeListener<Any?> { old, new ->
            onChange(old, new)
        }
        repository.addFieldListener(fieldName, listener)

        onDispose {
            repository.removeFieldListener(fieldName, listener)
        }
    }
}