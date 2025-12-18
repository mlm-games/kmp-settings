package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SliderSettingDialog(
    title: String,
    currentValue: Float,
    min: Float,
    max: Float,
    step: Float,
    onDismiss: () -> Unit,
    onValueSelected: (Float) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(currentValue) }

    SettingsDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(onClick = { onValueSelected(sliderValue) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        val formatted = remember(sliderValue) {
            val v = (sliderValue * 10f).roundToInt() / 10f
            v.roundToOneDecimal()
        }

        Text(
            text = formatted,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))

        val stepsCount = (((max - min) / step).toInt() - 1).coerceAtLeast(0)

        Slider(
            value = sliderValue,
            onValueChange = { v ->
                val snapped = if (step <= 0f) v else {
                    val n = ((v - min) / step).toInt()
                    min + (n * step)
                }
                sliderValue = snapped.coerceIn(min, max)
            },
            valueRange = min..max,
            steps = stepsCount
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = min.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = max.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun Float.roundToOneDecimal(): String {
    return ((this * 10.0).roundToInt() / 10.0).toString()
}