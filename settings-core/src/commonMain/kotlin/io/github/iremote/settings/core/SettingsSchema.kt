package io.github.iremote.settings.core

interface SettingsSchema<T> {
  val default: T
  val fields: List<SettingField<T, *>>

  fun fieldByName(name: String): SettingField<T, *>? = fields.firstOrNull { it.name == name }

  fun fieldsWithMeta(): List<SettingField<T, *>> = fields.filter { it.meta != null }

  fun groupedByCategory(): Map<SettingCategory, List<SettingField<T, *>>> =
    fieldsWithMeta().groupBy { it.meta!!.category }

  /**
   * Basic dependsOn support:
   * - if dependsOn is blank => enabled
   * - else: find that field and if it's Boolean and true => enabled, otherwise disabled
   */
  fun isEnabled(model: T, field: SettingField<T, *>): Boolean {
    val meta = field.meta ?: return true
    val dep = meta.dependsOn
    if (dep.isBlank()) return true
    val depField = fieldByName(dep) ?: return true
    val v = (depField as? SettingField<T, Boolean>)?.get(model) ?: return true
    return v
  }
}