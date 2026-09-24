package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.backup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@Composable
fun <T> ExportSettingsDialog(
    backupManager: SettingsBackupManager<T>,
    onExport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnExport by rememberUpdatedState(onExport)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var isExporting by remember(backupManager) { mutableStateOf(true) }
    var result by remember(backupManager) { mutableStateOf<ExportResult?>(null) }
    var shared by remember(backupManager) { mutableStateOf(false) }
    var shareError by remember(backupManager) { mutableStateOf<String?>(null) }

    LaunchedEffect(backupManager) {
        isExporting = true
        result = null
        shared = false
        shareError = null
        result = try {
            backupManager.export()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ExportResult.Error(error.message ?: "Export failed")
        }
        isExporting = false
    }

    AlertDialog(
        onDismissRequest = { if (!isExporting) currentOnDismiss() },
        title = { Text("Export Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (val value = result) {
                    null -> if (isExporting) CircularProgressIndicator()
                    is ExportResult.Success -> {
                        Text("Settings exported successfully!")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Size: ${value.json.length} characters",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is ExportResult.Error -> Text("Export failed: ${value.message}")
                }
                shareError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (val value = result) {
                is ExportResult.Success -> TextButton(
                    onClick = {
                        if (shared) return@TextButton
                        shared = true
                        try {
                            currentOnExport(value.json)
                        } catch (cancelled: CancellationException) {
                            shared = false
                            throw cancelled
                        } catch (error: Exception) {
                            shared = false
                            shareError = error.message ?: "Export callback failed"
                        }
                    },
                    enabled = !shared,
                ) {
                    Text(if (shared) "Shared" else "Share")
                }
                else -> TextButton(
                    onClick = { if (!isExporting) currentOnDismiss() },
                    enabled = !isExporting,
                ) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (result is ExportResult.Success) {
                TextButton(
                    onClick = { if (!isExporting) currentOnDismiss() },
                    enabled = !isExporting,
                ) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun <T> ImportSettingsDialog(
    backupManager: SettingsBackupManager<T>,
    jsonContent: String,
    onImportComplete: (ImportResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnImportComplete by rememberUpdatedState(onImportComplete)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    var validation by remember(backupManager, jsonContent) { mutableStateOf<ValidationResult?>(null) }
    var importResult by remember(backupManager, jsonContent) { mutableStateOf<ImportResult?>(null) }
    var isImporting by remember(backupManager, jsonContent) { mutableStateOf(false) }
    var completionDelivered by remember(backupManager, jsonContent) { mutableStateOf(false) }
    var operationError by remember(backupManager, jsonContent) { mutableStateOf<String?>(null) }
    var importJob by remember(backupManager, jsonContent) { mutableStateOf<Job?>(null) }

    DisposableEffect(backupManager, jsonContent) {
        onDispose { importJob?.cancel() }
    }

    LaunchedEffect(backupManager, jsonContent) {
        validation = null
        importResult = null
        isImporting = false
        completionDelivered = false
        operationError = null
        validation = try {
            backupManager.validate(jsonContent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            operationError = error.message ?: "Validation failed"
            null
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isImporting && (importResult == null || completionDelivered)) {
                currentOnDismiss()
            }
        },
        title = { Text("Import Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    isImporting -> CircularProgressIndicator()
                    importResult != null -> when (val value = importResult) {
                        is ImportResult.Success -> {
                            Text("Import successful!")
                            Text("Applied: ${value.appliedCount} settings")
                            if (value.skippedCount > 0) {
                                Text("Skipped: ${value.skippedCount} settings")
                            }
                            if (value.errors.isNotEmpty()) {
                                Text(
                                    "${value.errors.size} settings failed",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                value.errors.forEach { (name, message) ->
                                    Text(
                                        "• $name: $message",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        is ImportResult.Error -> Text("Import failed: ${value.message}")
                        null -> Unit
                    }
                    validation != null -> {
                        val value = validation
                        if (value != null && value.isValid) {
                            Text("Ready to import ${value.settingsCount} settings")
                            if (value.issues.isNotEmpty()) {
                                value.issues.forEach { issue ->
                                    Text("• $issue", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text("Validation issues:")
                            value?.issues?.forEach { issue ->
                                Text("• $issue", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    else -> operationError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                    } ?: CircularProgressIndicator()
                }
                operationError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (importResult == null && validation?.isValid == true) {
                TextButton(
                    onClick = {
                        if (isImporting) return@TextButton
                        isImporting = true
                        importResult = null
                        completionDelivered = false
                        operationError = null
                        val manager = backupManager
                        val content = jsonContent
                        importJob = scope.launch {
                            try {
                                val value = manager.import(content)
                                currentCoroutineContext().ensureActive()
                                importResult = value
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                operationError = error.message ?: "Import failed"
                            } finally {
                                isImporting = false
                            }
                        }
                    },
                    enabled = !isImporting
                ) {
                    Text("Import")
                }
            } else {
                val canClose = !isImporting && (importResult == null || completionDelivered)
                TextButton(
                    onClick = { if (canClose) currentOnDismiss() },
                    enabled = canClose
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (importResult == null) {
                TextButton(
                    onClick = { if (!isImporting) currentOnDismiss() },
                    enabled = !isImporting
                ) {
                    Text("Cancel")
                }
            }
        }
    )

    LaunchedEffect(importResult) {
        val completed = importResult ?: return@LaunchedEffect
        try {
            currentOnImportComplete(completed)
            completionDelivered = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            operationError = error.message ?: "Import callback failed"
            completionDelivered = true
        }
    }
}
