package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.ConfirmationConfig
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import io.github.mlmgames.settings.core.annotations.SettingPlatform
import io.github.mlmgames.settings.core.managers.ResetManager
import io.github.mlmgames.settings.core.platform.currentPlatform
import kotlinx.coroutines.CancellationException
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
    platform: SettingPlatform = currentPlatform,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    var selectedOption by remember { mutableStateOf(ResetOption.UI_ONLY) }
    var selectedCategory by remember { mutableStateOf<KClass<*>?>(null) }
    var pendingReset by remember { mutableStateOf<ResetRequest<T>?>(null) }
    var isResetting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var resultIsError by remember { mutableStateOf(false) }
    LaunchedEffect(resetManager, schema, platform) {
        pendingReset = null
        selectedCategory = null
    }

    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnReset by rememberUpdatedState(onReset)

    val visibleFields = remember(schema, platform) {
        schema.visibleUiFields(platform).filter { field ->
            field.isPersisted && field.meta?.noReset != true && field.isResettable
        }
    }
    val allResettableFields = remember(schema) { schema.resettableFields() }
    val categories = remember(schema, platform, visibleFields) {
        schema.orderedCategories(platform).filter { category ->
            visibleFields.any { it.meta?.category == category }
        }
    }

    LaunchedEffect(categories, selectedCategory) {
        if (selectedCategory !in categories) selectedCategory = null
    }

    val fieldsForSelectedOption: List<SettingField<T, *>> = when (selectedOption) {
        ResetOption.UI_ONLY -> visibleFields
        ResetOption.CATEGORY -> selectedCategory?.let { category ->
            visibleFields.filter { it.meta?.category == category }
        }.orEmpty()
        ResetOption.ALL -> allResettableFields
    }

    val beginReset: (ResetRequest<T>) -> Unit = beginReset@{ request ->
        if (isResetting || request.fields.isEmpty()) return@beginReset
        isResetting = true
        resultMessage = null
        resultIsError = false
        val manager = resetManager
        scope.launch {
            var count = 0
            var failure: Exception? = null
            try {
                count = when (request.option) {
                    ResetOption.UI_ONLY, ResetOption.CATEGORY ->
                        manager.resetFields(request.fields.map { it.name })
                    ResetOption.ALL -> manager.resetAll()
                }
            } catch (cancelled: CancellationException) {
                isResetting = false
                throw cancelled
            } catch (error: Exception) {
                failure = error
            }

            isResetting = false
            if (failure != null) {
                resultIsError = true
                resultMessage = failure.message ?: "Reset failed"
                return@launch
            }

            val partial = count != request.fields.size
            resultIsError = partial
            resultMessage = if (partial) {
                if (count < request.fields.size) {
                    "Reset partially completed: $count of ${request.fields.size} settings"
                } else {
                    "Reset completed with an unexpected result: $count settings"
                }
            } else {
                "Reset complete"
            }

            try {
                currentOnReset()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                resultIsError = true
                resultMessage = error.message ?: "Reset callback failed"
                return@launch
            }

            if (!partial) currentOnDismiss()
        }
    }

    val requestReset: () -> Unit = requestReset@{
        if (isResetting || pendingReset != null) return@requestReset
        val request = ResetRequest(selectedOption, fieldsForSelectedOption)
        if (request.fields.isEmpty()) {
            resultIsError = true
            resultMessage = "There are no resettable settings in this selection"
            return@requestReset
        }
        val messages = request.fields.mapNotNull { field ->
            field.resetConfirmation?.takeIf { it.isNotBlank() }?.let { message ->
                val name = field.meta?.title?.ifBlank { field.name } ?: field.name
                "$name: $message"
            }
        }
        if (messages.isEmpty()) {
            beginReset(request)
        } else {
            pendingReset = request
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isResetting && pendingReset == null) currentOnDismiss()
        },
        title = { Text("Reset Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Choose what to reset:")
                Spacer(Modifier.height(12.dp))

                ResetOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedOption == option,
                                onClick = {
                                    if (!isResetting && pendingReset == null) {
                                        selectedOption = option
                                        if (option != ResetOption.CATEGORY) selectedCategory = null
                                        resultMessage = null
                                    }
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = {
                                if (!isResetting && pendingReset == null) {
                                    selectedOption = option
                                    if (option != ResetOption.CATEGORY) selectedCategory = null
                                    resultMessage = null
                                }
                            },
                            enabled = !isResetting && pendingReset == null
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
                    Column(
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        categories.forEach { category ->
                            val categoryTitle = categoryTitles[category]
                                ?: category.simpleName
                                ?: "Unknown"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedCategory == category,
                                        enabled = !isResetting && pendingReset == null,
                                        onClick = { selectedCategory = category }
                                    )
                                    .padding(vertical = 6.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    enabled = !isResetting && pendingReset == null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(categoryTitle)
                            }
                        }
                    }
                }

                resultMessage?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = if (resultIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = requestReset,
                enabled = !isResetting &&
                    (selectedOption != ResetOption.CATEGORY || selectedCategory != null),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isResetting && pendingReset == null) currentOnDismiss() },
                enabled = !isResetting && pendingReset == null
            ) {
                Text("Cancel")
            }
        }
    )

    pendingReset?.let { request ->
        val message = request.fields.mapNotNull { field ->
            field.resetConfirmation?.takeIf { it.isNotBlank() }?.let { confirmation ->
                val name = field.meta?.title?.ifBlank { field.name } ?: field.name
                "$name: $confirmation"
            }
        }.joinToString("\n")
        SettingConfirmationDialog(
            config = ConfirmationConfig(
                title = "Confirm reset",
                message = message,
                titleRes = 0,
                messageRes = 0,
                confirmText = "Reset",
                confirmTextRes = 0,
                cancelText = "Cancel",
                cancelTextRes = 0,
                isDangerous = true,
            ),
            onConfirm = {
                if (pendingReset == request && !isResetting) {
                    pendingReset = null
                    beginReset(request)
                }
            },
            onDismiss = {
                if (!isResetting) pendingReset = null
            },
        )
    }
}

private data class ResetRequest<T>(
    val option: ResetOption,
    val fields: List<SettingField<T, *>>,
)
