package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*

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
  var input by remember { mutableStateOf(value) }
  val valid = validator(input)

  ApeDialog(
    onDismissRequest = onDismiss,
    title = title,
    confirmButton = {
      TextButton(
        onClick = { onConfirm(input) },
        enabled = valid,
        colors = ButtonDefaults.textButtonColors()
      ) { Text(confirmText) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(dismissText) }
    }
  ) {
    OutlinedTextField(
      value = input,
      onValueChange = { input = it },
      label = { Text(label) },
      placeholder = { Text(placeholder) },
      singleLine = true,
    )
  }
}