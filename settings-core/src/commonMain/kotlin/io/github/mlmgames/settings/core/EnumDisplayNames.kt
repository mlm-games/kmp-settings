package io.github.mlmgames.settings.core

fun formatEnumDisplayName(name: String): String {
    if (name.isEmpty()) return name
    return name.split('_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}
