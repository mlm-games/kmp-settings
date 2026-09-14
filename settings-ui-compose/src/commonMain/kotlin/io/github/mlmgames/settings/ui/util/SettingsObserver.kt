package io.github.mlmgames.settings.ui.util

import androidx.compose.runtime.*
import io.github.mlmgames.settings.core.FieldChangeListener
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Observe a specific setting field in Compose.
 */
@Composable
fun <T, V> SettingsRepository<T>.observeFieldAsState(
    fieldName: String,
    initial: V
): State<V> {
    // Repository is part of the key: a new repository instance must produce a
    // new flow instead of collecting the stale one.
    val flow = remember(this, fieldName) { observeField<V>(fieldName) }
    return flow.collectAsState(initial = initial)
}

/**
 * React to setting changes with a side effect.
 *
 * FieldChangeListener is a suspend callback invoked from the repository's
 * post-commit notification (caller coroutine, never the DataStore write
 * actor). The callback is marshalled to Main via rememberUpdatedState +
 * coroutine scope so Compose state mutation and navigation are safe, and the
 * latest [onChange] lambda is always used (no stale closure).
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
        val listener = FieldChangeListener<Any?> { old, new ->
            scope.launch {
                currentOnChange(old, new)
            }.join()
        }
        scope.launch { repository.addFieldListener(fieldName, listener) }

        onDispose {
            scope.launch { repository.removeFieldListener(fieldName, listener) }
        }
    }
}
