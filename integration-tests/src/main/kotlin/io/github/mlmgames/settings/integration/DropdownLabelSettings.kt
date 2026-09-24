package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.types.Dropdown
import kotlinx.serialization.Serializable

@Serializable
data class DropdownLabelSettings(
    @Setting(
        title = "Language",
        titleKey = "settings.language.title",
        category = General::class,
        type = Dropdown::class,
        options = ["English", "Spanish", "French"],
        optionsKey = "settings.language.options",
    )
    val language: Int = 0,
)
