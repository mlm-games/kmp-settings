package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingFieldCapability
import io.github.mlmgames.settings.core.SettingMeta

class UnitField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Unit,
    private val setter: (T, Unit) -> T,
) : SettingField<T, Unit> {
    override fun get(model: T): Unit = getter(model)
    override fun set(model: T, value: Unit): T = setter(model, value)
    override fun read(prefs: Preferences): Unit? = null
    override fun write(prefs: MutablePreferences, value: Unit) = Unit
    override fun hasValue(prefs: Preferences): Boolean = false
    override fun clear(prefs: MutablePreferences) = Unit
    override val isPersisted: Boolean
        get() = false
    override val isResettable: Boolean
        get() = false
    override val isUnit: Boolean
        get() = true
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.ACTION)
}
