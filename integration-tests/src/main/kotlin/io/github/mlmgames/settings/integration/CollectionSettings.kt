package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.Serialized
import kotlinx.serialization.Serializable

@Serializable
data class CollectionSettings(
    @Persisted
    @Serialized
    val names: List<String> = listOf("default"),
    @Persisted
    @Serialized
    val optionalNames: List<String>? = null,
    @Persisted
    val intValues: Map<Int, String> = emptyMap(),
    @Persisted
    val longValues: Map<Long, Int> = emptyMap(),
)
