package io.github.iremote.settings.core

data class SettingMeta(
  val title: String,
  val description: String,
  val category: SettingCategory,
  val type: SettingType,
  val key: String,
  val dependsOn: String,
  val min: Float,
  val max: Float,
  val step: Float,
  val options: List<String>,
)