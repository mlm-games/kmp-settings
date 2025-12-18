package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DropdownSettingDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onOptionSelected: (Int) -> Unit,
) {
    var selected by remember { mutableStateOf(selectedIndex) }

    SettingsDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(onClick = { onOptionSelected(selected) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        options.forEachIndexed { index, option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (selected == index),
                        onClick = { selected = index }
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                RadioButton(
                    selected = (selected == index),
                    onClick = { selected = index }
                )
                Spacer(Modifier.width(12.dp))
                Text(text = option, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}