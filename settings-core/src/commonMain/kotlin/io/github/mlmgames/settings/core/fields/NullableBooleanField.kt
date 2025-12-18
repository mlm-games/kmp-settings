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
}