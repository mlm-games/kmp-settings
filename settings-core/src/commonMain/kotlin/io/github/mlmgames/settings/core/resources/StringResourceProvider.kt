package io.github.mlmgames.settings.core.resources

/**
 * Platform-agnostic string resource provider.
 */
interface StringResourceProvider {
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String
    fun getStringArray(resId: Int): List<String>

    fun getString(key: String): String = ""
    fun getString(key: String, vararg formatArgs: Any): String = ""
    fun getStringArray(key: String): List<String> = emptyList()
}

fun StringResourceProvider.getStringOrDefault(key: String, fallback: String): String =
    getString(key).ifBlank { fallback }

fun StringResourceProvider.getStringOrDefault(key: String, fallback: String, vararg formatArgs: Any): String =
    getString(key, *formatArgs).ifBlank { fallback }

fun StringResourceProvider.getStringOrDefault(resId: Int, fallback: String): String =
    if (resId == 0) fallback else getString(resId).ifBlank { fallback }

fun StringResourceProvider.resolveString(
    key: String,
    resId: Int,
    fallback: String,
): String = when {
    key.isNotBlank() -> getString(key).ifBlank {
        if (resId == 0) fallback else getString(resId).ifBlank { fallback }
    }
    resId != 0 -> getString(resId).ifBlank { fallback }
    else -> fallback
}

fun StringResourceProvider.resolveStringArray(
    key: String,
    resId: Int,
    fallback: List<String>,
): List<String> = when {
    key.isNotBlank() -> getStringArray(key).ifEmpty {
        if (resId == 0) fallback else getStringArray(resId).ifEmpty { fallback }
    }
    resId != 0 -> getStringArray(resId).ifEmpty { fallback }
    else -> fallback
}

object NoOpStringResourceProvider : StringResourceProvider {
    override fun getString(resId: Int): String = ""
    override fun getString(resId: Int, vararg formatArgs: Any): String = ""
    override fun getStringArray(resId: Int): List<String> = emptyList()
    override fun getString(key: String): String = ""
    override fun getString(key: String, vararg formatArgs: Any): String = ""
    override fun getStringArray(key: String): List<String> = emptyList()
}