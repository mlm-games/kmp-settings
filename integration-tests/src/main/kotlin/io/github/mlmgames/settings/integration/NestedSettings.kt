package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.types.Toggle
import kotlinx.serialization.Serializable

object First {
    @Serializable
    data class NestedSettings(
        @Setting(title = "First", category = General::class, type = Toggle::class)
        val enabled: Boolean = false,
    )
}

object Second {
    @Serializable
    data class NestedSettings(
        @Setting(title = "Second", category = General::class, type = Toggle::class)
        val enabled: Boolean = true,
    )
}
