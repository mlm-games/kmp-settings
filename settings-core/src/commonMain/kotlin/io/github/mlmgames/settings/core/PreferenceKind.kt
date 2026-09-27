package io.github.mlmgames.settings.core

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * DataStore preference type, for code that has to address a stored value by type rather
 * than through a generated field. Migrations need this when the field being read no longer
 * exists in the schema.
 *
 * [storageId] is the suffix the generated fields use for the namespaced physical key, or
 * null for types the library never namespaces.
 */
enum class PreferenceKind(val storageId: String?) {
    BOOLEAN("boolean"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    STRING("string"),
    STRING_SET("string_set"),
    BYTE_ARRAY(null),
    ;

    fun key(name: String): Preferences.Key<*> = when (this) {
        BOOLEAN -> booleanPreferencesKey(name)
        INT -> intPreferencesKey(name)
        LONG -> longPreferencesKey(name)
        FLOAT -> floatPreferencesKey(name)
        DOUBLE -> doublePreferencesKey(name)
        STRING -> stringPreferencesKey(name)
        STRING_SET -> stringSetPreferencesKey(name)
        BYTE_ARRAY -> byteArrayPreferencesKey(name)
    }
}
