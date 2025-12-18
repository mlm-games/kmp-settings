package io.github.mlmgames.settings.core.managers

import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

class UndoManager<T>(
    private val repository: SettingsRepository<T>,
    private val maxHistory: Int = 20,
) {
    private val undoStack = ArrayDeque<SettingChange>()
    private val redoStack = ArrayDeque<SettingChange>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun recordChange(fieldName: String, oldValue: Any?, newValue: Any?) {
        undoStack.addLast(SettingChange(fieldName, oldValue, newValue))

        while (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }

        redoStack.clear()
        updateState()
    }

    suspend fun undo(): Boolean {
        val change = undoStack.removeLastOrNull() ?: return false

        val oldValue = change.oldValue ?: return false
        repository.set(change.fieldName, oldValue)

        redoStack.addLast(change)
        updateState()
        return true
    }

    suspend fun redo(): Boolean {
        val change = redoStack.removeLastOrNull() ?: return false

        val newValue = change.newValue ?: return false
        repository.set(change.fieldName, newValue)

        undoStack.addLast(change)
        updateState()
        return true
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    fun getUndoDescription(): String? = undoStack.lastOrNull()?.let { "Undo: ${it.fieldName}" }
    fun getRedoDescription(): String? = redoStack.lastOrNull()?.let { "Redo: ${it.fieldName}" }

    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}

data class SettingChange(
    val fieldName: String,
    val oldValue: Any?,
    val newValue: Any?,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
)