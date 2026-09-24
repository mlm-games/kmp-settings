package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.ui.formatSliderValue
import kotlin.math.ceil
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
    SliderSettingDialog(
        title = title,
        currentValue = currentValue,
        min = min,
        max = max,
        step = step,
        onDismiss = onDismiss,
        onValueSelected = onValueSelected,
        allowNull = false,
        onClear = null,
    )
}

@Composable
fun SliderSettingDialog(
    title: String,
    currentValue: Float?,
    min: Float,
    max: Float,
    step: Float,
    onDismiss: () -> Unit,
    onValueSelected: (Float) -> Unit,
    allowNull: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    var safeMin = if (min.isFinite()) min else 0f
    var safeMax = if (max.isFinite()) max else Float.MAX_VALUE / 2f
    if (safeMin > Float.MAX_VALUE / 2f) safeMin = Float.MAX_VALUE / 2f
    if (safeMax >= Float.MAX_VALUE) safeMax = Float.MAX_VALUE / 2f
    if (!(safeMax > safeMin) || !(safeMax - safeMin).isFinite()) {
        safeMin = 0f
        safeMax = 1f
    }
    val safeRange = safeMax - safeMin
    val requestedStep = if (step.isFinite() && step > 0f && step <= safeRange) step else safeRange
    val intervalRatio = safeRange / requestedStep
    val intervals = if (intervalRatio.isFinite()) {
        ceil(intervalRatio).toInt().coerceIn(1, 10000)
    } else {
        1
    }
    val safeStep = (safeRange / intervals).takeIf { it.isFinite() && it > 0f } ?: safeRange

    var sliderValue by remember(currentValue, safeMin, safeMax, safeStep) {
        mutableFloatStateOf((currentValue ?: safeMin).coerceIn(safeMin, safeMax))
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
        val formatted = remember(sliderValue, safeStep, currentValue) {
            if (currentValue == null && sliderValue == safeMin) "(not set)" else formatSliderValue(sliderValue, safeStep)
        }
        Text(
            text = formatted,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))

        val stepsCount = (intervals - 1).coerceAtLeast(0)

        Slider(
            value = sliderValue,
            onValueChange = { value ->
                val snapped = safeMin +
                    ((value - safeMin) / safeStep).roundToInt() * safeStep
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

        if (allowNull && onClear != null) {
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }
    }
}

fun Float.roundToOneDecimal(): String {
    return ((this * 10.0).roundToInt() / 10.0).toString()
}
