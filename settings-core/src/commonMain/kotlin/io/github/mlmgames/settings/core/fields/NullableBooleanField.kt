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
    private val key = stringPreferencesKey("${keyName}_nullable")

    override fun get(model: T): Boolean? = getter(model)
    override fun set(model: T, value: Boolean?): T = setter(model, value)

    override fun read(prefs: Preferences): Boolean? = when (prefs[key]) {
        "true" -> true
        "false" -> false
        else -> null
    }

    override fun write(prefs: MutablePreferences, value: Boolean?) {
        when (value) {
            true -> prefs[key] = "true"
            false -> prefs[key] = "false"
            null -> prefs.remove(key)
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
            else -> null
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

    private val key = longPreferencesKey("${keyName}_nullable")

    override fun get(model: T): Int? = getter(model)
    override fun set(model: T, value: Int?): T = setter(model, value)

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
        val v = encoded.substringAfter(':').toLongOrNull() ?: return null
        return if (v == NULL_SENTINEL) null else v.toInt()
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
    private val key = stringPreferencesKey("${keyName}_nullable_long")

    override fun get(model: T): Long? = getter(model)
    override fun set(model: T, value: Long?): T = setter(model, value)
    override fun read(prefs: Preferences): Long? = prefs[key]?.toLongOrNull()

    override fun write(prefs: MutablePreferences, value: Long?) {
        if (value == null) prefs.remove(key) else prefs[key] = value.toString()
    }

    override fun encodeValue(value: Long?): String {
        if (value == null) return "l:"
        return "l:$value"
    }

    override fun decodeValue(encoded: String): Long? {
        val v = encoded.substringAfter(':')
        if (v.isEmpty()) return null
        return v.toLongOrNull()
    }
}

class NullableFloatField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Float?,
    private val setter: (T, Float?) -> T,
) : SettingField<T, Float?> {
    private val key = floatPreferencesKey("${keyName}_nullable")

    override fun get(model: T): Float? = getter(model)
    override fun set(model: T, value: Float?): T = setter(model, value)

    override fun read(prefs: Preferences): Float? {
        val stored = prefs[key] ?: return null
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
    }
}

class NullableDoubleField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Double?,
    private val setter: (T, Double?) -> T,
) : SettingField<T, Double?> {
    private val key = doublePreferencesKey("${keyName}_nullable")

    override fun get(model: T): Double? = getter(model)
    override fun set(model: T, value: Double?): T = setter(model, value)

    override fun read(prefs: Preferences): Double? {
        val stored = prefs[key] ?: return null
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
        private const val NULL_SENTINEL = "\u0000__NULL__\u0000"
    }

    private val key = stringPreferencesKey("${keyName}_nullable")

    override fun get(model: T): String? = getter(model)
    override fun set(model: T, value: String?): T = setter(model, value)

    override fun read(prefs: Preferences): String? {
        val stored = prefs[key] ?: return null
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