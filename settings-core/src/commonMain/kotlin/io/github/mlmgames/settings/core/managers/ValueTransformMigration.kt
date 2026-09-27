package io.github.mlmgames.settings.core.managers

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.PreferenceKind
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingFieldStorage
import io.github.mlmgames.settings.core.remote.RemoteCodec

/**
 * Rewrites a stored value into the shape its field now uses, for the case where a setting
 * changed type rather than name: a boolean toggle becoming a three-way enum, an int-encoded
 * enum becoming a string one, and so on.
 *
 * The old field is no longer in the schema, so [oldKey] and [oldKind] address it directly.
 * Both the namespaced and the plain legacy physical key are read, because a value written
 * before the namespaced scheme existed only has the plain one.
 *
 * [transform] returns the new value as text in the target field's own stored form, which the
 * migration decodes and verifies. Going through text rather than a typed value is what keeps
 * the target's type an implementation detail: the caller states what to store, not how.
 * Returning null leaves preferences untouched, which is how an unrecognised stored value
 * stays put instead of being silently overwritten.
 */
internal class ValueTransformMigration(
    override val fromVersion: Int,
    override val toVersion: Int,
    private val oldKey: String,
    private val oldKind: PreferenceKind,
    private val target: SettingField<*, *>,
    private val codec: RemoteCodec?,
    private val transform: (Any?) -> String?,
) : Migration {
    override suspend fun migrate(prefs: MutablePreferences) {
        val stored = readLegacy(prefs) ?: return
        val text = transform(stored) ?: return
        val value = codec?.decode(text) ?: target.fromRemoteValue(text) ?: return
        writeTarget(prefs, value)
        clearLegacy(prefs)
    }

    private fun readLegacy(prefs: MutablePreferences): Any? {
        oldKind.storageId?.let { storageId ->
            val namespaced = oldKind.key(SettingFieldStorage.valueKeyName(oldKey, storageId))
            if (namespaced in prefs) return prefs[namespaced]
        }
        val plain = oldKind.key(oldKey)
        return if (plain in prefs) prefs[plain] else null
    }

    private fun writeTarget(prefs: MutablePreferences, value: Any) {
        @Suppress("UNCHECKED_CAST")
        val typed = target as SettingField<Any?, Any?>
        typed.write(prefs, value)
        if (typed.read(prefs) != value) {
            throw IllegalStateException(
                "Value transform for '${target.name}' produced a value it cannot store: $value",
            )
        }
    }

    private fun clearLegacy(prefs: MutablePreferences) {
        val keep = target.physicalKeys.mapTo(hashSetOf()) { it.name }
        physicalKeyNames(oldKey)
            .filter { it !in keep }
            .forEach { prefs.remove(stringPreferencesKey(it)) }
    }
}
