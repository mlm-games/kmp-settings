package io.github.mlmgames.settings.core.fields

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingFieldCapability
import io.github.mlmgames.settings.core.SettingMeta
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

    internal val key = booleanPreferencesKey(storageKeyName(keyName, "nullable_boolean"))
    private val nullKey = booleanPreferencesKey(nullStorageKeyName(keyName, "nullable_boolean"))
    private val legacyKey = stringPreferencesKey("${keyName}_nullable")
    private val legacyDirectKey = booleanPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, nullKey, legacyKey, legacyDirectKey)

    override fun get(model: T): Boolean? = getter(model)
    override fun set(model: T, value: Boolean?): T = setter(model, value)
    override fun read(prefs: Preferences): Boolean? {
        if (nullKey in prefs) return null
        if (key in prefs) return prefs.safeGet(key)
        val legacy = prefs.safeGet(legacyKey)
        if (legacy != null) {
            return when (legacy) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        return prefs.safeGet(legacyDirectKey)
    }
    override fun write(prefs: MutablePreferences, value: Boolean?) {
        prefs.removeAny(physicalKeys)
        if (value == null) {
            prefs[nullKey] = true
            prefs.remove(legacyKey)
            prefs.remove(legacyDirectKey)
        } else {
            prefs[key] = value
            prefs[legacyKey] = value.toString()
            prefs[legacyDirectKey] = value
        }
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isExplicitNull(prefs: Preferences): Boolean =
        nullKey in prefs || (key !in prefs && prefs.safeGet(legacyKey) == NULL_MARKER)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override val supportsExplicitNull: Boolean
        get() = true
    override fun toUiToggleValue(model: T): Boolean? = getter(model)
    override fun fromUiToggleValue(value: Boolean): Boolean? = value
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.TOGGLE)
    override fun encodeValue(value: Boolean?): String = when (value) {
        true -> FieldEncoding.encode(FieldEncoding.BOOLEAN, "true")
        false -> FieldEncoding.encode(FieldEncoding.BOOLEAN, "false")
        null -> FieldEncoding.encode(FieldEncoding.NULL, "")
    }
    override fun decodeValue(encoded: String): Boolean? {
        val tagged = FieldEncoding.tagged(
            encoded,
            FieldEncoding.NULL,
            FieldEncoding.NULLABLE_BOOLEAN,
            FieldEncoding.BOOLEAN,
        )
        return when (tagged.tag) {
            FieldEncoding.NULL -> {
                if (tagged.payload.isEmpty()) null
                else throw IllegalArgumentException("Invalid null payload: $encoded")
            }
            FieldEncoding.NULLABLE_BOOLEAN -> FieldEncoding.parseBoolean(tagged.payload)
            FieldEncoding.BOOLEAN -> when (tagged.payload) {
                "", NULL_MARKER -> null
                else -> FieldEncoding.parseBoolean(tagged.payload)
            }
            else -> throw IllegalArgumentException("Invalid nullable Boolean value: $encoded")
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

    private val key = androidx.datastore.preferences.core.intPreferencesKey(
        storageKeyName(keyName, "nullable_int"),
    )
    private val nullKey = booleanPreferencesKey(nullStorageKeyName(keyName, "nullable_int"))
    private val legacyKey = androidx.datastore.preferences.core.longPreferencesKey("${keyName}_nullable")
    private val legacyDirectKey = androidx.datastore.preferences.core.intPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, nullKey, legacyKey, legacyDirectKey)

    override fun get(model: T): Int? = getter(model)
    override fun set(model: T, value: Int?): T = setter(model, value)
    override fun read(prefs: Preferences): Int? {
        if (nullKey in prefs) return null
        if (key in prefs) return prefs.safeGet(key)
        val legacy = prefs.safeGet(legacyKey)
        if (legacy != null) {
            if (legacy == NULL_SENTINEL) return null
            return if (legacy in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) legacy.toInt() else null
        }
        return prefs.safeGet(legacyDirectKey)
    }
    override fun write(prefs: MutablePreferences, value: Int?) {
        prefs.removeAny(physicalKeys)
        if (value == null) {
            prefs[nullKey] = true
            prefs[legacyKey] = NULL_SENTINEL
            prefs.remove(legacyDirectKey)
        } else {
            prefs[key] = value
            prefs[legacyKey] = value.toLong()
            prefs[legacyDirectKey] = value
        }
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isExplicitNull(prefs: Preferences): Boolean =
        nullKey in prefs || (key !in prefs && prefs.safeGet(legacyKey) == NULL_SENTINEL)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override val supportsExplicitNull: Boolean
        get() = true
    override fun toUiSliderValue(model: T): Float? = getter(model)?.toFloat()
    override fun fromUiSliderValue(value: Float): Int? {
        if (!sliderInputAllowed(meta, value)) return null
        return value.roundToInt().takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
    }
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model) ?: return null
        return value.takeIf { dropdownIndexAllowed(meta, it) }
    }
    override fun fromUiDropdownIndex(index: Int): Int? =
        if (index == -1) null else index.takeIf { dropdownIndexAllowed(meta, it) }
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.SLIDER, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: Int?): String = when (value) {
        null -> FieldEncoding.encode(FieldEncoding.NULL, "")
        else -> FieldEncoding.encode(FieldEncoding.INT, value.toString())
    }
    override fun decodeValue(encoded: String): Int? {
        val tagged = FieldEncoding.tagged(
            encoded,
            FieldEncoding.NULL,
            FieldEncoding.NULLABLE_INT,
            FieldEncoding.INT,
        )
        return when (tagged.tag) {
            FieldEncoding.NULL -> {
                if (tagged.payload.isEmpty()) null
                else throw IllegalArgumentException("Invalid null payload: $encoded")
            }
            FieldEncoding.NULLABLE_INT -> {
                val number = tagged.payload.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid nullable Int value: $encoded")
                if (number !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    throw IllegalArgumentException("Int value out of range: $encoded")
                }
                number.toInt()
            }
            FieldEncoding.INT -> {
                if (tagged.payload.isEmpty()) return null
                val number = tagged.payload.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid nullable Int value: $encoded")
                if (number == NULL_SENTINEL) return null
                if (number !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    throw IllegalArgumentException("Int value out of range: $encoded")
                }
                number.toInt()
            }
            else -> throw IllegalArgumentException("Invalid nullable Int value: $encoded")
        }
    }
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

    private val key = androidx.datastore.preferences.core.longPreferencesKey(
        storageKeyName(keyName, "nullable_long"),
    )
    private val nullKey = booleanPreferencesKey(nullStorageKeyName(keyName, "nullable_long"))
    private val legacyKey = stringPreferencesKey("${keyName}_nullable_long")
    private val legacyDirectKey = androidx.datastore.preferences.core.longPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, nullKey, legacyKey, legacyDirectKey)

    override fun get(model: T): Long? = getter(model)
    override fun set(model: T, value: Long?): T = setter(model, value)
    override fun read(prefs: Preferences): Long? {
        if (nullKey in prefs) return null
        if (key in prefs) return prefs.safeGet(key)
        val legacy = prefs.safeGet(legacyKey)
        if (legacy != null) {
            if (legacy == NULL_MARKER) return null
            return legacy.toLongOrNull()
        }
        return prefs.safeGet(legacyDirectKey)
    }
    override fun write(prefs: MutablePreferences, value: Long?) {
        prefs.removeAny(physicalKeys)
        if (value == null) {
            prefs[nullKey] = true
            prefs.remove(legacyKey)
            prefs.remove(legacyDirectKey)
        } else {
            prefs[key] = value
            prefs[legacyKey] = value.toString()
            prefs[legacyDirectKey] = value
        }
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isExplicitNull(prefs: Preferences): Boolean =
        nullKey in prefs || (key !in prefs && prefs.safeGet(legacyKey) == NULL_MARKER)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override val supportsExplicitNull: Boolean
        get() = true
    override fun toUiSliderValue(model: T): Float? = getter(model)?.toFloat()
    override fun fromUiSliderValue(value: Float): Long? {
        if (!sliderInputAllowed(meta, value)) return null
        val rounded = value.roundToLong()
        return rounded.takeIf { it in Long.MIN_VALUE..Long.MAX_VALUE }
    }
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model) ?: return null
        if (value !in 0L..Int.MAX_VALUE.toLong()) return null
        val index = value.toInt()
        return index.takeIf { dropdownIndexAllowed(meta, it) }
    }
    override fun fromUiDropdownIndex(index: Int): Long? =
        if (index == -1) null else index.takeIf { dropdownIndexAllowed(meta, it) }?.toLong()
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.SLIDER, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: Long?): String = when (value) {
        null -> FieldEncoding.encode(FieldEncoding.NULL, "")
        else -> FieldEncoding.encode(FieldEncoding.LONG, value.toString())
    }
    override fun decodeValue(encoded: String): Long? {
        val tagged = FieldEncoding.tagged(
            encoded,
            FieldEncoding.NULL,
            FieldEncoding.NULLABLE_LONG,
            FieldEncoding.LONG,
        )
        return when (tagged.tag) {
            FieldEncoding.NULL -> {
                if (tagged.payload.isEmpty()) null
                else throw IllegalArgumentException("Invalid null payload: $encoded")
            }
            FieldEncoding.NULLABLE_LONG -> FieldEncoding.parseLong(tagged.payload)
            FieldEncoding.LONG -> when (tagged.payload) {
                "", NULL_MARKER -> null
                else -> FieldEncoding.parseLong(tagged.payload)
            }
            else -> throw IllegalArgumentException("Invalid nullable Long value: $encoded")
        }
    }
}

class NullableFloatField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Float?,
    private val setter: (T, Float?) -> T,
) : SettingField<T, Float?> {
    private val key = androidx.datastore.preferences.core.floatPreferencesKey(
        storageKeyName(keyName, "nullable_float"),
    )
    private val nullKey = booleanPreferencesKey(nullStorageKeyName(keyName, "nullable_float"))
    private val legacyKey = androidx.datastore.preferences.core.floatPreferencesKey("${keyName}_nullable")
    private val legacyDirectKey = androidx.datastore.preferences.core.floatPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, nullKey, legacyKey, legacyDirectKey)

    override fun get(model: T): Float? = getter(model)
    override fun set(model: T, value: Float?): T = setter(model, value)
    override fun read(prefs: Preferences): Float? {
        if (nullKey in prefs) return null
        if (key in prefs) return prefs.safeGet(key)
        val legacy = prefs.safeGet(legacyKey)
        if (legacy != null) return if (legacy.isNaN()) null else legacy
        return prefs.safeGet(legacyDirectKey)
    }
    override fun write(prefs: MutablePreferences, value: Float?) {
        prefs.removeAny(physicalKeys)
        if (value == null) {
            prefs[nullKey] = true
            prefs[legacyKey] = Float.NaN
            prefs.remove(legacyDirectKey)
        } else {
            prefs[key] = value
            prefs[legacyKey] = value
            prefs[legacyDirectKey] = value
        }
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isExplicitNull(prefs: Preferences): Boolean =
        nullKey in prefs || (key !in prefs && prefs.safeGet(legacyKey)?.isNaN() == true)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override val supportsExplicitNull: Boolean
        get() = true
    override fun toUiSliderValue(model: T): Float? = getter(model)?.takeIf { it.isFinite() }
    override fun fromUiSliderValue(value: Float): Float? =
        value.takeIf { sliderInputAllowed(meta, it) }
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model) ?: return null
        if (!value.isFinite() || value < 0f || value > Int.MAX_VALUE.toFloat()) return null
        val index = value.toInt()
        return index.takeIf { it.toFloat() == value && dropdownIndexAllowed(meta, it) }
    }
    override fun fromUiDropdownIndex(index: Int): Float? =
        if (index == -1) null else index.takeIf { dropdownIndexAllowed(meta, it) }?.toFloat()
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.SLIDER, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: Float?): String = when (value) {
        null -> FieldEncoding.encode(FieldEncoding.NULL, "")
        else -> FieldEncoding.encode(FieldEncoding.FLOAT, value.toString())
    }
    override fun decodeValue(encoded: String): Float? {
        val tagged = FieldEncoding.tagged(
            encoded,
            FieldEncoding.NULL,
            FieldEncoding.NULLABLE_FLOAT,
            FieldEncoding.FLOAT,
        )
        return when (tagged.tag) {
            FieldEncoding.NULL -> {
                if (tagged.payload.isEmpty()) null
                else throw IllegalArgumentException("Invalid null payload: $encoded")
            }
            FieldEncoding.NULLABLE_FLOAT -> FieldEncoding.parseFloat(tagged.payload)
            FieldEncoding.FLOAT -> if (tagged.payload.isEmpty()) null
            else FieldEncoding.parseFloat(tagged.payload)
            else -> throw IllegalArgumentException("Invalid nullable Float value: $encoded")
        }
    }
}

class NullableDoubleField<T>(
    override val name: String,
    override val keyName: String,
    override val meta: SettingMeta?,
    private val getter: (T) -> Double?,
    private val setter: (T, Double?) -> T,
) : SettingField<T, Double?> {
    private val key = androidx.datastore.preferences.core.doublePreferencesKey(
        storageKeyName(keyName, "nullable_double"),
    )
    private val nullKey = booleanPreferencesKey(nullStorageKeyName(keyName, "nullable_double"))
    private val legacyKey = androidx.datastore.preferences.core.doublePreferencesKey("${keyName}_nullable")
    private val legacyDirectKey = androidx.datastore.preferences.core.doublePreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, nullKey, legacyKey, legacyDirectKey)

    override fun get(model: T): Double? = getter(model)
    override fun set(model: T, value: Double?): T = setter(model, value)
    override fun read(prefs: Preferences): Double? {
        if (nullKey in prefs) return null
        if (key in prefs) return prefs.safeGet(key)
        val legacy = prefs.safeGet(legacyKey)
        if (legacy != null) return if (legacy.isNaN()) null else legacy
        return prefs.safeGet(legacyDirectKey)
    }
    override fun write(prefs: MutablePreferences, value: Double?) {
        prefs.removeAny(physicalKeys)
        if (value == null) {
            prefs[nullKey] = true
            prefs[legacyKey] = Double.NaN
            prefs.remove(legacyDirectKey)
        } else {
            prefs[key] = value
            prefs[legacyKey] = value
            prefs[legacyDirectKey] = value
        }
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isExplicitNull(prefs: Preferences): Boolean =
        nullKey in prefs || (key !in prefs && prefs.safeGet(legacyKey)?.isNaN() == true)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override val supportsExplicitNull: Boolean
        get() = true
    override fun toUiSliderValue(model: T): Float? = getter(model)?.takeIf { it.isFinite() }?.toFloat()
    override fun fromUiSliderValue(value: Float): Double? =
        value.takeIf { sliderInputAllowed(meta, it) }?.let(::uiFloatToDouble)
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model) ?: return null
        if (!value.isFinite() || value < 0.0 || value > Int.MAX_VALUE.toDouble()) return null
        val index = value.toInt()
        return index.takeIf { it.toDouble() == value && dropdownIndexAllowed(meta, it) }
    }
    override fun fromUiDropdownIndex(index: Int): Double? =
        if (index == -1) null else index.takeIf { dropdownIndexAllowed(meta, it) }?.toDouble()
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.SLIDER, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: Double?): String = when (value) {
        null -> FieldEncoding.encode(FieldEncoding.NULL, "")
        else -> FieldEncoding.encode(FieldEncoding.DOUBLE, value.toString())
    }
    override fun decodeValue(encoded: String): Double? {
        val tagged = FieldEncoding.tagged(
            encoded,
            FieldEncoding.NULL,
            FieldEncoding.NULLABLE_DOUBLE,
            FieldEncoding.DOUBLE,
        )
        return when (tagged.tag) {
            FieldEncoding.NULL -> {
                if (tagged.payload.isEmpty()) null
                else throw IllegalArgumentException("Invalid null payload: $encoded")
            }
            FieldEncoding.NULLABLE_DOUBLE -> FieldEncoding.parseDouble(tagged.payload)
            FieldEncoding.DOUBLE -> if (tagged.payload.isEmpty()) null
            else FieldEncoding.parseDouble(tagged.payload)
            else -> throw IllegalArgumentException("Invalid nullable Double value: $encoded")
        }
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
        internal const val NULL_SENTINEL = "\u0000__NULL__\u0000"
        internal const val CURRENT_NULL_SENTINEL = "__NULL__"
    }

    private val key = stringPreferencesKey(storageKeyName(keyName, "nullable_string"))
    private val nullKey = booleanPreferencesKey(nullStorageKeyName(keyName, "nullable_string"))
    private val legacyKey = stringPreferencesKey("${keyName}_nullable")
    private val legacyDirectKey = stringPreferencesKey(keyName)
    override val physicalKeys: List<Preferences.Key<*>> = listOf(key, nullKey, legacyKey, legacyDirectKey)

    override fun get(model: T): String? = getter(model)
    override fun set(model: T, value: String?): T = setter(model, value)
    override fun read(prefs: Preferences): String? {
        if (nullKey in prefs) return null
        if (key in prefs) return prefs.safeGet(key)
        val legacy = prefs.safeGet(legacyKey)
        if (legacy != null) {
            return if (legacy == NULL_SENTINEL) null else legacy
        }
        return prefs.safeGet(legacyDirectKey)
    }
    override fun write(prefs: MutablePreferences, value: String?) {
        prefs.removeAny(physicalKeys)
        if (value == null) {
            prefs[nullKey] = true
            prefs[legacyKey] = NULL_SENTINEL
            prefs.remove(legacyDirectKey)
        } else {
            prefs[key] = value
            prefs[legacyKey] = value
            prefs[legacyDirectKey] = value
        }
    }
    override fun hasValue(prefs: Preferences): Boolean = prefs.containsAny(physicalKeys)
    override fun isExplicitNull(prefs: Preferences): Boolean =
        nullKey in prefs || (key !in prefs && prefs.safeGet(legacyKey)?.let {
            it == NULL_SENTINEL
        } == true)
    override fun clear(prefs: MutablePreferences) { prefs.removeAny(physicalKeys) }
    override val supportsExplicitNull: Boolean
        get() = true
    override val isNullableString: Boolean
        get() = true
    override fun toUiDropdownIndex(model: T): Int? {
        val value = getter(model) ?: return null
        val options = dropdownOptions(meta)
        return options.indexOf(value).takeIf { it >= 0 }
    }
    override fun fromUiDropdownIndex(index: Int): String? =
        if (index == -1) null else dropdownOptions(meta).getOrNull(index)
    override fun getDropdownOptions(): List<String>? =
        dropdownOptions(meta).takeIf { it.isNotEmpty() }
    override val capabilities: Set<SettingFieldCapability>
        get() = setOf(SettingFieldCapability.TEXT_INPUT, SettingFieldCapability.DROPDOWN)
    override fun encodeValue(value: String?): String = when {
        value == null -> FieldEncoding.encode(FieldEncoding.NULL, "")
        value == NULL_SENTINEL || value == CURRENT_NULL_SENTINEL ->
            FieldEncoding.encode(FieldEncoding.NULLABLE_STRING, value)
        else -> FieldEncoding.encode(FieldEncoding.STRING, value)
    }
    override fun decodeValue(encoded: String): String? {
        val tagged = FieldEncoding.tagged(
            encoded,
            FieldEncoding.NULL,
            FieldEncoding.NULLABLE_STRING,
            FieldEncoding.STRING,
        )
        return when (tagged.tag) {
            FieldEncoding.NULL -> {
                if (tagged.payload.isEmpty()) null
                else throw IllegalArgumentException("Invalid null payload: $encoded")
            }
            FieldEncoding.NULLABLE_STRING -> tagged.payload
            FieldEncoding.STRING -> when (tagged.payload) {
                NULL_SENTINEL -> null
                else -> tagged.payload
            }
            else -> throw IllegalArgumentException("Invalid nullable String value: $encoded")
        }
    }
}
