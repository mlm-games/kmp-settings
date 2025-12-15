package io.github.iremote.settings.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

@Composable
fun ApeDialog(
  onDismissRequest: () -> Unit,
  title: String? = null,
  confirmButton: @Composable () -> Unit,
  dismissButton: @Composable (() -> Unit)? = null,
  properties: DialogProperties = DialogProperties(),
  content: @Composable () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = confirmButton,
    dismissButton = dismissButton,
    title = title?.let {
      { Text(text = it, style = MaterialTheme.typography.headlineSmall) }
    },
    text = {
      Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          content()
        }
      }
    },
    properties = properties,
    containerColor = MaterialTheme.colorScheme.surface,
  )
}