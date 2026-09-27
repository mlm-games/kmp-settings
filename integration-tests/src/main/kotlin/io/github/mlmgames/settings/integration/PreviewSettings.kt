package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.SchemaVersion
import kotlinx.serialization.Serializable

enum class PreviewMode { On, Private, Off }

/**
 * Stands in for a setting that changed type rather than name: it used to be a boolean
 * toggle, and is now a three-way enum.
 */
@Serializable
@SchemaVersion(3)
data class PreviewSettings(
    @Persisted
    val previews: PreviewMode = PreviewMode.On,
)
