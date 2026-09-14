package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.*
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta

class NullableBooleanField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Boolean?,
    private val setter: (T, Boolean?) -> T,
) : SettingField<T, Boolean?> {
    companion object {
        internal const val NULL_MARKER = "__NULL__"
    }

    internal val key = stringPreferencesKey("${keyName}_nullable")
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): Boolean? = getter(model)
    override fun set(model: T, value: Boolean?): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun isExplicitNull(prefs: Preferences): Boolean =
        prefs[key] == NULL_MARKER
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): Boolean? = when (prefs[key]) {
        "true" -> true
        "false" -> false
        // Explicit-null marker, absent key, or corrupt value all decode to null.
        // Use hasValue()/isExplicitNull() to distinguish.
        else -> null
    }

    override fun write(prefs: MutablePreferences, value: Boolean?) {
        when (value) {
            true -> prefs[key] = "true"
            false -> prefs[key] = "false"
            // Explicit marker (not key removal) so explicit null survives
            // export/import, snapshots, and undo. Absent key = never set.
            null -> prefs[key] = NULL_MARKER
        }
    }

    override fun encodeValue(value: Boolean?): String = when (value) {
        true -> "b:true"
        false -> "b:false"
        null -> "b:"
    }

    override fun decodeValue(encoded: String): Boolean? {
        val v = encoded.substringAfter(':')
        return when (v) {
            "true" -> true
            "false" -> false
            "" -> null
            else -> throw IllegalArgumentException("Invalid nullable boolean value: $encoded")
        }
    }
}

class NullableIntField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Int?,
    private val setter: (T, Int?) -> T,
) : SettingField<T, Int?> {
    companion object {
        private const val NULL_SENTINEL = Long.MIN_VALUE
    }

    internal val key = longPreferencesKey("${keyName}_nullable")
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): Int? = getter(model)
    override fun set(model: T, value: Int?): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun isExplicitNull(prefs: Preferences): Boolean =
        prefs[key] == NULL_SENTINEL
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): Int? {
        val stored = prefs[key] ?: return null
        return if (stored == NULL_SENTINEL) null else stored.toInt()
    }

    override fun write(prefs: MutablePreferences, value: Int?) {
        prefs[key] = value?.toLong() ?: NULL_SENTINEL
    }

    override fun encodeValue(value: Int?): String {
        val v = value ?: NULL_SENTINEL
        return "i:$v"
    }

    override fun decodeValue(encoded: String): Int? {
        val v = encoded.substringAfter(':').toLongOrNull()
            ?: throw IllegalArgumentException("Invalid nullable int value: $encoded")
        if (v == NULL_SENTINEL) return null
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) {
            throw IllegalArgumentException("Int value out of range: $encoded")
        }
        return v.toInt()
    }

    override fun toUiSliderValue(model: T): Float? = getter(model)?.toFloat()
    override fun fromUiSliderValue(value: Float): Int? = value.toInt()
}

class NullableLongField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Long?,
    private val setter: (T, Long?) -> T,
) : SettingField<T, Long?> {
    companion object {
        internal const val NULL_MARKER = "__NULL__"
    }

    internal val key = stringPreferencesKey("${keyName}_nullable_long")
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): Long? = getter(model)
    override fun set(model: T, value: Long?): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun isExplicitNull(prefs: Preferences): Boolean =
        prefs[key] == NULL_MARKER
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): Long? {
        val stored = prefs[key] ?: return null
        if (stored == NULL_MARKER) return null
        // Corrupt (non-numeric) values decode to null; hasValue() stays true
        // and isExplicitNull() false so callers can tell corruption from null.
        return stored.toLongOrNull()
    }

    override fun write(prefs: MutablePreferences, value: Long?) {
        prefs[key] = value?.toString() ?: NULL_MARKER
    }

    override fun encodeValue(value: Long?): String {
        if (value == null) return "l:"
        return "l:$value"
    }

    override fun decodeValue(encoded: String): Long? {
        val v = encoded.substringAfter(':')
        if (v.isEmpty()) return null
        return v.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid nullable long value: $encoded")
    }
}

class NullableFloatField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Float?,
    private val setter: (T, Float?) -> T,
) : SettingField<T, Float?> {
    internal val key = floatPreferencesKey("${keyName}_nullable")
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): Float? = getter(model)
    override fun set(model: T, value: Float?): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun isExplicitNull(prefs: Preferences): Boolean =
        (prefs[key]?.isNaN() == true)
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): Float? {
        val stored = prefs[key] ?: return null
        // NaN is the explicit-null marker. A genuine NaN setting value is
        // indistinguishable from null and reads back as null (documented).
        return if (stored.isNaN()) null else stored
    }

    override fun write(prefs: MutablePreferences, value: Float?) {
        prefs[key] = value ?: Float.NaN
    }

    override fun toUiSliderValue(model: T): Float? = getter(model)
    override fun fromUiSliderValue(value: Float): Float? = value

    override fun encodeValue(value: Float?): String {
        if (value == null) return "f:"
        return "f:$value"
    }

    override fun decodeValue(encoded: String): Float? {
        val v = encoded.substringAfter(':')
        if (v.isEmpty()) return null
        return v.toFloatOrNull()
            ?: throw IllegalArgumentException("Invalid nullable float value: $encoded")
    }
}

class NullableDoubleField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Double?,
    private val setter: (T, Double?) -> T,
) : SettingField<T, Double?> {
    internal val key = doublePreferencesKey("${keyName}_nullable")
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): Double? = getter(model)
    override fun set(model: T, value: Double?): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun isExplicitNull(prefs: Preferences): Boolean =
        (prefs[key]?.isNaN() == true)
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): Double? {
        val stored = prefs[key] ?: return null
        // NaN is the explicit-null marker (see NullableFloatField).
        return if (stored.isNaN()) null else stored
    }

    override fun write(prefs: MutablePreferences, value: Double?) {
        prefs[key] = value ?: Double.NaN
    }

    override fun encodeValue(value: Double?): String {
        if (value == null) return "d:"
        return "d:$value"
    }

    override fun decodeValue(encoded: String): Double? {
        val v = encoded.substringAfter(':')
        if (v.isEmpty()) return null
        return v.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid nullable double value: $encoded")
    }
}

class NullableStringField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> String?,
    private val setter: (T, String?) -> T,
) : SettingField<T, String?> {
    companion object {
        internal const val NULL_SENTINEL = "__NULL__"
    }

    internal val key = stringPreferencesKey("${keyName}_nullable")
    internal val physicalKeys: List<Preferences.Key<*>> = listOf(key)

    override fun get(model: T): String? = getter(model)
    override fun set(model: T, value: String?): T = setter(model, value)

    override fun hasValue(prefs: Preferences): Boolean = key in prefs
    override fun isExplicitNull(prefs: Preferences): Boolean =
        prefs[key] == NULL_SENTINEL
    override fun clear(prefs: MutablePreferences) { prefs.remove(key) }

    override fun read(prefs: Preferences): String? {
        val stored = prefs[key] ?: return null
        // A genuine value equal to the sentinel reads back as null (documented).
        return if (stored == NULL_SENTINEL) null else stored
    }

    override fun write(prefs: MutablePreferences, value: String?) {
        prefs[key] = value ?: NULL_SENTINEL
    }

    override fun encodeValue(value: String?): String {
        val v = value ?: NULL_SENTINEL
        return "s:$v"
    }

    override fun decodeValue(encoded: String): String? {
        val v = encoded.substringAfter(':')
        return if (v == NULL_SENTINEL) null else v
    }
}
