package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.ui.LocalStringResourceProvider
import io.github.mlmgames.settings.ui.components.resolveSettingsText

@Composable
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T? = null,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val provider = LocalStringResourceProvider.current
    var selected by remember(title, items, selectedItem) { mutableStateOf(selectedItem) }

    SettingsDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onItemSelected) },
                enabled = selected != null
            ) {
                Text(
                    provider.resolveSettingsText(
                        SettingsTextKeys.SELECT,
                        "Select",
                    )
                )
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