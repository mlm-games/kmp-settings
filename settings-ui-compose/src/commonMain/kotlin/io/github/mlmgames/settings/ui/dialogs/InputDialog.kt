package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun InputDialog(
    title: String,
    label: String,
    value: String,
    placeholder: String = "",
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    validator: (String) -> Boolean = { true },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(value) }
    val valid = validator(input)

    SettingsDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input) },
                enabled = valid,
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = input.isNotEmpty() && !valid,
            singleLine = true,
        )
    }
}