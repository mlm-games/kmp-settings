package io.github.mlmgames.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.*
import io.github.mlmgames.settings.ui.components.SettingsItem
import io.github.mlmgames.settings.ui.components.SettingsSection
import io.github.mlmgames.settings.ui.components.SettingsToggle
import io.github.mlmgames.settings.ui.dialogs.DropdownSettingDialog
import io.github.mlmgames.settings.ui.dialogs.SliderSettingDialog

/**
 * Generic "auto UI" for settings based on schema metadata.
 *
 * Assumptions (by design):
 * - TOGGLE uses Boolean properties
 * - DROPDOWN uses Int index properties
 * - SLIDER uses Float properties (or Int; we convert)
 */
@Composable
fun <T> AutoSettingsScreen(
  schema: SettingsSchema<T>,
  value: T,
  onSet: (name: String, value: Any) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showDropdown by remember { mutableStateOf(false) }
  var showSlider by remember { mutableStateOf(false) }
  var currentField by remember { mutableStateOf<SettingField<T, *>?>(null) }

  val grouped = remember(schema) { schema.groupedByCategory() }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    SettingCategory.entries.forEach { cat ->
      val fields = grouped[cat].orEmpty()
      if (fields.isEmpty()) return@forEach

      item {
        Text(
          text = cat.name.lowercase().replaceFirstChar { it.uppercase() },
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }

      item {
        SettingsSection(title = "") {
          // inner list
          Column {
            fields.forEach { f ->
              val meta = f.meta ?: return@forEach
              val enabled = schema.isEnabled(value, f)

              when (meta.type) {
                SettingType.TOGGLE -> {
                  val bf = f as? SettingField<T, Boolean>
                  if (bf != null) {
                    SettingsToggle(
                      title = meta.title,
                      description = meta.description.takeIf { it.isNotBlank() },
                      checked = bf.get(value),
                      enabled = enabled,
                      onCheckedChange = { onSet(bf.name, it) }
                    )
                  }
                }

                SettingType.DROPDOWN -> {
                  val inf = f as? SettingField<T, Int>
                  if (inf != null) {
                    val idx = inf.get(value)
                    SettingsItem(
                      title = meta.title,
                      subtitle = meta.options.getOrNull(idx) ?: "Unknown",
                      description = meta.description.takeIf { it.isNotBlank() },
                      enabled = enabled,
                      onClick = { currentField = f; showDropdown = true }
                    )
                  }
                }

                SettingType.SLIDER -> {
                  // support Float or Int sliders
                  val floatField = f as? SettingField<T, Float>
                  val intField = f as? SettingField<T, Int>

                  val subtitle = when {
                    floatField != null -> floatField.get(value).toString()
                    intField != null -> intField.get(value).toString()
                    else -> ""
                  }

                  SettingsItem(
                    title = meta.title,
                    subtitle = subtitle,
                    description = meta.description.takeIf { it.isNotBlank() },
                    enabled = enabled,
                    onClick = { currentField = f; showSlider = true }
                  )
                }

                SettingType.BUTTON -> {
                  // UI-only, will impl it later (follow other apps)
                  SettingsItem(
                    title = meta.title,
                    subtitle = null,
                    description = meta.description.takeIf { it.isNotBlank() },
                    enabled = enabled,
                    onClick = { /* no-op by default */ }
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  val cf = currentField
  if (showDropdown && cf?.meta != null) {
    val meta = cf.meta!!
    val inf = cf as? SettingField<T, Int>
    if (inf != null) {
      DropdownSettingDialog(
        title = meta.title,
        options = meta.options,
        selectedIndex = inf.get(value),
        onDismiss = { showDropdown = false },
        onOptionSelected = { idx ->
          onSet(inf.name, idx)
          showDropdown = false
        }
      )
    } else {
      showDropdown = false
    }
  }

  if (showSlider && cf?.meta != null) {
    val meta = cf.meta!!
    val ff = cf as? SettingField<T, Float>
    val inf = cf as? SettingField<T, Int>

    val cur = when {
      ff != null -> ff.get(value)
      inf != null -> inf.get(value).toFloat()
      else -> 0f
    }

    SliderSettingDialog(
      title = meta.title,
      currentValue = cur,
      min = meta.min,
      max = meta.max,
      step = meta.step,
      onDismiss = { showSlider = false },
      onValueSelected = { v ->
        when {
          ff != null -> onSet(ff.name, v)
          inf != null -> onSet(inf.name, v.toInt())
        }
        showSlider = false
      }
    )
  }
}