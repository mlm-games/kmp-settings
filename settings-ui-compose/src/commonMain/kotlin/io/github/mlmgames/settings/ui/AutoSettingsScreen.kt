package io.github.mlmgames.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mlmgames.settings.core.*
import io.github.mlmgames.settings.core.actions.ActionRegistry
import io.github.mlmgames.settings.core.annotations.SettingAction
import io.github.mlmgames.settings.core.annotations.SettingPlatform
import io.github.mlmgames.settings.core.annotations.ValidationResult
import io.github.mlmgames.settings.core.platform.currentPlatform
import io.github.mlmgames.settings.core.types.Button
import io.github.mlmgames.settings.core.types.Dropdown
import io.github.mlmgames.settings.core.types.Slider
import io.github.mlmgames.settings.core.types.TextInput
import io.github.mlmgames.settings.core.types.TimePickerType
import io.github.mlmgames.settings.core.types.Toggle
import io.github.mlmgames.settings.ui.components.*
import io.github.mlmgames.settings.ui.dialogs.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/**
 * Configuration for custom setting type rendering.
 */
data class CustomTypeHandler<T>(
    val typeClass: KClass<*>,
    val render: @Composable (
        field: SettingField<T, *>,
        meta: SettingMeta,
        value: T,
        enabled: Boolean,
        onSet: (name: String, value: Any) -> Unit,
    ) -> Unit
)

/**
 * Configuration for category display.
 */
data class CategoryConfig(
    val categoryClass: KClass<*>,
    val title: String,
    val titleRes: Int = 0,
)

/**
 * Auto-generated settings screen from schema.
 *
 * @param schema The settings schema
 * @param value Current settings value
 * @param onSet Callback when a setting is changed
 * @param onAction Callback for button actions
 * @param modifier Modifier for the screen
 * @param platform Override platform detection (defaults to current platform)
 * @param categoryConfigs Custom category display configuration
 * @param customTypeHandlers Custom type renderers
 * @param snackbarHostState External snackbar host state
 * @param actionIcons Map of action classes to their icons
 */
@Composable
fun <T> AutoSettingsScreen(
    schema: SettingsSchema<T>,
    value: T,
    onSet: (name: String, value: Any) -> Unit,
    onAction: suspend (KClass<out SettingAction>) -> Unit = {},
    modifier: Modifier = Modifier,
    platform: SettingPlatform = currentPlatform,
    categoryConfigs: List<CategoryConfig> = emptyList(),
    customTypeHandlers: List<CustomTypeHandler<T>> = emptyList(),
    snackbarHostState: SnackbarHostState? = null,
    actionTrailingContent: Map<KClass<out SettingAction>, @Composable () -> Unit> = emptyMap(),
) {
    val stringProvider = LocalStringResourceProvider.current
    val scope = rememberCoroutineScope()

    // Snackbar
    val internalSnackbarHostState = remember { SnackbarHostState() }
    val effectiveSnackbarHostState = snackbarHostState ?: internalSnackbarHostState
    val renderInternalSnackbarHost = snackbarHostState == null

    fun showSnackbar(message: String) {
        scope.launch {
            effectiveSnackbarHostState.currentSnackbarData?.dismiss()
            effectiveSnackbarHostState.showSnackbar(message)
        }
    }

    // Dialog states
    var showDropdown by remember { mutableStateOf(false) }
    var showSlider by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var currentField by remember { mutableStateOf<SettingField<T, *>?>(null) }

    // Single-dialog policy lambdas, declared before the list so row onClick
    // handlers can reference them. Opening one dialog closes any other, and
    // every dismiss path nulls the field so stale state never leaks.
    val openDialog: (SettingField<T, *>, String) -> Unit = { field, which ->
        currentField = field
        showDropdown = which == "dropdown"
        showSlider = which == "slider"
        showTextInput = which == "text"
        showTimePicker = which == "time"
    }
    val closeDialogs: () -> Unit = {
        showDropdown = false
        showSlider = false
        showTextInput = false
        showTimePicker = false
        currentField = null
    }

    // Confirmation dialog state
    var pendingConfirmation by remember { mutableStateOf<PendingConfirmation<T>?>(null) }

    val grouped = remember(schema, platform) { schema.groupedByCategory(platform) }
    val orderedCategories = remember(schema, platform) { schema.orderedCategories(platform) }

    val categoryConfigMap = remember(categoryConfigs) {
        categoryConfigs.associateBy { it.categoryClass }
    }
    val customHandlerMap = remember(customTypeHandlers) {
        customTypeHandlers.associateBy { it.typeClass }
    }

    // Handle setting change with validation and confirmation
    // Captures the latest value/schema via rememberUpdatedState-equivalent:
    // handleSetValue reads `value`/`schema` from composition locals at call
    // time, so dialogs always validate against current state.
    val handleSetValue: (SettingField<T, *>, Any) -> Unit = handleSetValue@{ field, newValue ->
        val meta = field.meta

        // Validate if rules exist
        if (meta?.validation != null) {
            when (val result = meta.validate(newValue, stringProvider)) {
                is ValidationResult.Valid -> { /* proceed */ }
                is ValidationResult.Invalid -> {
                    showSnackbar(result.message)
                    return@handleSetValue
                }
            }
        }

        // Re-check dependency at commit time: a dialog may have been opened
        // while enabled, then the dependency toggled off underneath.
        if (!schema.isEnabled(value, field)) {
            showSnackbar("Setting is disabled")
            return@handleSetValue
        }

        // Check for confirmation requirement
        if (meta?.confirmation != null) {
            // Queue: a second confirmation replaces the first only after the
            // first is resolved; pendingConfirmation holds at most one.
            pendingConfirmation = PendingConfirmation(
                field = field,
                value = newValue,
                isAction = false,
                config = meta.confirmation!!
            )
        } else {
            onSet(field.name, newValue)
        }
    }

    // Handle button actions
    val handleAction: (SettingField<T, *>) -> Unit = handleAction@{ field ->
        val meta = field.meta ?: run {
            showSnackbar("Action unavailable")
            return@handleAction
        }
        val actionClass = meta.actionClass ?: run {
            showSnackbar("Action unavailable")
            return@handleAction
        }

        val action = ActionRegistry.getAction(actionClass)

        if (action?.requiresConfirmation == true) {
            pendingConfirmation = PendingConfirmation(
                field = field,
                value = Unit,
                isAction = true,
                config = ConfirmationConfig(
                    title = action.confirmationTitle,
                    message = action.confirmationMessage,
                    titleRes = 0,
                    messageRes = 0,
                    confirmText = "Confirm",
                    confirmTextRes = 0,
                    cancelText = "Cancel",
                    cancelTextRes = 0,
                    isDangerous = action.isDangerous
                )
            )
        } else {
            scope.launch {
                runCatching { onAction(actionClass) }
                    .onFailure { showSnackbar(it.message ?: "Action failed") }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = if (renderInternalSnackbarHost) 88.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            orderedCategories.forEach { categoryClass ->
                val fields = grouped[categoryClass].orEmpty()
                if (fields.isEmpty()) return@forEach

                val categoryConfig = categoryConfigMap[categoryClass]
                val categoryTitle = when {
                    categoryConfig?.titleRes != 0 && categoryConfig != null ->
                        stringProvider.getString(categoryConfig.titleRes)
                    categoryConfig?.title?.isNotBlank() == true ->
                        categoryConfig.title
                    else ->
                        categoryClass.simpleName ?: "Unknown"
                }

                item(key = "header_${categoryClass.qualifiedName}") {
                    Text(
                        text = categoryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item(key = "section_${categoryClass.qualifiedName}") {
                    SettingsSection(title = "") {
                        Column {
                            fields.forEach { field ->
                                val meta = field.meta ?: return@forEach
                                val enabled = schema.isEnabled(value, field)

                                val title = meta.resolvedTitle(stringProvider)
                                val description = meta.resolvedDescription(stringProvider)
                                    .takeIf { it.isNotBlank() }

                                val customHandler = customHandlerMap[meta.type]
                                if (customHandler != null) {
                                    // Route through handleSetValue so custom types
                                    // keep validation + confirmation guarantees.
                                    @Suppress("UNCHECKED_CAST")
                                    customHandler.render(field, meta, value, enabled) { name, v ->
                                        val target = schema.fieldByName(name) ?: field
                                        handleSetValue(target, v)
                                    }
                                    return@forEach
                                }

                                when (meta.type) {
                                    Toggle::class -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val boolField = field as? SettingField<T, Boolean>
                                        if (boolField != null) {
                                            SettingsToggle(
                                                title = title,
                                                description = description,
                                                checked = boolField.get(value),
                                                enabled = enabled,
                                                onCheckedChange = { handleSetValue(field, it) }
                                            )
                                        }
                                    }

                                    Dropdown::class -> {
                                        val options = meta.dropdownLabels(field, stringProvider)
                                        // Nullable selection: null stays null (subtitle
                                        // shows "(not set)") instead of coercing to index 0.
                                        val idx = field.toUiDropdownIndex(value)
                                        SettingsItem(
                                            title = title,
                                            subtitle = if (idx == null) "(not set)" else options.getOrNull(idx) ?: "Unknown",
                                            description = description,
                                            enabled = enabled,
                                            onClick = { openDialog(field, "dropdown") }
                                        )
                                    }

                                    Slider::class -> {
                                        val sliderVal = field.toUiSliderValue(value)
                                        val subtitle = sliderVal?.let {
                                            formatSliderValue(it, meta.step)
                                        } ?: ""

                                        SettingsItem(
                                            title = title,
                                            subtitle = subtitle,
                                            description = description,
                                            enabled = enabled,
                                            onClick = { openDialog(field, "slider") }
                                        )
                                    }

                                    Button::class -> {
                                        SettingsAction(
                                            title = title,
                                            description = description,
                                            enabled = enabled,
                                            onClick = { handleAction(field) },
                                            trailingContent = meta.actionClass?.let { actionTrailingContent[it] },
                                        )
                                    }

                                    TextInput::class -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val stringField = field as? SettingField<T, String>
                                        if (stringField != null) {
                                            SettingsItem(
                                                title = title,
                                                subtitle = stringField.get(value).ifBlank { "(empty)" },
                                                description = description,
                                                enabled = enabled,
                                                onClick = { openDialog(field, "text") }
                                            )
                                        } else {
                                            SettingsItem(
                                                title = title,
                                                subtitle = "Unsupported type for text input",
                                                description = description,
                                                enabled = false,
                                                onClick = {}
                                            )
                                        }
                                    }

                                    TimePickerType::class -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val intField = field as? SettingField<T, Int>
                                        if (intField != null) {
                                            val minutes = intField.get(value)
                                            SettingsItem(
                                                title = title,
                                                subtitle = formatMinutesOfDay(minutes),
                                                description = description,
                                                enabled = enabled,
                                                onClick = { openDialog(field, "time") }
                                            )
                                        } else {
                                            SettingsItem(
                                                title = title,
                                                subtitle = "Unsupported type for time picker",
                                                description = description,
                                                enabled = false,
                                                onClick = {}
                                            )
                                        }
                                    }

                                    else -> {
                                        // Unknown/custom type without a handler: visible
                                        // fallback instead of a silent blank row.
                                        SettingsItem(
                                            title = title,
                                            subtitle = "Unsupported setting type",
                                            description = description,
                                            enabled = false,
                                            onClick = {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (renderInternalSnackbarHost) {
            SnackbarHost(
                hostState = effectiveSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    // Dialogs (single `currentField` policy; openDialog/closeDialogs above).
    val cf = currentField
    if (showDropdown && cf?.meta != null) {
        val meta = cf.meta!!
        val options = meta.dropdownLabels(cf, stringProvider)
        // -1 = explicit-null selection for nullable dropdowns.
        val currentIdx = cf.toUiDropdownIndex(value) ?: -1

        DropdownSettingDialog(
            title = meta.resolvedTitle(stringProvider),
            options = options,
            selectedIndex = currentIdx,
            onDismiss = { closeDialogs() },
            onOptionSelected = { idx ->
                val newValue = cf.fromUiDropdownIndex(idx)
                if (newValue != null) {
                    handleSetValue(cf, newValue)
                } else {
                    showSnackbar("Invalid selection")
                }
                closeDialogs()
            }
        )
    }

    if (showSlider && cf?.meta != null) {
        val meta = cf.meta!!
        val currentVal = cf.toUiSliderValue(value) ?: 0f

        SliderSettingDialog(
            title = meta.resolvedTitle(stringProvider),
            currentValue = currentVal,
            min = meta.min,
            max = meta.max,
            step = meta.step,
            onDismiss = { closeDialogs() },
            onValueSelected = { v ->
                val newValue = cf.fromUiSliderValue(v)
                if (newValue != null) {
                    handleSetValue(cf, newValue)
                } else {
                    showSnackbar("Invalid value")
                }
                closeDialogs()
            }
        )
    }

    if (showTextInput && cf?.meta != null) {
        val meta = cf.meta!!
        @Suppress("UNCHECKED_CAST")
        val stringField = cf as? SettingField<T, String>
        if (stringField != null) {
            InputDialog(
                title = meta.resolvedTitle(stringProvider),
                label = meta.resolvedTitle(stringProvider),
                value = stringField.get(value),
                onDismiss = { closeDialogs() },
                onConfirm = { newValue ->
                    handleSetValue(cf, newValue)
                    closeDialogs()
                },
                validator = { input ->
                    if (meta.validation != null) {
                        meta.validate(input, stringProvider) is ValidationResult.Valid
                    } else true
                }
            )
        } else {
            closeDialogs()
        }
    }

    if (showTimePicker && cf?.meta != null) {
        val meta = cf.meta!!
        @Suppress("UNCHECKED_CAST")
        val intField = cf as? SettingField<T, Int>
        if (intField != null) {
            TimePickerSettingDialog(
                title = meta.resolvedTitle(stringProvider),
                currentMinutes = intField.get(value),
                onDismiss = { closeDialogs() },
                onTimeSelected = { minutes ->
                    handleSetValue(cf, minutes)
                    closeDialogs()
                }
            )
        } else {
            closeDialogs()
        }
    }

    // Confirmation dialog: explicit isAction flag replaces the fragile
    // `value == Unit` sentinel, and the value is re-validated at confirm time
    // (validate-then-confirm window) plus re-checked against dependencies.
    pendingConfirmation?.let { pending ->
        SettingConfirmationDialog(
            config = pending.config,
            onConfirm = {
                if (pending.isAction) {
                    val actionClass = pending.field.meta?.actionClass
                    if (actionClass != null) {
                        scope.launch {
                            runCatching { onAction(actionClass) }
                                .onFailure { showSnackbar(it.message ?: "Action failed") }
                        }
                    }
                } else {
                    val meta = pending.field.meta
                    if (meta?.validation != null) {
                        when (val result = meta.validate(pending.value, stringProvider)) {
                            is ValidationResult.Valid -> { /* proceed */ }
                            is ValidationResult.Invalid -> {
                                showSnackbar(result.message)
                                pendingConfirmation = null
                                return@SettingConfirmationDialog
                            }
                        }
                    }
                    if (!schema.isEnabled(value, pending.field)) {
                        showSnackbar("Setting is disabled")
                        pendingConfirmation = null
                        return@SettingConfirmationDialog
                    }
                    onSet(pending.field.name, pending.value)
                }
                pendingConfirmation = null
            },
            onDismiss = { pendingConfirmation = null }
        )
    }
}

private data class PendingConfirmation<T>(
    val field: SettingField<T, *>,
    val value: Any,
    val isAction: Boolean,
    val config: ConfirmationConfig,
)

/** Shared rounding: dialog and subtitle must agree (round, not truncate). */
internal fun formatSliderValue(value: Float, step: Float): String {
    return if (step < 1f) {
        val v = (value * 10f).roundToInt() / 10f
        v.toString()
    } else {
        value.roundToInt().toString()
    }
}

private fun formatMinutesOfDay(totalMinutes: Int, use24Hour: Boolean = true): String {
    val clamped = totalMinutes.coerceIn(0, 1439)
    val hour = clamped / 60
    val minute = clamped % 60

    return if (use24Hour) {
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    } else {
        val suffix = if (hour < 12) "AM" else "PM"
        val hour12 = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        "$hour12:${minute.toString().padStart(2, '0')} $suffix"
    }
}