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
import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.core.resources.StringResourceProvider
import io.github.mlmgames.settings.core.resources.getStringOrDefault
import io.github.mlmgames.settings.core.resources.resolveString
import io.github.mlmgames.settings.core.types.Button
import io.github.mlmgames.settings.core.types.Dropdown
import io.github.mlmgames.settings.core.types.Slider
import io.github.mlmgames.settings.core.types.TextInput
import io.github.mlmgames.settings.core.types.TimePickerType
import io.github.mlmgames.settings.core.types.Toggle
import io.github.mlmgames.settings.ui.components.*
import io.github.mlmgames.settings.ui.dialogs.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
        onSet: (name: String, value: Any?) -> Unit,
    ) -> Unit
)

/**
 * Configuration for category display.
 */
data class CategoryConfig(
    val categoryClass: KClass<*>,
    val title: String,
    val titleRes: Int = 0,
    val titleKey: String = "",
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
 * @param actionTrailingContent Map of action classes to their trailing content
 */
@Composable
fun <T> AutoSettingsScreen(
    schema: SettingsSchema<T>,
    value: T,
    onSet: (name: String, value: Any?) -> Unit,
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
    val currentSchema by rememberUpdatedState(schema)
    val currentValue by rememberUpdatedState(value)
    val currentOnSet by rememberUpdatedState(onSet)
    val currentOnAction by rememberUpdatedState(onAction)
    val currentStringProvider by rememberUpdatedState(stringProvider)

    val internalSnackbarHostState = remember { SnackbarHostState() }
    val effectiveSnackbarHostState = snackbarHostState ?: internalSnackbarHostState
    val renderInternalSnackbarHost = snackbarHostState == null
    val currentSnackbarHostState by rememberUpdatedState(effectiveSnackbarHostState)
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    val showSnackbar: (String) -> Unit = { message ->
        snackbarJob?.cancel()
        snackbarJob = scope.launch {
            currentSnackbarHostState.currentSnackbarData?.dismiss()
            currentSnackbarHostState.showSnackbar(message)
        }
    }

    var dialogKind by remember { mutableStateOf<DialogKind?>(null) }
    var currentField by remember { mutableStateOf<SettingField<T, *>?>(null) }
    var pendingConfirmation by remember { mutableStateOf<PendingConfirmation<T>?>(null) }
    val inFlightActions = remember { mutableStateMapOf<KClass<out SettingAction>, Boolean>() }
    val toggleDrafts = remember { mutableStateMapOf<String, ToggleDraft>() }

    val openDialog: (SettingField<T, *>, DialogKind) -> Unit = { field, kind ->
        if (field.meta != null && pendingConfirmation == null) {
            currentField = field
            dialogKind = kind
        }
    }
    val closeDialogs: () -> Unit = {
        dialogKind = null
        currentField = null
    }

    val grouped = remember(schema, platform) { schema.groupedByCategory(platform) }
    val orderedCategories = remember(schema, platform) { schema.orderedCategories(platform) }
    val categoryConfigMap = remember(categoryConfigs) {
        categoryConfigs.associateBy { it.categoryClass }
    }
    val customHandlerMap = remember(customTypeHandlers) {
        customTypeHandlers.associateBy { it.typeClass }
    }

    val isFieldEnabled: (SettingField<T, *>) -> Boolean = { field ->
        try {
            currentSchema.isEnabled(currentValue, field)
        } catch (_: Exception) {
            false
        }
    }
    val isFieldVisible: (SettingField<T, *>) -> Boolean = { field ->
        field.meta?.isVisibleOnPlatform(platform) == true
    }
    val readFieldValue: (SettingField<T, *>) -> Any? = { field ->
        try {
            field.get(currentValue)
        } catch (_: RuntimeException) {
            null
        }
    }
    val canWriteNull: (SettingField<T, *>) -> Boolean = { field ->
        field.supportsExplicitNull
    }

    val commitSet: (SettingField<T, *>, Any?) -> Unit = { field, newValue ->
        try {
            currentOnSet(field.name, newValue)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (field.meta?.type == Toggle::class) toggleDrafts.remove(field.name)
            showSnackbar(error.message ?: safeText(currentStringProvider, SettingsTextKeys.SETTING_UPDATE_FAILED, "Setting update failed"))
        }
    }

    val handleSetValue: (SettingField<T, *>, Any?) -> Unit = handleSetValue@{ requestedField, newValue ->
        val field = currentSchema.fieldByName(requestedField.name) ?: return@handleSetValue
        val meta = field.meta ?: return@handleSetValue
        if (!isFieldVisible(field) || !isFieldEnabled(field)) {
            showSnackbar(safeText(currentStringProvider, SettingsTextKeys.SETTING_DISABLED, "Setting is disabled"))
            return@handleSetValue
        }
        if (newValue == null && !canWriteNull(field)) {
            showSnackbar(safeText(currentStringProvider, SettingsTextKeys.SETTING_CANNOT_BE_CLEARED, "This setting cannot be cleared"))
            return@handleSetValue
        }

        val validation = safeValidate(meta, newValue, currentStringProvider)
        if (validation is ValidationResult.Invalid) {
            showSnackbar(validation.message)
            return@handleSetValue
        }

        val oldValue = readFieldValue(field)
        if (meta.type != Toggle::class && newValue != null && oldValue == newValue) {
            return@handleSetValue
        }

        val confirmation = meta.confirmation
        if (confirmation != null) {
            if (pendingConfirmation == null) {
                pendingConfirmation = PendingConfirmation(
                    schema = currentSchema,
                    field = field,
                    value = newValue,
                    actionClass = null,
                    config = confirmation,
                )
            }
        } else {
            commitSet(field, newValue)
        }
    }

    val launchAction: (SettingField<T, *>, KClass<out SettingAction>) -> Unit = launchAction@{ field, actionClass ->
        if (inFlightActions.containsKey(actionClass)) return@launchAction
        val resolved = currentSchema.fieldByName(field.name)
        if (resolved == null || !isFieldVisible(resolved) || !isFieldEnabled(resolved)) {
            showSnackbar(safeText(currentStringProvider, SettingsTextKeys.SETTING_DISABLED, "Setting is disabled"))
            return@launchAction
        }

        inFlightActions[actionClass] = true
        scope.launch {
            try {
                currentOnAction(actionClass)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showSnackbar(error.message ?: safeText(currentStringProvider, SettingsTextKeys.ACTION_FAILED, "Action failed"))
            } finally {
                inFlightActions.remove(actionClass)
            }
        }
    }

    val handleAction: (SettingField<T, *>) -> Unit = handleAction@{ field ->
        val meta = field.meta ?: run {
            showSnackbar(safeText(currentStringProvider, SettingsTextKeys.ACTION_UNAVAILABLE, "Action unavailable"))
            return@handleAction
        }
        val actionClass = meta.actionClass ?: run {
            showSnackbar(safeText(currentStringProvider, SettingsTextKeys.ACTION_UNAVAILABLE, "Action unavailable"))
            return@handleAction
        }
        if (!isFieldVisible(field) || !isFieldEnabled(field)) {
            showSnackbar(safeText(currentStringProvider, SettingsTextKeys.SETTING_DISABLED, "Setting is disabled"))
            return@handleAction
        }
        if (inFlightActions.containsKey(actionClass)) return@handleAction

        val action = ActionRegistry.getAction(actionClass)
        val confirmation = meta.confirmation ?: action?.takeIf { it.requiresConfirmation }?.let {
            ConfirmationConfig(
                title = it.confirmationTitle,
                message = it.confirmationMessage,
                titleRes = 0,
                messageRes = 0,
                confirmText = "Confirm",
                confirmTextRes = 0,
                cancelText = "Cancel",
                cancelTextRes = 0,
                isDangerous = it.isDangerous,
                titleKey = it.confirmationTitleKey,
                messageKey = it.confirmationMessageKey,
                confirmTextKey = SettingsTextKeys.CONFIRM,
                cancelTextKey = SettingsTextKeys.CANCEL,
            )
        } ?: if (action == null) {
            val actionTitle = safeResolvedTitle(meta, currentStringProvider)
            ConfirmationConfig(
                title = "Run action",
                message = safeText(
                    currentStringProvider,
                    SettingsTextKeys.RUN_ACTION_MESSAGE,
                    "Run $actionTitle?",
                    actionTitle,
                ),
                titleRes = 0,
                messageRes = 0,
                confirmText = "Run",
                confirmTextRes = 0,
                cancelText = "Cancel",
                cancelTextRes = 0,
                isDangerous = true,
                titleKey = SettingsTextKeys.RUN_ACTION,
                confirmTextKey = SettingsTextKeys.RUN,
                cancelTextKey = SettingsTextKeys.CANCEL,
            )
        } else {
            null
        }
        if (confirmation != null) {
            if (pendingConfirmation == null) {
                pendingConfirmation = PendingConfirmation(
                    schema = currentSchema,
                    field = field,
                    value = null,
                    actionClass = actionClass,
                    config = confirmation,
                )
            }
        } else {
            launchAction(field, actionClass)
        }
    }

    val visibleFields = remember(grouped, platform) {
        grouped.values.flatten()
    }
    LaunchedEffect(value, schema, platform) {
        visibleFields.forEach { field ->
            val draft = toggleDrafts[field.name] ?: return@forEach
            if (!isFieldEnabled(field)) {
                toggleDrafts.remove(field.name)
                return@forEach
            }
            val actual = try {
                field.toUiToggleValue(value)
            } catch (_: RuntimeException) {
                null
            }
            if (actual is Boolean && actual == draft.requested && actual != draft.baseline) {
                toggleDrafts.remove(field.name)
            }
        }
        val visibleNames = visibleFields.mapTo(mutableSetOf()) { it.name }
        toggleDrafts.keys.filter { it !in visibleNames }.forEach(toggleDrafts::remove)
    }

    LaunchedEffect(dialogKind, currentField, value, platform, schema) {
        val field = currentField ?: return@LaunchedEffect
        val resolved = currentSchema.fieldByName(field.name) ?: run {
            closeDialogs()
            return@LaunchedEffect
        }
        val compatible = when (dialogKind) {
            null -> true
            DialogKind.DROPDOWN -> resolved.meta?.let { meta ->
                supportsDropdown(resolved, meta, currentValue)
            } == true
            DialogKind.SLIDER -> {
                val meta = resolved.meta
                meta != null && supportsSlider(resolved, meta, currentValue)
            }
            DialogKind.TEXT -> {
                val value = readFieldValue(resolved)
                value is String || value == null && canWriteNull(resolved)
            }
            DialogKind.TIME -> {
                val value = readFieldValue(resolved)
                value is Int || value == null && canWriteNull(resolved)
            }
        }
        if (
            dialogKind == null ||
            field !== resolved ||
            !isFieldVisible(resolved) ||
            !isFieldEnabled(resolved) ||
            !compatible
        ) {
            closeDialogs()
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

                val categoryKey = categoryClass.qualifiedName ?: categoryClass.toString()
                val categoryConfig = categoryConfigMap[categoryClass]
                val generatedTitleRes = currentSchema.categoryTitleResources[categoryClass] ?: 0
                val generatedTitleKey = currentSchema.categoryTitleKeys[categoryClass].orEmpty()
                val categoryFallback = categoryConfig?.title?.ifBlank {
                    categoryClass.simpleName ?: safeText(currentStringProvider, SettingsTextKeys.CATEGORY, "Category")
                } ?: categoryClass.simpleName ?: safeText(currentStringProvider, SettingsTextKeys.CATEGORY, "Category")
                val categoryTitle = when {
                    categoryConfig?.titleKey?.isNotBlank() == true ->
                        safeResolveString(currentStringProvider, categoryConfig.titleKey, categoryConfig.titleRes, categoryFallback)
                    categoryConfig?.titleRes != 0 && categoryConfig != null ->
                        safeResourceString(currentStringProvider, categoryConfig.titleRes, categoryFallback)
                    categoryConfig?.title?.isNotBlank() == true -> categoryConfig.title
                    generatedTitleKey.isNotBlank() ->
                        safeResolveString(currentStringProvider, generatedTitleKey, generatedTitleRes, categoryFallback)
                    generatedTitleRes != 0 -> safeResourceString(currentStringProvider, generatedTitleRes, categoryFallback)
                    else -> categoryFallback
                }

                item(key = "header_$categoryKey") {
                    Text(
                        text = categoryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item(key = "section_$categoryKey") {
                    SettingsSection(title = "") {
                        Column {
                            fields.forEach { field ->
                                key(field.name) {
                                    val meta = field.meta
                                    if (meta != null) {
                                        val enabled = isFieldEnabled(field)
                                        val title = safeResolvedTitle(meta, currentStringProvider)
                                        val description = safeResolvedDescription(meta, currentStringProvider)
                                            .takeIf { it.isNotBlank() }
                                        val customHandler = customHandlerMap[meta.type]
                                        if (customHandler != null) {
                                            customHandler.render(field, meta, currentValue, enabled) { name, newValue ->
                                                val target = currentSchema.fieldByName(name)
                                                if (target != null) {
                                                    handleSetValue(target, newValue)
                                                }
                                            }
                                        } else {
                                            when (meta.type) {
                                                Toggle::class -> {
                                                    val fieldValue = try {
                                                        field.toUiToggleValue(currentValue)
                                                    } catch (_: RuntimeException) {
                                                        null
                                                    }
                                                    val nullable = canWriteNull(field)
                                                    if (fieldValue is Boolean || (fieldValue == null && nullable)) {
                                                        val checked = toggleDrafts[field.name]?.requested
                                                            ?: fieldValue
                                                        if (nullable) {
                                                            SettingsNullableToggle(
                                                                title = title,
                                                                checked = checked,
                                                                description = description,
                                                                enabled = enabled,
                                                                onCheckedChange = {
                                                                    val baseline = fieldValue == true
                                                                    val next = !(toggleDrafts[field.name]?.requested ?: baseline)
                                                                    val converted = try {
                                                                        field.fromUiToggleValue(next)
                                                                    } catch (_: RuntimeException) {
                                                                        null
                                                                    }
                                                                    if (converted is Boolean) {
                                                                        toggleDrafts[field.name] = ToggleDraft(converted, baseline)
                                                                        handleSetValue(field, converted)
                                                                    }
                                                                },
                                                                onClear = {
                                                                    toggleDrafts.remove(field.name)
                                                                    handleSetValue(field, null)
                                                                },
                                                            )
                                                        } else {
                                                            SettingsToggle(
                                                                title = title,
                                                                description = description,
                                                                checked = checked == true,
                                                                enabled = enabled,
                                                                onCheckedChange = {
                                                                    val baseline = fieldValue == true
                                                                    val next = !(toggleDrafts[field.name]?.requested ?: baseline)
                                                                    val converted = try {
                                                                        field.fromUiToggleValue(next)
                                                                    } catch (_: RuntimeException) {
                                                                        null
                                                                    }
                                                                    if (converted is Boolean) {
                                                                        toggleDrafts[field.name] = ToggleDraft(converted, baseline)
                                                                        handleSetValue(field, converted)
                                                                    }
                                                                },
                                                            )
                                                        }
                                                    } else {
                                                        UnsupportedSettingRow(title, description)
                                                    }
                                                }

                                                Dropdown::class -> {
                                                    val options = resolveDropdownLabels(
                                                        field,
                                                        meta,
                                                        currentStringProvider,
                                                    )
                                                    val index = try {
                                                        field.toUiDropdownIndex(currentValue)
                                                    } catch (_: RuntimeException) {
                                                        null
                                                    }
                                                    val nullable = canWriteNull(field)
                                                    if (supportsDropdown(field, meta, currentValue)) {
                                                        val subtitle = if (nullable && index == null) {
                                                            safeText(currentStringProvider, SettingsTextKeys.NOT_SET, "(not set)")
                                                        } else {
                                                            options.getOrNull(index ?: -1) ?: safeText(currentStringProvider, SettingsTextKeys.UNKNOWN, "Unknown")
                                                        }
                                                        SettingsItem(
                                                            title = title,
                                                            subtitle = subtitle,
                                                            description = description,
                                                            enabled = enabled && (options.isNotEmpty() || nullable),
                                                            onClick = {
                                                                openDialog(field, DialogKind.DROPDOWN)
                                                            },
                                                        )
                                                    } else {
                                                        UnsupportedSettingRow(title, description)
                                                    }
                                                }

                                                Slider::class -> {
                                                    val sliderValue = try {
                                                        field.toUiSliderValue(currentValue)?.takeIf { it.isFinite() }
                                                    } catch (_: RuntimeException) {
                                                        null
                                                    }
                                                    val nullable = canWriteNull(field)
                                                    if (supportsSlider(field, meta, currentValue) &&
                                                        (sliderValue != null || nullable)
                                                    ) {
                                                        SettingsItem(
                                                            title = title,
                                                            subtitle = sliderValue?.let {
                                                                formatSliderValue(it, meta.step)
                                                            } ?: safeText(currentStringProvider, SettingsTextKeys.NOT_SET, "(not set)"),
                                                            description = description,
                                                            enabled = enabled,
                                                            onClick = {
                                                                openDialog(field, DialogKind.SLIDER)
                                                            },
                                                        )
                                                    } else {
                                                        UnsupportedSettingRow(title, description)
                                                    }
                                                }

                                                Button::class -> {
                                                    val actionClass = meta.actionClass
                                                    val actionPending = actionClass != null &&
                                                        pendingConfirmation?.actionClass == actionClass
                                                    SettingsAction(
                                                        title = title,
                                                        description = description,
                                                        enabled = enabled &&
                                                            actionClass != null &&
                                                            !inFlightActions.containsKey(actionClass) &&
                                                            !actionPending,
                                                        onClick = { handleAction(field) },
                                                        trailingContent = actionClass?.let {
                                                            actionTrailingContent[it]
                                                        },
                                                    )
                                                }

                                                TextInput::class -> {
                                                    val fieldValue = readFieldValue(field)
                                                    if (fieldValue is String || (fieldValue == null && canWriteNull(field))) {
                                                        SettingsItem(
                                                            title = title,
                                                            subtitle = fieldValue?.ifBlank { safeText(currentStringProvider, SettingsTextKeys.EMPTY, "(empty)") }
                                                                ?: safeText(currentStringProvider, SettingsTextKeys.NOT_SET, "(not set)"),
                                                            description = description,
                                                            enabled = enabled,
                                                            onClick = {
                                                                openDialog(field, DialogKind.TEXT)
                                                            },
                                                        )
                                                    } else {
                                                        UnsupportedSettingRow(title, description)
                                                    }
                                                }

                                                TimePickerType::class -> {
                                                    val fieldValue = readFieldValue(field)
                                                    if (fieldValue is Int || (fieldValue == null && canWriteNull(field))) {
                                                        SettingsItem(
                                                            title = title,
                                                            subtitle = fieldValue?.let {
                                                                formatMinutesOfDay(it, provider = currentStringProvider)
                                                            }
                                                                ?: safeText(currentStringProvider, SettingsTextKeys.NOT_SET, "(not set)"),
                                                            description = description,
                                                            enabled = enabled,
                                                            onClick = {
                                                                openDialog(field, DialogKind.TIME)
                                                            },
                                                        )
                                                    } else {
                                                        UnsupportedSettingRow(title, description)
                                                    }
                                                }

                                                else -> UnsupportedSettingRow(title, description)
                                            }
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

    val field = currentField?.let { requested ->
        currentSchema.fieldByName(requested.name) ?: requested
    }

    if (dialogKind == DialogKind.DROPDOWN && field?.meta != null) {
        val meta = field.meta!!
        val options = resolveDropdownLabels(field, meta, currentStringProvider)
        val nullable = canWriteNull(field)
        val selectedIndex = try {
            field.toUiDropdownIndex(currentValue)
        } catch (_: RuntimeException) {
            null
        }
        DropdownSettingDialog(
            title = safeResolvedTitle(meta, currentStringProvider),
            options = options,
            selectedIndex = selectedIndex ?: NULL_DROPDOWN_INDEX,
            allowNull = nullable,
            onDismiss = closeDialogs,
            onOptionSelected = { uiIndex ->
                val fieldIndex = when {
                    nullable && uiIndex == 0 -> NULL_DROPDOWN_INDEX
                    nullable -> uiIndex - 1
                    else -> uiIndex
                }
                if (fieldIndex == NULL_DROPDOWN_INDEX) {
                    handleSetValue(field, null)
                } else {
                    val newValue = try {
                        field.fromUiDropdownIndex(fieldIndex)
                    } catch (_: RuntimeException) {
                        null
                    }
                    if (newValue == null) {
                        showSnackbar(safeText(currentStringProvider, SettingsTextKeys.INVALID_SELECTION, "Invalid selection"))
                    } else {
                        handleSetValue(field, newValue)
                    }
                }
                closeDialogs()
            },
        )
    }

    if (dialogKind == DialogKind.SLIDER && field?.meta != null) {
        val meta = field.meta!!
        val nullable = canWriteNull(field)
        val currentValueForSlider = try {
            field.toUiSliderValue(currentValue)?.takeIf { it.isFinite() }
        } catch (_: RuntimeException) {
            null
        }
        if (currentValueForSlider != null || nullable) {
            SliderSettingDialog(
                title = safeResolvedTitle(meta, currentStringProvider),
                currentValue = currentValueForSlider,
                min = meta.min,
                max = meta.max,
                step = meta.step,
                allowNull = nullable,
                onDismiss = closeDialogs,
                onValueSelected = { sliderValue ->
                    val newValue = try {
                        field.fromUiSliderValue(sliderValue)
                    } catch (_: RuntimeException) {
                        null
                    }
                    if (newValue == null) {
                        showSnackbar(safeText(currentStringProvider, SettingsTextKeys.INVALID_VALUE, "Invalid value"))
                    } else {
                        handleSetValue(field, newValue)
                    }
                    closeDialogs()
                },
                onClear = if (nullable) {
                    {
                        handleSetValue(field, null)
                        closeDialogs()
                    }
                } else {
                    null
                },
            )
        }
    }

    if (dialogKind == DialogKind.TEXT && field?.meta != null) {
        val meta = field.meta!!
        val fieldValue = readFieldValue(field)
        if (fieldValue is String) {
            InputDialog(
                title = safeResolvedTitle(meta, currentStringProvider),
                label = safeResolvedTitle(meta, currentStringProvider),
                value = fieldValue,
                onDismiss = closeDialogs,
                onConfirm = { newValue ->
                    handleSetValue(field, newValue)
                    closeDialogs()
                },
                validator = { input ->
                    meta.validation?.let {
                        safeValidate(meta, input, currentStringProvider) is ValidationResult.Valid
                    } ?: true
                },
            )
        } else if (fieldValue == null && canWriteNull(field)) {
            NullableInputDialog(
                title = safeResolvedTitle(meta, currentStringProvider),
                label = safeResolvedTitle(meta, currentStringProvider),
                value = null,
                onDismiss = closeDialogs,
                onConfirm = { newValue ->
                    handleSetValue(field, newValue)
                    closeDialogs()
                },
                onClear = {
                    handleSetValue(field, null)
                    closeDialogs()
                },
                validator = { input ->
                    meta.validation?.let {
                        safeValidate(meta, input, currentStringProvider) is ValidationResult.Valid
                    } ?: true
                },
            )
        }
    }

    if (dialogKind == DialogKind.TIME && field?.meta != null) {
        val meta = field.meta!!
        val fieldValue = readFieldValue(field)
        if (fieldValue is Int || (fieldValue == null && canWriteNull(field))) {
            val currentMinutes = if (fieldValue is Int) fieldValue else null
            TimePickerSettingDialog(
                title = safeResolvedTitle(meta, currentStringProvider),
                currentMinutes = currentMinutes,
                onDismiss = closeDialogs,
                onTimeSelected = { minutes ->
                    handleSetValue(field, minutes)
                    closeDialogs()
                },
                onClear = if (canWriteNull(field)) {
                    {
                        handleSetValue(field, null)
                        closeDialogs()
                    }
                } else {
                    null
                },
            )
        }
    }

    pendingConfirmation?.let { pending ->
        SettingConfirmationDialog(
            config = pending.config,
            onConfirm = {
                if (pendingConfirmation != pending) return@SettingConfirmationDialog
                if (pending.schema !== currentSchema) {
                    pendingConfirmation = null
                    showSnackbar(safeText(currentStringProvider, SettingsTextKeys.SCHEMA_CHANGED, "Settings schema changed"))
                    return@SettingConfirmationDialog
                }
                val actionClass = pending.actionClass
                if (actionClass != null) {
                    val resolved = currentSchema.fieldByName(pending.field.name)
                    if (
                        resolved == null ||
                        pending.field !== resolved ||
                        resolved.meta?.actionClass != actionClass ||
                        !isFieldVisible(resolved) ||
                        !isFieldEnabled(resolved)
                    ) {
                        pendingConfirmation = null
                        showSnackbar(safeText(currentStringProvider, SettingsTextKeys.ACTION_NO_LONGER_AVAILABLE, "Action is no longer available"))
                        return@SettingConfirmationDialog
                    }
                    pendingConfirmation = null
                    launchAction(resolved, actionClass)
                } else {
                    val resolved = currentSchema.fieldByName(pending.field.name)
                    val meta = resolved?.meta
                    if (resolved == null || pending.field !== resolved || meta == null) {
                        pendingConfirmation = null
                        clearToggleDraft(pending, toggleDrafts)
                        showSnackbar(safeText(currentStringProvider, SettingsTextKeys.SETTING_NO_LONGER_AVAILABLE, "Setting is no longer available"))
                        return@SettingConfirmationDialog
                    }
                    val validation = safeValidate(meta, pending.value, currentStringProvider)
                    if (validation is ValidationResult.Invalid) {
                        pendingConfirmation = null
                        clearToggleDraft(pending, toggleDrafts)
                        showSnackbar(validation.message)
                        return@SettingConfirmationDialog
                    }
                    if (!isFieldVisible(resolved) || !isFieldEnabled(resolved)) {
                        pendingConfirmation = null
                        clearToggleDraft(pending, toggleDrafts)
                        showSnackbar(safeText(currentStringProvider, SettingsTextKeys.SETTING_DISABLED, "Setting is disabled"))
                        return@SettingConfirmationDialog
                    }
                    if (pending.value == null && !canWriteNull(resolved)) {
                        pendingConfirmation = null
                        showSnackbar(safeText(currentStringProvider, SettingsTextKeys.SETTING_CANNOT_BE_CLEARED, "This setting cannot be cleared"))
                        return@SettingConfirmationDialog
                    }
                    pendingConfirmation = null
                    clearToggleDraft(pending, toggleDrafts)
                    commitSet(resolved, pending.value)
                }
            },
            onDismiss = {
                if (pendingConfirmation == pending) {
                    clearToggleDraft(pending, toggleDrafts)
                    pendingConfirmation = null
                }
            },
        )
    }
}

private const val NULL_DROPDOWN_INDEX = -1

private enum class DialogKind {
    DROPDOWN,
    SLIDER,
    TEXT,
    TIME,
}

private data class PendingConfirmation<T>(
    val schema: SettingsSchema<T>,
    val field: SettingField<T, *>,
    val value: Any?,
    val actionClass: KClass<out SettingAction>?,
    val config: ConfirmationConfig,
)

private fun clearToggleDraft(
    pending: PendingConfirmation<*>,
    drafts: MutableMap<String, ToggleDraft>,
) {
    if (pending.actionClass != null) return
    val value = pending.value as? Boolean ?: return
    if (drafts[pending.field.name]?.requested == value) {
        drafts.remove(pending.field.name)
    }
}

private data class ToggleDraft(
    val requested: Boolean,
    val baseline: Boolean,
)

@Composable
private fun UnsupportedSettingRow(title: String, description: String?) {
    val provider = LocalStringResourceProvider.current
    SettingsItem(
        title = title,
        subtitle = safeText(provider, SettingsTextKeys.UNSUPPORTED_SETTING_TYPE, "Unsupported setting type"),
        description = description,
        enabled = false,
        onClick = {},
    )
}

private fun safeText(
    provider: StringResourceProvider,
    key: String,
    fallback: String,
): String = runCatching { provider.getStringOrDefault(key, fallback) }.getOrElse { fallback }

private fun safeText(
    provider: StringResourceProvider,
    key: String,
    fallback: String,
    vararg formatArgs: Any,
): String = runCatching { provider.getStringOrDefault(key, fallback, *formatArgs) }.getOrElse { fallback }

private fun safeResourceString(
    provider: StringResourceProvider,
    resource: Int,
    fallback: String,
): String = if (resource == 0) {
    fallback
} else {
    runCatching { provider.getStringOrDefault(resource, fallback) }.getOrElse { fallback }
}

private fun safeResolveString(
    provider: StringResourceProvider,
    key: String,
    resource: Int,
    fallback: String,
): String = runCatching { provider.resolveString(key, resource, fallback) }.getOrElse { fallback }

private fun safeResolvedTitle(meta: SettingMeta, provider: StringResourceProvider): String =
    runCatching { meta.resolvedTitle(provider) }.getOrNull()
        ?.ifBlank { meta.title }
        ?.ifBlank { safeText(provider, SettingsTextKeys.SETTING, "Setting") }
        ?: meta.title.ifBlank { safeText(provider, SettingsTextKeys.SETTING, "Setting") }

private fun safeResolvedDescription(meta: SettingMeta, provider: StringResourceProvider): String =
    runCatching { meta.resolvedDescription(provider) }.getOrElse { meta.description }

private fun safeValidate(
    meta: SettingMeta,
    value: Any?,
    provider: StringResourceProvider,
): ValidationResult = try {
    meta.validate(value, provider)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ValidationResult.Invalid(
        message = safeText(provider, SettingsTextKeys.VALIDATION_UNAVAILABLE, "Validation unavailable"),
        messageKey = SettingsTextKeys.VALIDATION_UNAVAILABLE,
    )
}

private fun <T> supportsDropdown(
    field: SettingField<T, *>,
    meta: SettingMeta,
    model: T,
): Boolean {
    if (SettingFieldCapability.DROPDOWN in field.capabilities) return true
    val hasOptions = try {
        field.getDropdownOptions()?.isNotEmpty() == true
    } catch (_: RuntimeException) {
        false
    }
    if (meta.options.isNotEmpty() || hasOptions) return true
    return try {
        field.toUiDropdownIndex(model) != null
    } catch (_: RuntimeException) {
        false
    }
}

private fun <T> supportsSlider(
    field: SettingField<T, *>,
    meta: SettingMeta,
    model: T,
): Boolean {
    if (SettingFieldCapability.SLIDER in field.capabilities) return true
    if (meta.valueKind in numericValueKinds && field.supportsExplicitNull) return true
    return try {
        field.toUiSliderValue(model)?.takeIf { it.isFinite() } != null
    } catch (_: RuntimeException) {
        false
    }
}

private val numericValueKinds = setOf(
    ValueKind.INT,
    ValueKind.LONG,
    ValueKind.FLOAT,
    ValueKind.DOUBLE,
)

internal fun resolveDropdownLabels(
    field: SettingField<*, *>,
    meta: SettingMeta,
    provider: StringResourceProvider,
): List<String> {
    val fieldOptions = try {
        field.getDropdownOptions()
    } catch (_: RuntimeException) {
        null
    }
    val declaredOptions = meta.options
    val resolvedOptions = try {
        meta.resolvedOptions(provider).orEmpty()
    } catch (_: RuntimeException) {
        emptyList()
    }

    if ((meta.optionsKey.isNotBlank() || meta.optionsRes != 0) &&
        declaredOptions.isNotEmpty() && resolvedOptions.size != declaredOptions.size
    ) {
        return fieldOptions ?: declaredOptions
    }
    if (resolvedOptions.isEmpty()) {
        return if (declaredOptions.isNotEmpty()) declaredOptions else fieldOptions.orEmpty()
    }
    if (fieldOptions == null) return resolvedOptions
    return if (resolvedOptions.size == fieldOptions.size) resolvedOptions else fieldOptions
}

internal fun formatSliderValue(value: Float, step: Float): String {
    val safeValue = if (value.isFinite()) value else 0f
    val safeStep = if (step.isFinite() && step > 0f) step else 1f
    return if (safeStep < 1f) {
        val rounded = (safeValue * 10f).roundToInt() / 10f
        rounded.toString()
    } else {
        safeValue.roundToInt().toString()
    }
}

private fun formatMinutesOfDay(
    totalMinutes: Int,
    use24Hour: Boolean = true,
    provider: StringResourceProvider,
): String {
    val clamped = totalMinutes.coerceIn(0, 1439)
    val hour = clamped / 60
    val minute = clamped % 60
    return if (use24Hour) {
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    } else {
        val suffix = safeText(
            provider,
            if (hour < 12) SettingsTextKeys.AM else SettingsTextKeys.PM,
            if (hour < 12) "AM" else "PM",
        )
        val hour12 = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        "$hour12:${minute.toString().padStart(2, '0')} $suffix"
    }
}
