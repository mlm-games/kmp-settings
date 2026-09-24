package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NullableInputDialog(
    title: String,
    label: String,
    value: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
    onClear: () -> Unit,
    validator: (String?) -> Boolean = { true },
) {
    var input by remember(title, value) { mutableStateOf(value) }
    val valid = validator(input)

    SettingsDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input) },
                enabled = valid,
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        OutlinedTextField(
            value = input.orEmpty(),
            onValueChange = { input = it },
            label = { Text(label) },
            isError = input != null && !valid,
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClear) {
            Text("Clear")
        }
    }
}
