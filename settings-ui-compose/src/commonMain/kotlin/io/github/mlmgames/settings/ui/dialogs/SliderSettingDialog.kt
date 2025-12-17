package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

  ApeDialog(
    onDismissRequest = onDismiss,
    title = title,
    confirmButton = {
      TextButton(onClick = { onValueSelected(sliderValue) }) { Text("Apply") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  ) {
      val formatted = remember(sliderValue) { val v = kotlin.math.round(sliderValue * 10f) / 10f
          val whole = v.toInt()
          val frac = kotlin.math.abs(((v - whole) * 10).toInt())
          "$whole.$frac" }
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
  }
}