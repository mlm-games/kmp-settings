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
import io.github.mlmgames.settings.core.types.*
import io.github.mlmgames.settings.ui.components.*
import io.github.mlmgames.settings.ui.dialogs.*
import kotlinx.coroutines.launch
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
    var currentField by remember { mutableStateOf<SettingField<T, *>?>(null) }

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

        // Check for confirmation requirement
        if (meta?.confirmation != null) {
            pendingConfirmation = PendingConfirmation(
                field = field,
                value = newValue,
                config = meta.confirmation!!
            )
        } else {
            onSet(field.name, newValue)
        }
    }

    // Handle button actions
    val handleAction: (SettingField<T, *>) -> Unit = handleAction@{ field ->
        val meta = field.meta ?: return@handleAction
        val actionClass = meta.actionClass ?: return@handleAction

        val action = ActionRegistry.getAction(actionClass)

        if (action?.requiresConfirmation == true) {
            pendingConfirmation = PendingConfirmation(
                field = field,
                value = Unit,
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

                item(key = "header_${categoryClass.simpleName}") {
                    Text(
                        text = categoryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item(key = "section_${categoryClass.simpleName}") {
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
                                    @Suppress("UNCHECKED_CAST")
                                    customHandler.render(field, meta, value, enabled, onSet)
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
                                        val options = field.getDropdownOptions()
                                            ?: meta.resolvedOptions(stringProvider)
                                        val idx = field.toUiDropdownIndex(value) ?: 0
                                        SettingsItem(
                                            title = title,
                                            subtitle = options.getOrNull(idx) ?: "Unknown",
                                            description = description,
                                            enabled = enabled,
                                            onClick = { currentField = field; showDropdown = true }
                                        )
                                    }

                                    Slider::class -> {
                                        val sliderVal = field.toUiSliderValue(value)
                                        val subtitle = sliderVal?.let {
                                            if (meta.step < 1f) {
                                                ((it * 10).toInt() / 10f).toString()
                                            } else {
                                                it.toInt().toString()
                                            }
                                        } ?: ""

                                        SettingsItem(
                                            title = title,
                                            subtitle = subtitle,
                                            description = description,
                                            enabled = enabled,
                                            onClick = { currentField = field; showSlider = true }
                                        )
                                    }

                                    Button::class -> {
                                        SettingsAction(
                                            title = title,
                                            description = description,
                                            enabled = enabled,
                                            onClick = { handleAction(field) }
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
                                                onClick = { currentField = field; showTextInput = true }
                                            )
                                        }
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

    // Dialogs
    val cf = currentField
    if (showDropdown && cf?.meta != null) {
        val meta = cf.meta!!
        val options = cf.getDropdownOptions() ?: meta.resolvedOptions(stringProvider)
        val currentIdx = cf.toUiDropdownIndex(value) ?: 0

        DropdownSettingDialog(
            title = meta.resolvedTitle(stringProvider),
            options = options,
            selectedIndex = currentIdx,
            onDismiss = { showDropdown = false },
            onOptionSelected = { idx ->
                val newValue = cf.fromUiDropdownIndex(idx)
                if (newValue != null) {
                    handleSetValue(cf, newValue)
                }
                showDropdown = false
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
            onDismiss = { showSlider = false },
            onValueSelected = { v ->
                val newValue = cf.fromUiSliderValue(v)
                if (newValue != null) {
                    handleSetValue(cf, newValue)
                }
                showSlider = false
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
                onDismiss = { showTextInput = false },
                onConfirm = { newValue ->
                    handleSetValue(cf, newValue)
                    showTextInput = false
                },
                validator = { input ->
                    if (meta.validation != null) {
                        meta.validate(input, stringProvider) is ValidationResult.Valid
                    } else true
                }
            )
        } else {
            showTextInput = false
        }
    }

    // Confirmation dialog
    pendingConfirmation?.let { pending ->
        SettingConfirmationDialog(
            config = pending.config,
            onConfirm = {
                if (pending.value == Unit) {
                    val actionClass = pending.field.meta?.actionClass
                    if (actionClass != null) {
                        scope.launch {
                            runCatching { onAction(actionClass) }
                                .onFailure { showSnackbar(it.message ?: "Action failed") }
                        }
                    }
                } else {
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
    val config: ConfirmationConfig,
)