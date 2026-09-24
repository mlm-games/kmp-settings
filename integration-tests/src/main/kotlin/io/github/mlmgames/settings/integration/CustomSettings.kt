package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.Serialized
import io.github.mlmgames.settings.core.annotations.SerializedWith
import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.annotations.SettingSerializer
import io.github.mlmgames.settings.core.types.SettingTypeMarker
import kotlinx.serialization.Serializable

object CustomConfigType : SettingTypeMarker

@Serializable
data class CustomConfig(
    val value: Int = 1,
)

class CustomConfigSerializer : SettingSerializer<CustomConfig> {
    override fun serialize(value: CustomConfig): String = value.value.toString()

    override fun deserialize(json: String): CustomConfig = CustomConfig(json.toInt())
}

@Serializable
data class CustomSettings(
    @Setting(title = "Config", category = General::class, type = CustomConfigType::class)
    @Serialized
    @SerializedWith(CustomConfigSerializer::class)
    val config: CustomConfig = CustomConfig(),
)
