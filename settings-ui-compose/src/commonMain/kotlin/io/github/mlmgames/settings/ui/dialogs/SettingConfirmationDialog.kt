package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.github.mlmgames.settings.core.ConfirmationConfig
import io.github.mlmgames.settings.ui.LocalStringResourceProvider

@Composable
fun SettingConfirmationDialog(
    config: ConfirmationConfig,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val provider = LocalStringResourceProvider.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(config.resolvedTitle(provider))
        },
        text = {
            Text(config.resolvedMessage(provider))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (config.isDangerous) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                Text(config.resolvedConfirmText(provider))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(config.resolvedCancelText(provider))
            }
        }
    )
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDangerous: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDangerous) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}