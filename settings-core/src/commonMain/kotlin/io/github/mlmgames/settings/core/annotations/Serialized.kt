package io.github.mlmgames.settings.core.annotations

import kotlin.reflect.KClass

/**
 * Marks a property for JSON serialization.
 *
 * The property type must be annotated with @Serializable from kotlinx.serialization.
 *
 * Usage:
 * ```
 * @Setting(...)
 * @Serialized
 * val swipeAction: SwipeActionConfig = SwipeActionConfig()
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Serialized

/**
 * Specify a custom serializer class.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class SerializedWith(
    val serializer: KClass<out SettingSerializer<*>>
)

/**
 * Interface for custom serializers.
 */
interface SettingSerializer<T> {
    fun serialize(value: T): String
    fun deserialize(json: String): T
}