package io.github.mlmgames.settings.ui.dialogs

import androidx.compose.runtime.Composable

@Composable
fun DropdownSettingDialog(
  title: String,
  options: List<String>,
  selectedIndex: Int,
  onDismiss: () -> Unit,
  onOptionSelected: (Int) -> Unit,
) {
  SelectionDialog(
    title = title,
    items = options.indices.toList(),
    selectedItem = selectedIndex,
    itemLabel = { idx -> options[idx] },
    onItemSelected = onOptionSelected,
    onDismiss = onDismiss
  )
}