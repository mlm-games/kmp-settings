package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingFieldCapability
import io.github.mlmgames.settings.core.SettingMeta
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class BooleanField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Boolean,
    private val setter: (T, Boolean) -> T,
) : SettingField<T, Boolean> {
    internal val key = booleanPreferencesKey(storageKeyName(keyName, "boolean"))
    private val legacyKey = booleanPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): Boolean = getter(model)
    override fun set(model: T, value: Boolean): T = setter(model, value)
    override fun read(prefs: Preferences): Boolean? = prefs.safeGet(key) ?: prefs.safeGet(legacyKey)
    override fun write(prefs: MutablePreferences, value: Boolean) {
        prefs[key] = value
        prefs[legacyKey] = value
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override fun toUiToggleValue(model: T): Boolean = getter(model)
    override fun fromUiToggleValue(value: Boolean): Boolean = value
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.TOGGLE)
    override fun encodeValue(value: Boolean): String = FieldEncoding.encode(FieldEncoding.BOOLEAN, value.toString())
    override fun decodeValue(encoded: String): Boolean =
        FieldEncoding.parseBoolean(FieldEncoding.tagged(encoded, FieldEncoding.BOOLEAN).payload)
}

class IntField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Int,
    private val setter: (T, Int) -> T,
) : SettingField<T, Int> {
    private val key = intPreferencesKey(storageKeyName(keyName, "int"))
    private val legacyKey = intPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): Int = getter(model)
    override fun set(model: T, value: Int): T = setter(model, value)
    override fun read(prefs: Preferences): Int? = prefs.safeGet(key) ?: prefs.safeGet(legacyKey)
    override fun write(prefs: MutablePreferences, value: Int) {
        prefs[key] = value
        prefs[legacyKey] = value
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }

    override fun toUiSliderValue(model: T): Float = getter(model).toFloat()
    override fun fromUiSliderValue(value: Float): Int? {
        if (!sliderInputAllowed(meta, value)) return null
        val rounded = value.roundToInt()
        return rounded.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
    }
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model)
        return value.takeIf { dropdownIndexAllowed(meta, it) }
    }
    override fun fromUiDropdownIndex(index: Int): Int? =
        index.takeIf { dropdownIndexAllowed(meta, it) }
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.SLIDER, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: Int): String = FieldEncoding.encode(FieldEncoding.INT, value.toString())
    override fun decodeValue(encoded: String): Int =
        FieldEncoding.parseInt(FieldEncoding.tagged(encoded, FieldEncoding.INT).payload)
}

class LongField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Long,
    private val setter: (T, Long) -> T,
) : SettingField<T, Long> {
    private val key = longPreferencesKey(storageKeyName(keyName, "long"))
    private val legacyKey = longPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): Long = getter(model)
    override fun set(model: T, value: Long): T = setter(model, value)
    override fun read(prefs: Preferences): Long? = prefs.safeGet(key) ?: prefs.safeGet(legacyKey)
    override fun write(prefs: MutablePreferences, value: Long) {
        prefs[key] = value
        prefs[legacyKey] = value
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }

    override fun toUiSliderValue(model: T): Float = getter(model).toFloat()
    override fun fromUiSliderValue(value: Float): Long? {
        if (!sliderInputAllowed(meta, value)) return null
        val rounded = value.roundToLong()
        return rounded.takeIf { it in Long.MIN_VALUE..Long.MAX_VALUE }
    }
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model)
        if (value !in 0L..Int.MAX_VALUE.toLong()) return null
        val index = value.toInt()
        return index.takeIf { dropdownIndexAllowed(meta, it) }
    }
    override fun fromUiDropdownIndex(index: Int): Long? =
        index.takeIf { dropdownIndexAllowed(meta, it) }?.toLong()
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.SLIDER, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: Long): String = FieldEncoding.encode(FieldEncoding.LONG, value.toString())
    override fun decodeValue(encoded: String): Long =
        FieldEncoding.parseLong(FieldEncoding.tagged(encoded, FieldEncoding.LONG).payload)
}

class FloatField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Float,
    private val setter: (T, Float) -> T,
) : SettingField<T, Float> {
    private val key = floatPreferencesKey(storageKeyName(keyName, "float"))
    private val legacyKey = floatPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): Float = getter(model)
    override fun set(model: T, value: Float): T = setter(model, value)
    override fun read(prefs: Preferences): Float? = prefs.safeGet(key) ?: prefs.safeGet(legacyKey)
    override fun write(prefs: MutablePreferences, value: Float) {
        prefs[key] = value
        prefs[legacyKey] = value
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }

    override fun toUiSliderValue(model: T): Float? = getter(model).takeIf { it.isFinite() }
    override fun fromUiSliderValue(value: Float): Float? =
        value.takeIf { sliderInputAllowed(meta, it) }
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model)
        if (!value.isFinite() || value < 0f || value > Int.MAX_VALUE.toFloat()) return null
        val index = value.toInt()
        return index.takeIf { it.toFloat() == value && dropdownIndexAllowed(meta, it) }
    }
    override fun fromUiDropdownIndex(index: Int): Float? =
        index.takeIf { dropdownIndexAllowed(meta, it) }?.toFloat()
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.SLIDER, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: Float): String = FieldEncoding.encode(FieldEncoding.FLOAT, value.toString())
    override fun decodeValue(encoded: String): Float =
        FieldEncoding.parseFloat(FieldEncoding.tagged(encoded, FieldEncoding.FLOAT).payload)
}

class DoubleField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Double,
    private val setter: (T, Double) -> T,
) : SettingField<T, Double> {
    private val key = doublePreferencesKey(storageKeyName(keyName, "double"))
    private val legacyKey = doublePreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): Double = getter(model)
    override fun set(model: T, value: Double): T = setter(model, value)
    override fun read(prefs: Preferences): Double? = prefs.safeGet(key) ?: prefs.safeGet(legacyKey)
    override fun write(prefs: MutablePreferences, value: Double) {
        prefs[key] = value
        prefs[legacyKey] = value
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }

    override fun toUiSliderValue(model: T): Float? = getter(model).toFloat().takeIf { it.isFinite() }
    override fun fromUiSliderValue(value: Float): Double? =
        value.takeIf { sliderInputAllowed(meta, it) }?.let(::uiFloatToDouble)
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model)
        if (!value.isFinite() || value < 0.0 || value > Int.MAX_VALUE.toDouble()) return null
        val index = value.toInt()
        return index.takeIf { it.toDouble() == value && dropdownIndexAllowed(meta, it) }
    }
    override fun fromUiDropdownIndex(index: Int): Double? =
        index.takeIf { dropdownIndexAllowed(meta, it) }?.toDouble()
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.SLIDER, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: Double): String = FieldEncoding.encode(FieldEncoding.DOUBLE, value.toString())
    override fun decodeValue(encoded: String): Double =
        FieldEncoding.parseDouble(FieldEncoding.tagged(encoded, FieldEncoding.DOUBLE).payload)
}

class StringField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> String,
    private val setter: (T, String) -> T,
) : SettingField<T, String> {
    private val key = stringPreferencesKey(storageKeyName(keyName, "string"))
    private val legacyKey = stringPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, legacyKey)

    override fun get(model: T): String = getter(model)
    override fun set(model: T, value: String): T = setter(model, value)
    override fun read(prefs: Preferences): String? = prefs.safeGet(key) ?: prefs.safeGet(legacyKey)
    override fun write(prefs: MutablePreferences, value: String) {
        prefs[key] = value
        prefs[legacyKey] = value
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }

    override fun toUiDropdownIndex(model: T): Int? {
        val options = dropdownOptions(meta)
        return options.indexOf(getter(model)).takeIf { it >= 0 }
    }
    override fun fromUiDropdownIndex(index: Int): String? =
        dropdownOptions(meta).getOrNull(index)
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.TEXT_INPUT, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: String): String = FieldEncoding.encode(FieldEncoding.STRING, value)
    override fun decodeValue(encoded: String): String =
        FieldEncoding.tagged(encoded, FieldEncoding.STRING).payload
}
