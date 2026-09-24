package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.ui.LocalStringResourceProvider
import io.github.mlmgames.settings.ui.components.resolveSettingsText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSettingDialog(
    title: String,
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onTimeSelected: (Int) -> Unit,
) {
    TimePickerSettingDialog(
        title = title,
        currentMinutes = currentMinutes,
        onDismiss = onDismiss,
        onTimeSelected = onTimeSelected,
        onClear = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSettingDialog(
    title: String,
    currentMinutes: Int?,
    onDismiss: () -> Unit,
    onTimeSelected: (Int) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val provider = LocalStringResourceProvider.current
    val initialMinutes = (currentMinutes ?: 0).coerceIn(0, 1439)
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    LaunchedEffect(title, currentMinutes) {
        state.hour = initialMinutes / 60
        state.minute = initialMinutes % 60
    }

    TimePickerDialog(
        title = { Text(title) },
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = onClear) {
                        Text(
                            provider.resolveSettingsText(
                                SettingsTextKeys.CLEAR,
                                "Clear",
                            )
                        )
                    }
                }
                TextButton(onClick = {
                    onTimeSelected(state.hour * 60 + state.minute)
                }) {
                    Text(
                        provider.resolveSettingsText(
                            SettingsTextKeys.APPLY,
                            "Apply",
                        )
                    )
                }
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
        },
    ) {
        TimePicker(state = state)
    }
}

fun formatMinutesOfDay(
    totalMinutes: Int,
    use24Hour: Boolean = true,
    amPm: (Boolean) -> String = { isPm -> if (isPm) "PM" else "AM" },
): String {
    val clamped = totalMinutes.coerceIn(0, 1439)
    val h = clamped / 60
    val m = clamped % 60
    return if (use24Hour) {
        "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    } else {
        val suffix = amPm(h >= 12)
        val h12 = if (h % 12 == 0) 12 else h % 12
        "$h12:${m.toString().padStart(2, '0')} $suffix"
    }
}
