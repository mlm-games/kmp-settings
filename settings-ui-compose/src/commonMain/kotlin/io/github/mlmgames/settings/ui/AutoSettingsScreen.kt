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
import io.github.mlmgames.settings.core.annotations.ValidationResult
import io.github.mlmgames.settings.core.types.*
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
 */
@Composable
fun <T> AutoSettingsScreen(
    schema: SettingsSchema<T>,
    value: T,
    onSet: (name: String, value: Any) -> Unit,
    onAction: suspend (KClass<out SettingAction>) -> Unit = {},
    modifier: Modifier = Modifier,
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

    val grouped = remember(schema) { schema.groupedByCategory() }
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
            schema.orderedCategories().forEach { categoryClass ->
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
                                val options = meta.resolvedOptions(stringProvider)

                                val customHandler = customHandlerMap[meta.type]
                                if (customHandler != null) {
                                    @Suppress("UNCHECKED_CAST")
                                    customHandler.render(field, meta, value, enabled, onSet)
                                    return@forEach
                                }

                                when (meta.type) {
                                    Toggle::class -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val bf = field as? SettingField<T, Boolean>
                                        if (bf != null) {
                                            SettingsToggle(
                                                title = title,
                                                description = description,
                                                checked = bf.get(value),
                                                enabled = enabled,
                                                onCheckedChange = { handleSetValue(field, it) }
                                            )
                                        }
                                    }

                                    Dropdown::class -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val intField = field as? SettingField<T, Int>
                                        if (intField != null && options.isNotEmpty()) {
                                            val idx = intField.get(value)
                                            SettingsItem(
                                                title = title,
                                                subtitle = options.getOrNull(idx) ?: "Unknown",
                                                description = description,
                                                enabled = enabled,
                                                onClick = { currentField = field; showDropdown = true }
                                            )
                                        }
                                    }

                                    Slider::class -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val floatField = field as? SettingField<T, Float>
                                        @Suppress("UNCHECKED_CAST")
                                        val intField = field as? SettingField<T, Int>

                                        val subtitle = when {
                                            floatField != null -> floatField.get(value).roundToOneDecimal()
                                            intField != null -> intField.get(value).toString()
                                            else -> ""
                                        }

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
        @Suppress("UNCHECKED_CAST")
        val intField = cf as? SettingField<T, Int>
        if (intField != null) {
            val options = meta.resolvedOptions(stringProvider)
            DropdownSettingDialog(
                title = meta.resolvedTitle(stringProvider),
                options = options,
                selectedIndex = intField.get(value),
                onDismiss = { showDropdown = false },
                onOptionSelected = { idx ->
                    handleSetValue(cf, idx)
                    showDropdown = false
                }
            )
        } else {
            showDropdown = false
        }
    }

    if (showSlider && cf?.meta != null) {
        val meta = cf.meta!!
        @Suppress("UNCHECKED_CAST")
        val floatField = cf as? SettingField<T, Float>
        @Suppress("UNCHECKED_CAST")
        val intField = cf as? SettingField<T, Int>

        val currentVal = when {
            floatField != null -> floatField.get(value)
            intField != null -> intField.get(value).toFloat()
            else -> 0f
        }

        SliderSettingDialog(
            title = meta.resolvedTitle(stringProvider),
            currentValue = currentVal,
            min = meta.min,
            max = meta.max,
            step = meta.step,
            onDismiss = { showSlider = false },
            onValueSelected = { v ->
                when {
                    floatField != null -> handleSetValue(cf, v)
                    intField != null -> handleSetValue(cf, v.toInt())
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

fun Float.roundToOneDecimal(): String {
    return ((this * 10.0).roundToInt() / 10.0).toString()
}