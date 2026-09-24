package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.ui.LocalStringResourceProvider
import io.github.mlmgames.settings.ui.components.resolveSettingsText

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
    val provider = LocalStringResourceProvider.current
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
                Text(
                    provider.resolveSettingsText(
                        SettingsTextKeys.APPLY,
                        "Apply",
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    provider.resolveSettingsText(
                        SettingsTextKeys.CANCEL,
                        "Cancel",
                    )
                )
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
            Text(
                provider.resolveSettingsText(
                    SettingsTextKeys.CLEAR,
                    "Clear",
                )
            )
        }
    }
}
