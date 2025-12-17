package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> SelectionDialog(
  title: String,
  items: List<T>,
  selectedItem: T? = null,
  itemLabel: (T) -> String,
  onItemSelected: (T) -> Unit,
  onDismiss: () -> Unit,
) {
  var selected by remember { mutableStateOf(selectedItem) }

  ApeDialog(
    onDismissRequest = onDismiss,
    title = title,
    confirmButton = {
      TextButton(onClick = { selected?.let(onItemSelected) }, enabled = selected != null) {
        Text("Select")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  ) {
    items.forEach { item ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .selectable(
            selected = (selected == item),
            onClick = { selected = item }
          )
          .padding(vertical = 12.dp, horizontal = 16.dp)
      ) {
        RadioButton(
          selected = (selected == item),
          onClick = { selected = item }
        )
        Spacer(Modifier.width(12.dp))
        Text(text = itemLabel(item), style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}