package io.github.mlmgames.settings.core.resources

/**
 * Platform-agnostic string resource provider.
 */
interface StringResourceProvider {
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String
    fun getStringArray(resId: Int): List<String>
}

object NoOpStringResourceProvider : StringResourceProvider {
    override fun getString(resId: Int): String = ""
    override fun getString(resId: Int, vararg formatArgs: Any): String = ""
    override fun getStringArray(resId: Int): List<String> = emptyList()
}