package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.AddedInVersion
import io.github.mlmgames.settings.core.annotations.DeprecatedSetting
import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.RenamedFrom
import io.github.mlmgames.settings.core.annotations.SchemaVersion
import kotlinx.serialization.Serializable

typealias IntegrationCount = Int

@Serializable
@SchemaVersion(2)
data class AliasSettings(
    @RenamedFrom("old_count", sinceVersion = 2)
    @AddedInVersion(2)
    @DeprecatedSetting(removeInVersion = 3)
    @Persisted
    val count: IntegrationCount = 0,
)
