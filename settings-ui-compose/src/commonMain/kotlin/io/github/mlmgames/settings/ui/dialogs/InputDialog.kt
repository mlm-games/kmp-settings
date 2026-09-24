package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.ui.LocalStringResourceProvider
import io.github.mlmgames.settings.ui.components.resolveSettingsText

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
    val provider = LocalStringResourceProvider.current
    val resolvedConfirmText = when (confirmText) {
        "Confirm" -> provider.resolveSettingsText(SettingsTextKeys.CONFIRM, confirmText)
        "OK" -> provider.resolveSettingsText(SettingsTextKeys.OK, confirmText)
        else -> confirmText
    }
    val resolvedDismissText = if (dismissText == "Cancel") {
        provider.resolveSettingsText(SettingsTextKeys.CANCEL, dismissText)
    } else {
        dismissText
    }
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
                Text(resolvedConfirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(resolvedDismissText)
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