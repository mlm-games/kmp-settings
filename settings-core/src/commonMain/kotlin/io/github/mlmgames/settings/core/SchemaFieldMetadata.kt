package io.github.mlmgames.settings.core

data class SchemaFieldMetadata(
    val renamedFrom: String? = null,
    val renamedSinceVersion: Int? = null,
    val addedInVersion: Int? = null,
    val deprecated: Boolean = false,
    val deprecationMessage: String? = null,
    val removeInVersion: Int? = null,
)
