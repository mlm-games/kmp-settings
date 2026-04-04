package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.*
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta

class BooleanField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Boolean,
    private val setter: (T, Boolean) -> T,
) : SettingField<T, Boolean> {
    private val key = booleanPreferencesKey(keyName)
    override fun get(model: T) = getter(model)
    override fun set(model: T, value: Boolean) = setter(model, value)
    override fun read(prefs: Preferences) = prefs[key]
    override fun write(prefs: MutablePreferences, value: Boolean) { prefs[key] = value }
    override fun encodeValue(value: Boolean): String = "b:$value"
    override fun decodeValue(encoded: String): Boolean = encoded.substringAfter(':').toBooleanStrict()
}

class IntField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Int,
    private val setter: (T, Int) -> T,
) : SettingField<T, Int> {
    private val key = intPreferencesKey(keyName)
    override fun get(model: T) = getter(model)
    override fun set(model: T, value: Int) = setter(model, value)
    override fun read(prefs: Preferences) = prefs[key]
    override fun write(prefs: MutablePreferences, value: Int) { prefs[key] = value }

    override fun toUiSliderValue(model: T): Float = getter(model).toFloat()
    override fun fromUiSliderValue(value: Float): Int = value.toInt()
    override fun encodeValue(value: Int): String = "i:$value"
    override fun decodeValue(encoded: String): Int = encoded.substringAfter(':').toInt()
}

class LongField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Long,
    private val setter: (T, Long) -> T,
) : SettingField<T, Long> {
    private val key = longPreferencesKey(keyName)
    override fun get(model: T) = getter(model)
    override fun set(model: T, value: Long) = setter(model, value)
    override fun read(prefs: Preferences) = prefs[key]
    override fun write(prefs: MutablePreferences, value: Long) { prefs[key] = value }
    override fun encodeValue(value: Long): String = "l:$value"
    override fun decodeValue(encoded: String): Long = encoded.substringAfter(':').toLong()
}

class FloatField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Float,
    private val setter: (T, Float) -> T,
) : SettingField<T, Float> {
    private val key = floatPreferencesKey(keyName)
    override fun get(model: T) = getter(model)
    override fun set(model: T, value: Float) = setter(model, value)
    override fun read(prefs: Preferences) = prefs[key]
    override fun write(prefs: MutablePreferences, value: Float) { prefs[key] = value }

    override fun toUiSliderValue(model: T): Float = getter(model)
    override fun fromUiSliderValue(value: Float): Float = value
    override fun encodeValue(value: Float): String = "f:$value"
    override fun decodeValue(encoded: String): Float = encoded.substringAfter(':').toFloat()
}

class DoubleField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Double,
    private val setter: (T, Double) -> T,
) : SettingField<T, Double> {
    private val key = doublePreferencesKey(keyName)
    override fun get(model: T) = getter(model)
    override fun set(model: T, value: Double) = setter(model, value)
    override fun read(prefs: Preferences) = prefs[key]
    override fun write(prefs: MutablePreferences, value: Double) { prefs[key] = value }
    override fun encodeValue(value: Double): String = "d:$value"
    override fun decodeValue(encoded: String): Double = encoded.substringAfter(':').toDouble()
}

class StringField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> String,
    private val setter: (T, String) -> T,
) : SettingField<T, String> {
    private val key = stringPreferencesKey(keyName)
    override fun get(model: T) = getter(model)
    override fun set(model: T, value: String) = setter(model, value)
    override fun read(prefs: Preferences) = prefs[key]
    override fun write(prefs: MutablePreferences, value: String) { prefs[key] = value }
    override fun encodeValue(value: String): String = "s:$value"
    override fun decodeValue(encoded: String): String = encoded.substringAfter(':')
}