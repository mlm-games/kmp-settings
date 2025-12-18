package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.backup.*
import kotlinx.coroutines.launch

@Composable
fun <T> ExportSettingsDialog(
    backupManager: SettingsBackupManager<T>,
    onExport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var isExporting by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        result = backupManager.export()
        isExporting = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Settings") },
        text = {
            when (val r = result) {
                null -> {
                    if (isExporting) {
                        CircularProgressIndicator()
                    }
                }
                is ExportResult.Success -> {
                    Column {
                        Text("Settings exported successfully!")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Size: ${r.json.length} bytes",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is ExportResult.Error -> {
                    Text("Export failed: ${r.message}")
                }
            }
        },
        confirmButton = {
            when (val r = result) {
                is ExportResult.Success -> {
                    TextButton(onClick = { onExport(r.json) }) {
                        Text("Share")
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
    var validation by remember { mutableStateOf<ValidationResult?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(jsonContent) {
        validation = backupManager.validate(jsonContent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Settings") },
        text = {
            Column {
                when {
                    importResult != null -> {
                        when (val r = importResult) {
                            is ImportResult.Success -> {
                                Text("Import successful!")
                                Text("Applied: ${r.appliedCount} settings")
                                if (r.skippedCount > 0) {
                                    Text("Skipped: ${r.skippedCount} settings")
                                }
                            }
                            is ImportResult.Error -> {
                                Text("Import failed: ${r.message}")
                            }
                            else -> {}
                        }
                    }
                    isImporting -> {
                        CircularProgressIndicator()
                    }
                    validation != null -> {
                        val v = validation!!
                        if (v.isValid) {
                            Text("Ready to import ${v.settingsCount} settings")
                        } else {
                            Text("Validation issues:")
                            v.issues.forEach { issue ->
                                Text("• $issue", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (importResult == null && validation?.isValid == true) {
                TextButton(
                    onClick = {
                        isImporting = true
                        scope.launch {
                            importResult = backupManager.import(jsonContent)
                            isImporting = false
                            importResult?.let { onImportComplete(it) }
                        }
                    },
                    enabled = !isImporting
                ) {
                    Text("Import")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (importResult == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}