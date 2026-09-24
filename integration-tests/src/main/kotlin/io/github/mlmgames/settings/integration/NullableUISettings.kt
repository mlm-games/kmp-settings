package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.types.Dropdown
import io.github.mlmgames.settings.core.types.Slider
import io.github.mlmgames.settings.core.types.Toggle
import kotlinx.serialization.Serializable

@Serializable
data class NullableUISettings(
    @Setting(title = "Nullable toggle", category = General::class, type = Toggle::class)
    val toggle: Boolean? = null,
    @Setting(title = "Nullable slider", category = General::class, type = Slider::class, min = 0f, max = 10f)
    val slider: Int? = null,
    @Setting(title = "Nullable choice", category = General::class, type = Dropdown::class, options = ["one", "two"])
    val choice: String? = null,
)
