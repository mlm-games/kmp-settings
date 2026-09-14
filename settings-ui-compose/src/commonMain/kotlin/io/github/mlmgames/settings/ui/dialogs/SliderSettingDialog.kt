package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.ui.formatSliderValue
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
    // Guard misconfigured meta at the UI boundary: KSP validates, but
    // hand-built schemas can still pass min>=max or step<=0, which would
    // crash Slider (valueRange) or explode steps (Inf.toInt()).
    val safeMin = min
    val safeMax = if (max > min) max else min + 1f
    val safeStep = if (step.isFinite() && step > 0f) step else (safeMax - safeMin)

    var sliderValue by remember(currentValue, safeMin, safeMax, safeStep) {
        mutableFloatStateOf(currentValue.coerceIn(safeMin, safeMax))
    }

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
        val formatted = remember(sliderValue, safeStep) {
            formatSliderValue(sliderValue, safeStep)
        }

        Text(
            text = formatted,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))

        // steps = intervals - 1; snapping rounds to nearest (not truncation,
        // which biased negative ranges toward zero).
        val intervals = ((safeMax - safeMin) / safeStep).roundToInt().coerceAtLeast(1)
        val stepsCount = (intervals - 1).coerceAtLeast(0)

        Slider(
            value = sliderValue,
            onValueChange = { v ->
                val snapped = run {
                    val n = ((v - safeMin) / safeStep).roundToInt()
                    safeMin + (n * safeStep)
                }
                sliderValue = snapped.coerceIn(safeMin, safeMax)
            },
            valueRange = safeMin..safeMax,
            steps = stepsCount
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = safeMin.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = safeMax.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun Float.roundToOneDecimal(): String {
    return ((this * 10.0).roundToInt() / 10.0).toString()
}
