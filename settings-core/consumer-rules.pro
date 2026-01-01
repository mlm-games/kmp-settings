-keep @interface io.github.mlmgames.settings.core.annotations.CategoryDefinition
-keep,allowobfuscation @io.github.mlmgames.settings.core.annotations.CategoryDefinition class * {
    <fields>;
}
-keepclassmembers @interface io.github.mlmgames.settings.core.annotations.CategoryDefinition {
    *;
}

-keep class * implements io.github.mlmgames.settings.core.annotations.SettingCategoryMarker {
    *;
}

-keep class * implements io.github.mlmgames.settings.core.SettingsSchema {
    *;
}
-keep class * implements io.github.mlmgames.settings.core.SettingField {
    *;
}

-keep class io.github.mlmgames.settings.core.types.** { *; }
-keep class io.github.mlmgames.settings.core.SettingMeta { *; }