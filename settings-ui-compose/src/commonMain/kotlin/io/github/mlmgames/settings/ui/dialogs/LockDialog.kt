package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.managers.SettingsLockManager
import io.github.mlmgames.settings.core.managers.UnlockResult
import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.ui.LocalStringResourceProvider
import io.github.mlmgames.settings.ui.components.resolveSettingsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsLockDialog(
    lockManager: SettingsLockManager,
    isSettingPin: Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    key(lockManager, isSettingPin) {
        SettingsLockDialogContent(
            lockManager = lockManager,
            isSettingPin = isSettingPin,
            onSuccess = { currentOnSuccess() },
            onDismiss = { currentOnDismiss() },
        )
    }
}

@Composable
private fun SettingsLockDialogContent(
    lockManager: SettingsLockManager,
    isSettingPin: Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val provider = LocalStringResourceProvider.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                if (isSettingPin) {
                    provider.resolveSettingsText(SettingsTextKeys.SET_PIN, "Set PIN")
                } else {
                    provider.resolveSettingsText(SettingsTextKeys.ENTER_PIN, "Enter PIN")
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (!isProcessing) {
                            val digits = it.filter(Char::isDigit).take(6)
                            if (digits != pin) {
                                pin = digits
                                error = null
                            }
                        }
                    },
                    label = {
                        Text(
                            provider.resolveSettingsText(
                                SettingsTextKeys.PIN,
                                "PIN",
                            )
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    enabled = !isProcessing,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSettingPin) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            if (!isProcessing) {
                                val digits = it.filter(Char::isDigit).take(6)
                                if (digits != confirmPin) {
                                    confirmPin = digits
                                    error = null
                                }
                            }
                        },
                        label = {
                            Text(
                                provider.resolveSettingsText(
                                    SettingsTextKeys.CONFIRM_PIN,
                                    "Confirm PIN",
                                )
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = error != null,
                        enabled = !isProcessing,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isProcessing) return@TextButton
                    val validationError = when {
                        pin.length !in 4..6 -> provider.resolveSettingsText(
                            SettingsTextKeys.PIN_LENGTH,
                            "PIN must be 4 to 6 digits",
                        )
                        isSettingPin && pin != confirmPin -> provider.resolveSettingsText(
                            SettingsTextKeys.PIN_MATCH,
                            "PINs don't match",
                        )
                        else -> null
                    }
                    if (validationError != null) {
                        error = validationError
                        return@TextButton
                    }

                    isProcessing = true
                    error = null
                    scope.launch {
                        var succeeded = false
                        try {
                            if (isSettingPin) {
                                if (lockManager.enableLock(pin)) {
                                    succeeded = true
                                } else {
                                    error = provider.resolveSettingsText(
                                        SettingsTextKeys.SET_PIN_FAILED,
                                        "Failed to set PIN",
                                    )
                                }
                            } else {
                                when (lockManager.unlock(pin)) {
                                    UnlockResult.Success -> succeeded = true
                                    UnlockResult.InvalidPin -> error = provider.resolveSettingsText(
                                        SettingsTextKeys.INVALID_PIN,
                                        "Invalid PIN",
                                    )
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: provider.resolveSettingsText(
                                SettingsTextKeys.PIN_OPERATION_FAILED,
                                "PIN operation failed",
                            )
                        } finally {
                            isProcessing = false
                        }

                        if (succeeded) {
                            try {
                                onSuccess()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: provider.resolveSettingsText(
                                    SettingsTextKeys.PIN_OPERATION_FAILED,
                                    "PIN operation failed",
                                )
                            }
                        }
                    }
                },
                enabled = !isProcessing && pin.isNotEmpty() && (!isSettingPin || confirmPin.isNotEmpty())
            ) {
                Text(
                    provider.resolveSettingsText(
                        SettingsTextKeys.CONFIRM,
                        "Confirm",
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isProcessing) onDismiss() },
                enabled = !isProcessing
            ) {
                Text(
                    provider.resolveSettingsText(
                        SettingsTextKeys.CANCEL,
                        "Cancel",
                    )
                )
            }
        }
    )
}
