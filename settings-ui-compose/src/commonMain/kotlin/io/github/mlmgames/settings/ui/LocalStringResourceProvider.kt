package io.github.mlmgames.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mlmgames.settings.core.resources.NoOpStringResourceProvider
import io.github.mlmgames.settings.core.resources.StringResourceProvider

/**
 * Composition local for string resources.
 */
val LocalStringResourceProvider = staticCompositionLocalOf<StringResourceProvider> {
    NoOpStringResourceProvider
}

/**
 * Provide string resources to settings UI.
 */
@Composable
fun ProvideStringResources(
    provider: StringResourceProvider,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalStringResourceProvider provides provider,
        content = content
    )
}