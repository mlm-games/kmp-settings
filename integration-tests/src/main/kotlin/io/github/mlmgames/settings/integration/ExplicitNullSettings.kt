package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.Persisted
import kotlinx.serialization.Serializable

@Serializable
data class ExplicitNullSettings(
    @Persisted
    val value: String? = null,
)
