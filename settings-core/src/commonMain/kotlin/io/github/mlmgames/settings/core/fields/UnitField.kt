package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta

/**
 * No-op field used for Button settings backed by `Unit`.
 *
 * - Not persisted (read() always null, write() does nothing)
 * - Getter/setter exist only to satisfy SettingField<T, V>
 */
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

    override fun write(prefs: MutablePreferences, value: Unit) {
        // Intentionally no-op: Unit-backed button settings are not persisted.
    }
}