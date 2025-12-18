package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.SettingsSchema
import io.github.mlmgames.settings.core.managers.ResetManager
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

enum class ResetOption(val title: String, val description: String) {
    UI_ONLY("UI Settings", "Reset visible settings only"),
    CATEGORY("Category", "Reset a specific category"),
    ALL("All Settings", "Reset everything including internal state")
}

@Composable
fun <T> ResetSettingsDialog(
    resetManager: ResetManager<T>,
    schema: SettingsSchema<T>,
    categoryTitles: Map<KClass<*>, String> = emptyMap(),
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    var selectedOption by remember { mutableStateOf(ResetOption.UI_ONLY) }
    var selectedCategory by remember { mutableStateOf<KClass<*>?>(null) }
    var isResetting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val categories = remember { schema.orderedCategories() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Settings") },
        text = {
            Column {
                Text("Choose what to reset:")
                Spacer(Modifier.height(12.dp))

                ResetOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedOption == option,
                                onClick = { selectedOption = option }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(option.title)
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (selectedOption == ResetOption.CATEGORY && categories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Select category:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))

                    categories.forEach { cat ->
                        val catTitle = categoryTitles[cat] ?: cat.simpleName ?: "Unknown"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat }
                                )
                                .padding(vertical = 6.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(catTitle)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isResetting = true
                    scope.launch {
                        when (selectedOption) {
                            ResetOption.UI_ONLY -> resetManager.resetUISettings()
                            ResetOption.CATEGORY -> selectedCategory?.let {
                                resetManager.resetCategory(it)
                            }
                            ResetOption.ALL -> resetManager.resetAll()
                        }
                        isResetting = false
                        onReset()
                        onDismiss()
                    }
                },
                enabled = !isResetting && (selectedOption != ResetOption.CATEGORY || selectedCategory != null),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}