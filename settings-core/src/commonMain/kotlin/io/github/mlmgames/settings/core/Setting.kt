package io.github.mlmgames.settings.core

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Setting(
  val title: String,
  val description: String = "",
  val category: SettingCategory,
  val type: SettingType,

  /**
   * Optional stable key name. If blank, defaults to snake_case(propertyName).
   * Prefer setting this for public libraries to avoid breaking persistence when renaming properties.
   */
  val key: String = "",

  /**
   * Name of another property that gates this setting. Typically refers to a Boolean setting.
   */
  val dependsOn: String = "",

  val min: Float = 0f,
  val max: Float = 100f,
  val step: Float = 1f,
  val options: Array<String> = [],
)

enum class SettingCategory { GENERAL, APPEARANCE, ARCHIVES, SYSTEM }
enum class SettingType { TOGGLE, DROPDOWN, SLIDER, BUTTON }