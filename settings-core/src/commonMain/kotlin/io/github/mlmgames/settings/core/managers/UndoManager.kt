package io.github.mlmgames.settings.core.managers

import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class UndoManager<T>(
    private val repository: SettingsRepository<T>,
    private val maxHistory: Int = 20,
) {
    private val mutex = Mutex()
    private val undoStack = ArrayDeque<SettingChange>()
    private val redoStack = ArrayDeque<SettingChange>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    suspend fun recordChange(fieldName: String, oldValue: Any?, newValue: Any?) {
        if (oldValue == newValue) return
        mutex.withLock {
            undoStack.addLast(SettingChange(fieldName, oldValue, newValue))

            while (undoStack.size > maxHistory) {
                undoStack.removeFirst()
            }

            redoStack.clear()
            updateState()
        }
    }

    /**
     * Undo pops only after a successful repository write. Nulls are first-class:
     * undoing to null clears back to explicit null (never drops the entry).
     */
    suspend fun undo(): Boolean {
        val change = mutex.withLock { undoStack.lastOrNull() } ?: return false
        return try {
            repository.set(change.fieldName, change.oldValue)
            mutex.withLock {
                // Remove the exact entry; another thread may have recorded meanwhile.
                if (undoStack.lastOrNull() == change) undoStack.removeLast()
                else undoStack.remove(change)
                redoStack.addLast(change)
                updateState()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun redo(): Boolean {
        val change = mutex.withLock { redoStack.lastOrNull() } ?: return false
        return try {
            repository.set(change.fieldName, change.newValue)
            mutex.withLock {
                if (redoStack.lastOrNull() == change) redoStack.removeLast()
                else redoStack.remove(change)
                undoStack.addLast(change)
                updateState()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearHistory() {
        mutex.withLock {
            undoStack.clear()
            redoStack.clear()
            updateState()
        }
    }

    suspend fun getUndoDescription(): String? = mutex.withLock {
        undoStack.lastOrNull()?.let { "Undo: ${it.fieldName}" }
    }

    suspend fun getRedoDescription(): String? = mutex.withLock {
        redoStack.lastOrNull()?.let { "Redo: ${it.fieldName}" }
    }

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
