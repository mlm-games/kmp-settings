package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSettingDialog(
    title: String,
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onTimeSelected: (Int) -> Unit,
) {
    val clamped = currentMinutes.coerceIn(0, 1439)
    val state = rememberTimePickerState(
        initialHour = clamped / 60,
        initialMinute = clamped % 60,
        is24Hour = true,
    )

    TimePickerDialog(
        title = { Text(title) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(state.hour * 60 + state.minute)
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        TimePicker(state = state)
    }
}

fun formatMinutesOfDay(totalMinutes: Int, use24Hour: Boolean = true): String {
    val clamped = totalMinutes.coerceIn(0, 1439)
    val h = clamped / 60
    val m = clamped % 60
    return if (use24Hour) {
        "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    } else {
        val suffix = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        "$h12:${m.toString().padStart(2, '0')} $suffix"
    }
}