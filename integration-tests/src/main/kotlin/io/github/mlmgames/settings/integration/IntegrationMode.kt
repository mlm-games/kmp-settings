package io.github.mlmgames.settings.integration

import kotlinx.serialization.Serializable

@Serializable
enum class IntegrationMode {
    Basic,
    Standard,
    Advanced,
}
