package io.github.mlmgames.settings.core.types

import kotlin.reflect.KClass

/**
 * Marker interface for all setting types.
 */
interface SettingTypeMarker

// Built-in Types

/** Boolean toggle switch */
object Toggle : SettingTypeMarker

/** Dropdown/selection from options list */
object Dropdown : SettingTypeMarker

/** Numeric slider with min/max/step */
object Slider : SettingTypeMarker

/** Action button (triggers callback) */
object Button : SettingTypeMarker

/** Text input field */
object TextInput : SettingTypeMarker

object SettingTypes {
    private val builtInTypes: Set<KClass<*>> = setOf(
        Toggle::class,
        Dropdown::class,
        Slider::class,
        Button::class,
        TextInput::class,
    )

    fun isBuiltIn(type: KClass<*>): Boolean = type in builtInTypes
}