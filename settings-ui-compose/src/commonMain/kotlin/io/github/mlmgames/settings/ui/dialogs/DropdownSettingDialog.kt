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
    DropdownSettingDialog(
        title = title,
        options = options,
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onOptionSelected = onOptionSelected,
        allowNull = false,
    )
}

@Composable
fun DropdownSettingDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onOptionSelected: (Int) -> Unit,
    allowNull: Boolean = false,
    nullLabel: String = "(not set)",
) {
    val displayedOptions = if (allowNull) listOf(nullLabel) + options else options
    val initialIndex = when {
        allowNull && selectedIndex < 0 -> 0
        allowNull -> selectedIndex + 1
        else -> selectedIndex
    }.takeIf { it in displayedOptions.indices } ?: -1
    var selected by remember(title, displayedOptions, initialIndex) {
        mutableStateOf(initialIndex)
    }

    SettingsDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(
                onClick = {
                    val index = when {
                        allowNull && selected == 0 -> -1
                        allowNull -> selected - 1
                        else -> selected
                    }
                    if (allowNull || index in options.indices) {
                        onOptionSelected(index)
                    }
                },
                enabled = allowNull || selected in options.indices,
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        displayedOptions.forEachIndexed { index, option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == index,
                        onClick = { selected = index }
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                RadioButton(
                    selected = selected == index,
                    onClick = { selected = index }
                )
                Spacer(Modifier.width(12.dp))
                Text(text = option, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
