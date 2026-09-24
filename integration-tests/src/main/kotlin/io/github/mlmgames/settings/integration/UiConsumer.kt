package io.github.mlmgames.settings.integration

import androidx.compose.runtime.Composable
import io.github.mlmgames.settings.ui.AutoSettingsScreen

@Composable
fun IntegrationSettingsScreen(
    value: IntegrationSettings,
    onSet: (String, Any?) -> Unit,
) {
    AutoSettingsScreen(
        schema = IntegrationSettingsSchema,
        value = value,
        onSet = onSet,
    )
}
