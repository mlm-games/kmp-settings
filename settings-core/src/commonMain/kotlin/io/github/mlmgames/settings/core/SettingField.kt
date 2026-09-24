package io.github.mlmgames.settings.core

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

object SettingFieldStorage {
    const val NAMESPACE: String = "__kmp_settings_v2__:"
    const val NULL_NAMESPACE: String = "__kmp_settings_v2__:null:"

    fun valueKeyName(keyName: String, kind: String): String =
        "$NAMESPACE$kind:${keyName.length}:$keyName"

    fun nullKeyName(keyName: String, kind: String): String =
        "$NULL_NAMESPACE$kind:${keyName.length}:$keyName"

    fun isReservedKeyName(keyName: String): Boolean =
        keyName.startsWith(NAMESPACE) || keyName.startsWith(NULL_NAMESPACE)
}

enum class SettingFieldCapability {
    TOGGLE,
    SLIDER,
    DROPDOWN,
    TEXT_INPUT,
    ACTION,
}

interface SettingField<T, V> {
    /** Property name in the data class */
    val name: String

    /** DataStore key name */
    val keyName: String

    /** UI metadata (null for @Persisted-only fields) */
    val meta: SettingMeta?

    /** Get value from model */
    fun get(model: T): V

    /** Set value in model (returns new model) */
    fun set(model: T, value: V): T

    /** Read from preferences */
    fun read(prefs: Preferences): V?

    /** Write to preferences */
    fun write(prefs: MutablePreferences, value: V)

    /** Convert value to UI slider float. Returns null if not applicable. */
    fun toUiSliderValue(model: T): Float? = null

    /** Convert UI slider float back to value. Returns null if not applicable. */
    fun fromUiSliderValue(value: Float): V? = null

    /** Convert value to UI dropdown index. Returns null if not applicable. */
    fun toUiDropdownIndex(model: T): Int? = null

    /** Convert UI dropdown index back to value. Returns null if not applicable. */
    fun fromUiDropdownIndex(index: Int): V? = null

    fun toUiToggleValue(model: T): Boolean? = null

    fun fromUiToggleValue(value: Boolean): V? = null

    val capabilities: Set<SettingFieldCapability>
        get() = emptySet()

    fun supports(capability: SettingFieldCapability): Boolean = capability in capabilities

    /** Get dropdown options. Returns null if not applicable. */
    fun getDropdownOptions(): List<String>? = null

    /** Encode a typed value to a type-prefixed string for backup export. */
    fun encodeValue(value: V): String? = null

    /** Decode a type-prefixed string back to a typed value for backup import. */
    fun decodeValue(encoded: String): V? = null

    /**
     * Whether DataStore currently holds a value for this field.
     * Distinguishes explicit null (marker present) from absent.
     */
    fun hasValue(prefs: Preferences): Boolean = read(prefs) != null

    fun isStoredValueValid(prefs: Preferences): Boolean = true

    /**
     * Whether the stored value explicitly represents null.
     * Only meaningful when [hasValue] is true. Default false for non-nullable fields.
     */
    fun isExplicitNull(prefs: Preferences): Boolean = false

    /** Remove any stored value (all physical keys) for this field. */
    fun clear(prefs: MutablePreferences) {}

    val isPersisted: Boolean
        get() = true

    val supportsExplicitNull: Boolean
        get() = false

    fun writeNullable(prefs: MutablePreferences, value: Any?): Boolean {
        if (value == null) {
            if (!supportsExplicitNull) return false
            @Suppress("UNCHECKED_CAST")
            write(prefs, null as V)
        } else {
            @Suppress("UNCHECKED_CAST")
            write(prefs, value as V)
        }
        return true
    }

    /** False for non-persisted placeholders (e.g. Button/Unit). Excluded from reset-all. */
    val isResettable: Boolean
        get() = isPersisted

    val resetConfirmation: String?
        get() = meta?.confirmReset

    val resetConfirmationKey: String?
        get() = meta?.confirmResetKey

    val isUnit: Boolean
        get() = false

    val isNullableString: Boolean
        get() = false

    val physicalKeys: List<Preferences.Key<*>>
        get() = emptyList()

    val backupTypeTag: String?
        get() = null

    val nullableBackupTypeTag: String?
        get() = backupTypeTag

    fun rawStoredValue(prefs: Preferences): String? {
        if (!hasValue(prefs)) return null
        if (isExplicitNull(prefs)) return "n:"
        val tag = backupTypeTag ?: physicalKeys.asSequence()
            .mapNotNull { physicalKeyTag(it.name) }
            .firstOrNull() ?: return null
        val keyNames = physicalKeys.mapTo(hashSetOf()) { it.name }
        val stored = prefs.asMap().entries.firstOrNull { it.key.name in keyNames }?.value ?: return null
        val payload = when (stored) {
            is String -> stored
            is Boolean -> stored.toString()
            is Number -> stored.toString()
            is ByteArray -> stored.joinToString(",") { it.toString() }
            else -> stored.toString()
        }
        return "$tag:$payload"
    }
}

private fun physicalKeyTag(name: String): String? {
    val kind = when {
        name.startsWith(SettingFieldStorage.NULL_NAMESPACE) ->
            name.removePrefix(SettingFieldStorage.NULL_NAMESPACE).substringBefore(':')
        name.startsWith(SettingFieldStorage.NAMESPACE) ->
            name.removePrefix(SettingFieldStorage.NAMESPACE).substringBefore(':')
        else -> return null
    }
    return when (kind) {
        "boolean" -> "b"
        "nullable_boolean" -> "b1"
        "int" -> "i"
        "nullable_int" -> "i1"
        "long" -> "l"
        "nullable_long" -> "l1"
        "float" -> "f"
        "nullable_float" -> "f1"
        "double" -> "d"
        "nullable_double" -> "d1"
        "string" -> "s"
        "nullable_string" -> "s1"
        "enum" -> "e"
        "nullable_enum" -> "e"
        "enum_ordinal" -> "eo"
        "serialized" -> "j"
        "nullable_serialized" -> "j1"
        "string_list" -> "ls"
        "int_list" -> "li"
        "long_list" -> "ll"
        "map" -> "m"
        "string_set" -> "ss"
        else -> null
    }
}