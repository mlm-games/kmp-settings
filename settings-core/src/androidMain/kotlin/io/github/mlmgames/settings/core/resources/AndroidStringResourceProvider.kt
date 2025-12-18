package io.github.mlmgames.settings.core.resources

import android.content.Context

class AndroidStringResourceProvider(
    private val context: Context
) : StringResourceProvider {
    override fun getString(resId: Int): String =
        if (resId != 0) context.getString(resId) else ""

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        if (resId != 0) context.getString(resId, *formatArgs) else ""

    override fun getStringArray(resId: Int): List<String> =
        if (resId != 0) context.resources.getStringArray(resId).toList() else emptyList()
}