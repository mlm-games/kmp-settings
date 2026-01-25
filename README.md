# KMP Settings

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mlm-games/kmp-settings-core.svg)](https://central.sonatype.com/search?q=io.github.mlm-games.kmp-settings)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A **Kotlin Multiplatform** settings library that generates type-safe, persistent settings from annotations. Define your settings once with a data class, and get automatic DataStore persistence, Compose UI, migrations, backup/restore, and more.

## Features

- 🎯 **Annotation-based** - Define settings with `@Setting` and `@Persisted` annotations
- 🔧 **KSP Code Generation** - Auto-generates type-safe schema classes
- 💾 **DataStore Persistence** - Built on AndroidX DataStore for reliable storage
- 🎨 **Compose UI** - Auto-generated settings screens with Material 3
- 🔄 **Migrations** - Schema versioning with key renames and deletions
- 📦 **Backup/Restore** - Export and import settings as JSON
- ↩️ **Undo/Redo** - Track and revert setting changes
- 🔒 **Settings Lock** - PIN-protect your settings
- ✅ **Validation** - Range, length, pattern, and custom validators
- 🌍 **Platform-specific** - Show/hide settings per platform

## Supported Platforms

| Platform | Status |
|----------|--------|
| Android | ✅ |
| iOS (arm64, simulatorArm64) | ✅ |
| JVM (Desktop) | ✅ |
| Linux x64 | ✅ |

## Installation

Add the dependencies to your `build.gradle.kts`:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.2.21-2.0.4"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mlm-games:kmp-settings-core:1.0.0")
            // Optional: Compose UI components
            implementation("io.github.mlm-games:kmp-settings-ui-compose:1.0.0")
        }
    }
}

dependencies {
    // KSP processor (add for each target)
    add("kspCommonMainMetadata", "io.github.mlm-games:kmp-settings-ksp:1.0.0")
    // Or for specific targets:
    // add("kspAndroid", "io.github.mlm-games:kmp-settings-ksp:1.0.0")
    // add("kspJvm", "io.github.mlm-games:kmp-settings-ksp:1.0.0")
}
```

## Quick Start

### 1. Define Categories

```kotlin
import io.github.mlmgames.settings.core.annotations.CategoryDefinition

@CategoryDefinition(order = 0)
object General

@CategoryDefinition(order = 1)
object Appearance

@CategoryDefinition(order = 2)
object Advanced
```

### 2. Define Settings

```kotlin
import io.github.mlmgames.settings.core.annotations.*
import io.github.mlmgames.settings.core.types.*

data class AppSettings(
    @Setting(
        title = "Dark Mode",
        description = "Enable dark theme",
        category = Appearance::class,
        type = Toggle::class
    )
    val darkMode: Boolean = false,

    @Setting(
        title = "Font Size",
        category = Appearance::class,
        type = Slider::class,
        min = 12f,
        max = 24f,
        step = 1f
    )
    val fontSize: Float = 16f,

    @Setting(
        title = "Language",
        category = General::class,
        type = Dropdown::class,
        options = ["English", "Spanish", "French", "German"]
    )
    val language: Int = 0,

    @Setting(
        title = "Username",
        category = General::class,
        type = TextInput::class
    )
    val username: String = "",

    // Internal state (not shown in UI)
    @Persisted
    val lastSyncTime: Long = 0L,

    @Persisted(key = "hidden_items")
    val hiddenItems: Set<String> = emptySet()
)
```

### 3. Create DataStore and Repository

```kotlin
// Android
val dataStore = createSettingsDataStore(context, "app_settings")

// iOS
val dataStore = createSettingsDataStore("app_settings")

// JVM/Desktop
val dataStore = createSettingsDataStore("app_settings")

// Linux
val dataStore = createSettingsDataStore("app_settings")
```

```kotlin
// Create repository with generated schema
val repository = SettingsRepository(dataStore, AppSettingsSchema)
```

### 4. Use Settings

```kotlin
// Observe as Flow
repository.flow.collect { settings ->
    println("Dark mode: ${settings.darkMode}")
}

// Update settings
repository.update { it.copy(darkMode = true) }

// Update single field
repository.set("darkMode", true)

// Get current value
val currentFontSize: Float? = repository.get("fontSize")
```

### 5. Display Settings UI (Compose)

```kotlin
@Composable
fun SettingsScreen() {
    val settings by repository.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    AutoSettingsScreen(
        schema = AppSettingsSchema,
        value = settings,
        onSet = { name, value ->
            scope.launch { repository.set(name, value) }
        }
    )
}
```

## Setting Types

| Type | Property Type | UI Widget |
|------|---------------|-----------|
| `Toggle` | `Boolean` | Switch |
| `Slider` | `Float` or `Int` | Slider with min/max/step |
| `Dropdown` | `Int` (index) | Selection dialog |
| `TextInput` | `String` | Text input dialog |
| `Button` | `Unit` | Action button |

## Supported Data Types

### Primitives
- `Boolean`, `Int`, `Long`, `Float`, `Double`, `String`
- Nullable variants: `Boolean?`, `Int?`, etc.

### Collections
- `Set<String>`
- `List<String>`, `List<Int>`, `List<Long>`
- `Map<String, String>`, `Map<String, Int>`, `Map<String, Long>`
- `Map<String, Float>`, `Map<String, Double>`, `Map<String, Boolean>`
- `Map<Int, String>`, `Map<Int, Int>`, `Map<Long, String>`, `Map<Long, Long>`

### Enums
```kotlin
enum class Theme { LIGHT, DARK, SYSTEM }

@Setting(title = "Theme", category = Appearance::class, type = Dropdown::class)
val theme: Theme = Theme.SYSTEM
```

### Complex Types (with @Serialized)
```kotlin
@Serializable
data class NotificationConfig(
    val enabled: Boolean = true,
    val sound: String = "default",
    val vibrate: Boolean = true
)

@Setting(title = "Notifications", category = General::class, type = Button::class)
@Serialized
val notifications: NotificationConfig = NotificationConfig()
```

## Advanced Features

### Platform-Specific Settings

```kotlin
@Setting(
    title = "Haptic Feedback",
    category = General::class,
    type = Toggle::class,
    platforms = [SettingPlatform.ANDROID, SettingPlatform.IOS]
)
val hapticFeedback: Boolean = true

@Setting(
    title = "System Tray",
    category = General::class,
    type = Toggle::class,
    platforms = [SettingPlatform.DESKTOP] // JVM + Linux
)
val showInSystemTray: Boolean = true
```

### Setting Dependencies

```kotlin
@Setting(
    title = "Enable Notifications",
    category = General::class,
    type = Toggle::class
)
val notificationsEnabled: Boolean = true

@Setting(
    title = "Notification Sound",
    category = General::class,
    type = Dropdown::class,
    options = ["Default", "Chime", "Bell"],
    dependsOn = "notificationsEnabled" // Disabled when notifications are off
)
val notificationSound: Int = 0
```

### Validation

```kotlin
@Setting(title = "Volume", category = General::class, type = Slider::class)
@Range(min = 0.0, max = 100.0, errorMessage = "Volume must be 0-100")
val volume: Float = 50f

@Setting(title = "Username", category = General::class, type = TextInput::class)
@Length(min = 3, max = 20, errorMessage = "Username must be 3-20 characters")
@Pattern(regex = "^[a-zA-Z0-9_]+$", errorMessage = "Only letters, numbers, and underscores")
val username: String = ""

@Setting(title = "Email", category = General::class, type = TextInput::class)
@Required(errorMessage = "Email is required")
val email: String = ""
```

### Confirmation Dialogs

```kotlin
@Setting(
    title = "Delete All Data",
    category = Advanced::class,
    type = Button::class
)
@RequiresConfirmation(
    title = "Delete All Data?",
    message = "This action cannot be undone.",
    confirmText = "Delete",
    isDangerous = true
)
@ActionHandler(DeleteDataAction::class)
val deleteData: Unit = Unit
```

### Button Actions

```kotlin
// Define action
object ClearCacheAction : SettingAction {
    override val id = "clear_cache"
}

// Register handler at app startup
ActionRegistry.register<ClearCacheAction> {
    // Clear cache logic
    cacheManager.clear()
}

// In settings
@Setting(title = "Clear Cache", category = Advanced::class, type = Button::class)
@ActionHandler(ClearCacheAction::class)
val clearCache: Unit = Unit
```

### Reset Behavior

```kotlin
@Setting(title = "API Key", category = Advanced::class, type = TextInput::class)
@NoReset // This field won't be reset
val apiKey: String = ""

@Setting(title = "Custom Theme", category = Appearance::class, type = Button::class)
@ConfirmReset(message = "Reset custom theme to defaults?")
val customTheme: String = ""
```

### Localization (String Resources)

```kotlin
@Setting(
    titleRes = R.string.setting_dark_mode,
    descriptionRes = R.string.setting_dark_mode_desc,
    category = Appearance::class,
    type = Toggle::class
)
val darkMode: Boolean = false

@Setting(
    title = "Language",
    category = General::class,
    type = Dropdown::class,
    optionsRes = R.array.languages // String array resource
)
val language: Int = 0
```

Provide resources in your Compose UI:

```kotlin
// Android
val provider = AndroidStringResourceProvider(context)

ProvideStringResources(provider) {
    AutoSettingsScreen(...)
}
```

## Migrations

```kotlin
@SchemaVersion(2)
data class AppSettings(
    @Setting(...)
    @RenamedFrom("dark_theme", sinceVersion = 2)
    val darkMode: Boolean = false,

    @Setting(...)
    @AddedInVersion(2)
    val accentColor: String = "#6200EE",

    @Persisted
    @DeprecatedSetting(message = "Use newField instead", removeInVersion = 3)
    val oldField: String = ""
)
```

```kotlin
val migrationManager = MigrationManager(dataStore, currentVersion = 2)
    .addKeyRename(fromVersion = 1, toVersion = 2, "dark_theme", "dark_mode")
    .addKeyDeletion(fromVersion = 1, toVersion = 2, "removed_setting")

// Run at app startup
when (val result = migrationManager.migrate()) {
    is MigrationResult.Success -> println("Migrated from ${result.fromVersion} to ${result.toVersion}")
    is MigrationResult.PartialSuccess -> println("Some migrations failed: ${result.errors}")
    MigrationResult.NoMigrationNeeded -> println("Already up to date")
}
```

## Backup & Restore

```kotlin
val backupManager = SettingsBackupManager(
    dataStore = dataStore,
    schema = AppSettingsSchema,
    appId = "com.myapp",
    schemaVersion = 2
)

// Export
when (val result = backupManager.export()) {
    is ExportResult.Success -> saveToFile(result.json)
    is ExportResult.Error -> showError(result.message)
}

// Import
when (val result = backupManager.import(jsonString)) {
    is ImportResult.Success -> showMessage("Imported ${result.appliedCount} settings")
    is ImportResult.Error -> showError(result.message)
}

// Validate before importing
val validation = backupManager.validate(jsonString)
if (validation.isValid) {
    // Safe to import
}
```

## Undo/Redo

```kotlin
val undoManager = UndoManager(repository, maxHistory = 20)

// Record changes (call this when setting changes)
undoManager.recordChange("darkMode", oldValue = false, newValue = true)

// Undo/Redo
if (undoManager.canUndo.value) {
    undoManager.undo()
}
if (undoManager.canRedo.value) {
    undoManager.redo()
}
```

## Settings Lock (PIN Protection)

```kotlin
val lockManager = SettingsLockManager(dataStore)

// Enable lock
lockManager.enableLock("1234")

// Check if locked
lockManager.isLocked.collect { isLocked ->
    if (isLocked) {
        // Show PIN dialog
    }
}

// Unlock
when (lockManager.unlock("1234")) {
    UnlockResult.Success -> // Continue
    UnlockResult.InvalidPin -> // Show error
}

// Set auto-lock timeout (5 minutes)
lockManager.setLockTimeout(5 * 60 * 1000L)
```

## Reset Manager

```kotlin
val resetManager = ResetManager(dataStore, AppSettingsSchema)

// Reset single field
resetManager.resetField("darkMode")

// Reset multiple fields
resetManager.resetFields(listOf("darkMode", "fontSize"))

// Reset category
resetManager.resetCategory(Appearance::class)

// Reset all UI settings
resetManager.resetUISettings()

// Reset everything
resetManager.resetAll()

// Create/restore snapshots
val snapshot = resetManager.createSnapshot()
// Later...
resetManager.restoreSnapshot(snapshot)
```

## Custom UI Types

```kotlin
// Define custom type
object ColorPicker : SettingTypeMarker

// Create handler
val colorPickerHandler = CustomTypeHandler<AppSettings>(
    typeClass = ColorPicker::class,
    render = { field, meta, value, enabled, onSet ->
        // Your custom color picker UI
        ColorPickerRow(
            title = meta.resolvedTitle(LocalStringResourceProvider.current),
            color = (field as SettingField<AppSettings, String>).get(value),
            enabled = enabled,
            onColorSelected = { color -> onSet(field.name, color) }
        )
    }
)

// Use in AutoSettingsScreen
AutoSettingsScreen(
    schema = AppSettingsSchema,
    value = settings,
    onSet = { name, value -> repository.set(name, value) },
    customTypeHandlers = listOf(colorPickerHandler)
)
```

## Observing Changes

```kotlin
// Observe specific field
repository.observeField<Boolean>("darkMode").collect { isDark ->
    updateTheme(isDark)
}

// Listen for changes
repository.addChangeListener { field, oldValue, newValue ->
    analytics.log("Setting changed: ${field.name}")
}

// Field-specific listener
repository.addFieldListener<Boolean>("darkMode") { old, new ->
    if (new) enableDarkMode() else disableDarkMode()
}
```

## Compose Helpers

```kotlin
@Composable
fun MyScreen() {
    // Observe field as state
    val darkMode by repository.observeFieldAsState("darkMode", initial = false)

    // React to changes
    OnSettingChanged(repository, "theme") { old, new ->
        // Handle theme change
    }
}
```

## ProGuard / R8

The library includes consumer ProGuard rules. If you have issues, add:

```proguard
-keep class * implements io.github.mlmgames.settings.core.SettingsSchema { *; }
-keep class * implements io.github.mlmgames.settings.core.SettingField { *; }
-keep @io.github.mlmgames.settings.core.annotations.CategoryDefinition class * { *; }
```

## Complete Example

See the [app](github.com/mlm-games/iremote) for a working example.

```kotlin
// Categories.kt
@CategoryDefinition(order = 0)
object General

@CategoryDefinition(order = 1)
object Appearance

@CategoryDefinition(order = 2)
object Privacy

@CategoryDefinition(order = 3)
object Advanced

// Settings.kt
@SchemaVersion(1)
data class AppSettings(
    // General
    @Setting(title = "Username", category = General::class, type = TextInput::class)
    @Required
    @Length(min = 3, max = 30)
    val username: String = "",

    @Setting(title = "Notifications", category = General::class, type = Toggle::class)
    val notifications: Boolean = true,

    // Appearance
    @Setting(title = "Theme", category = Appearance::class, type = Dropdown::class, 
             options = ["Light", "Dark", "System"])
    val theme: Int = 2,

    @Setting(title = "Font Size", category = Appearance::class, type = Slider::class,
             min = 12f, max = 24f, step = 2f)
    val fontSize: Float = 16f,

    // Privacy
    @Setting(title = "Analytics", category = Privacy::class, type = Toggle::class)
    @RequiresConfirmation(message = "This helps us improve the app")
    val analytics: Boolean = true,

    // Advanced
    @Setting(title = "Clear Cache", category = Advanced::class, type = Button::class)
    @ActionHandler(ClearCacheAction::class)
    val clearCache: Unit = Unit,

    // Internal
    @Persisted
    val onboardingComplete: Boolean = false,

    @Persisted
    val lastOpenedVersion: Int = 0
)

// App.kt
class MyApp : Application() {
    lateinit var settingsRepository: SettingsRepository<AppSettings>

    override fun onCreate() {
        super.onCreate()
        
        val dataStore = createSettingsDataStore(this, "settings")
        settingsRepository = SettingsRepository(dataStore, AppSettingsSchema)

        // Register actions
        ActionRegistry.register<ClearCacheAction> {
            cacheDir.deleteRecursively()
        }
    }
}

// SettingsScreen.kt
@Composable
fun SettingsScreen(repository: SettingsRepository<AppSettings>) {
    val settings by repository.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    ProvideStringResources(AndroidStringResourceProvider(LocalContext.current)) {
        AutoSettingsScreen(
            schema = AppSettingsSchema,
            value = settings,
            onSet = { name, value ->
                scope.launch { repository.set(name, value) }
            },
            onAction = { actionClass ->
                ActionRegistry.execute(actionClass)
            },
            categoryConfigs = listOf(
                CategoryConfig(General::class, "General Settings"),
                CategoryConfig(Appearance::class, "Look & Feel"),
                CategoryConfig(Privacy::class, "Privacy"),
                CategoryConfig(Advanced::class, "Advanced")
            )
        )
    }
}
```

## License

```
Copyright 2024 MLM Games

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
