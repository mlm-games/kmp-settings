package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.NoReset
import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.SchemaVersion
import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.annotations.SettingPlatform
import io.github.mlmgames.settings.core.types.Dropdown
import io.github.mlmgames.settings.core.types.Slider
import io.github.mlmgames.settings.core.types.Toggle
import kotlinx.serialization.Serializable

@Serializable
@SchemaVersion(1)
data class IntegrationSettings(
    @Setting(title = "Enabled", category = General::class, type = Toggle::class)
    val enabled: Boolean = false,
    @Setting(title = "Level", category = General::class, type = Slider::class, min = 0f, max = 10f)
    val level: Int = 5,
    @Setting(title = "Mode", category = General::class, type = Dropdown::class)
    val mode: IntegrationMode = IntegrationMode.Standard,
    @Setting(title = "Long level", category = General::class, type = Slider::class, min = 0f, max = 10f)
    val longLevel: Long = 5L,
    @Setting(title = "Double level", category = General::class, type = Slider::class, min = 0f, max = 1f, step = 0.1f)
    val doubleLevel: Double = 0.5,
    @Setting(title = "Text choice", category = General::class, type = Dropdown::class, options = ["one", "two"])
    val textChoice: String = "one",
    @Persisted
    @NoReset
    val revision: Long = 0L,
    @Persisted
    val optionalInt: Int? = 42,
    @Persisted
    val optionalText: String? = "default",
    @Persisted
    val optionalFloat: Float? = 1f,
    @Persisted
    val optionalDouble: Double? = 1.0,
    @Persisted
    val optionalLong: Long? = 1L,
    @Persisted
    val optionalMode: IntegrationMode? = IntegrationMode.Standard,
    @Setting(title = "When", category = General::class, type = Toggle::class)
    val `when`: Boolean = false,
    @Setting(title = "Android only", category = General::class, type = Toggle::class, platforms = [SettingPlatform.ANDROID])
    val androidOnly: Boolean = false,
    @Setting(title = "iOS only", category = General::class, type = Toggle::class, platforms = [SettingPlatform.IOS])
    val iosOnly: Boolean = false,
)
