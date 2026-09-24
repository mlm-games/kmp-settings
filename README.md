# kmp-settings

Type-safe settings management for Kotlin Multiplatform with declarative UI generation.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mlm-games/kmp-settings-core)](https://search.maven.org/search?q=g:io.github.mlm-games)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

## Features

- **Declarative settings**: Define settings with `@Setting` and `@Persisted` annotations on data class properties
- **Auto-generated schema**: KSP processor generates type-safe `SettingsSchema` with field metadata
- **Built-in persistence**: DataStore-based storage with support for primitives, collections, enums, and serialized objects
- **Auto-generated UI**: `AutoSettingsScreen` composable renders settings from schema with zero boilerplate
- **Cross-platform**: Core supports Android, iOS, JVM (Desktop), Linux, and Wasm; the Compose UI currently targets Android, iOS, JVM, and Wasm
- **Backup/restore**: JSON export/import with checksum validation and schema versioning
- **Advanced features**: Field dependencies, validation rules, confirmation dialogs, undo/redo, reset management

## Installation

```kotlin
// In your build.gradle.kts
dependencies {
    // Core (required)
    implementation("io.github.mlmgames:kmp-settings-core:<version>")
    
    // KSP processor (required for code generation)
    ksp("io.github.mlmgames:kmp-settings-ksp:<version>")
    
    // Compose UI (optional)
    implementation("io.github.mlmgames:kmp-settings-ui-compose:<version>")
}
```

For a Kotlin Multiplatform module, apply the KSP plugin and add the processor
to `kspCommonMainMetadata` so the shared schema is generated once.

**Requirements:**
- Android minSdk 21
- Java 17 or newer for JVM builds, the KSP processor, and Gradle

## Quick Start

### 1. Define your settings

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.annotations.*
import io.github.mlmgames.settings.core.types.*
import io.github.mlmgames.settings.ui.AutoSettingsScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

// Define categories
@CategoryDefinition(order = 0)
object General

@CategoryDefinition(order = 1)
object Appearance

// Define settings data class
@kotlinx.serialization.Serializable
data class AppSettings(
    @Setting(
        title = "Dark Mode",
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
        options = ["English", "Spanish", "French"]
    )
    val language: Int = 0,
    
    // Persisted but not shown in UI
    @Persisted
    val lastSyncTime: Long = 0L
)
```

### 2. Build to generate schema

The KSP processor generates `AppSettingsSchema` automatically.

### 3. Create repository and UI

```kotlin
class SettingsViewModel(
    dataStore: DataStore<Preferences>,
) : ViewModel() {
    private val repository = SettingsRepository(
        dataStore = dataStore,
        schema = AppSettingsSchema,
    )
    
    val settings = repository.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = AppSettingsSchema.default
    )
    
    fun updateSetting(name: String, value: Any?) {
        viewModelScope.launch {
            repository.set(name, value)
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    
    AutoSettingsScreen(
        schema = AppSettingsSchema,
        value = settings,
        onSet = viewModel::updateSetting
    )
}
```

Create the `DataStore` in platform code and pass it into shared code. Android uses
`createSettingsDataStore(context, "settings")`; JVM, iOS, Linux, and Wasm use
`createSettingsDataStore("settings")`. The library keeps one active store per
canonical file and rejects path-like store names.

### Localized settings metadata

Use stable string keys for metadata that must be translated. Keys are resolved by
`StringResourceProvider` before legacy resource IDs and literal fallbacks:

```kotlin
@Setting(
    titleKey = "settings.appearance.dark_mode",
    descriptionKey = "settings.appearance.dark_mode.description",
    optionsKey = "settings.appearance.theme.options",
    category = Appearance::class,
    type = Toggle::class,
)
val darkMode: Boolean = false
```

The same key API is available for categories, confirmation dialogs, and validation
messages. Existing `title`, `titleRes`, and `descriptionRes` declarations remain
supported for migration. On Android, provide a key-to-resource resolver when
application resources use names different from the library defaults:

```kotlin
ProvideStringResources(
    AndroidStringResourceProvider(context) { key ->
        when (key) {
            "settings.appearance.dark_mode" -> R.string.dark_mode
            else -> 0
        }
    }
) {
    AutoSettingsScreen(/* ... */)
}
```

`SettingsTextKeys` contains the built-in UI keys used by the generated settings
screen and dialogs. The KSP processor warns when UI metadata relies only on
literal text.

## Usage

### Supported Field Types

| Kotlin Type | Storage | UI Types |
|------------|---------|----------|
| `Boolean` | DataStore | Toggle |
| `Int`, `Long`, `Float`, `Double` | DataStore | Slider, Dropdown |
| `String` | DataStore | TextInput, Dropdown |
| `Set<String>` | DataStore | [custom] |
| `List<String>`, `List<Int>` | JSON serialized | [custom] |
| `Map<K, V>` | JSON serialized | [custom] |
| `@Serializable` objects | JSON serialized | [custom] |
| `Enum` | String key | Dropdown |
| `Unit` | - | Button |

### Platform-Specific Settings

```kotlin
@Setting(
    title = "Android-only Feature",
    category = General::class,
    type = Toggle::class,
    platforms = [SettingPlatform.ANDROID]  // Hidden on iOS/Desktop
)
val androidFeature: Boolean = false
```

### Field Dependencies

```kotlin
@Setting(
    title = "Enable Notifications",
    category = General::class,
    type = Toggle::class
)
val notificationsEnabled: Boolean = false

@Setting(
    title = "Notification Sound",
    category = General::class,
    type = Toggle::class,
    dependsOn = "notificationsEnabled"  // Disabled when above is false
)
val notificationSound: Boolean = true
```

### Validation & Confirmation

```kotlin
@Setting(
    title = "Server URL",
    category = General::class,
    type = TextInput::class
)
@Pattern("^https?://.*")  // Regex validation
val serverUrl: String = "https://api.example.com"

@Setting(
    title = "Delete All Data",
    category = General::class,
    type = Button::class
)
@RequiresConfirmation(
    title = "Confirm Deletion",
    message = "This cannot be undone. Continue?",
    isDangerous = true
)
@ActionHandler(DeleteDataAction::class)
val deleteData: Unit = Unit
```

### Backup & Restore

```kotlin
val backupManager = SettingsBackupManager(
    dataStore = dataStore,
    schema = AppSettingsSchema,
    appId = "com.example.myapp",
    schemaVersion = 1
)

// Export
when (val result = backupManager.export()) {
    is ExportResult.Success -> saveToFile(result.json)
    is ExportResult.Error -> showError(result.message)
}

// Import with validation
val jsonString = readFromFile()
when (val result = backupManager.import(jsonString)) {
    is ImportResult.Success -> showSuccess("Imported ${result.appliedCount} settings")
    is ImportResult.Error -> when (result.error) {
        ImportError.APP_MISMATCH -> showError("Wrong app backup")
        ImportError.VERSION_TOO_NEW -> showError("Backup from newer version")
        ImportError.CHECKSUM_MISMATCH -> showError("Corrupted backup file")
        else -> showError(result.message)
    }
}
```

The importer accepts legacy `0.8.1` backup checksums and nullable storage markers.
New exports use a versioned, unambiguous format. Legacy PIN hashes and timeout
values are also migrated when they are successfully verified.

### Custom UI Types

```kotlin
AutoSettingsScreen(
    schema = AppSettingsSchema,
    value = settings,
    onSet = viewModel::updateSetting,
    customTypeHandlers = listOf(
        CustomTypeHandler(
            typeClass = ColorPicker::class,
            render = { field, meta, value, enabled, onSet ->
                val color = (field as SettingField<AppSettings, String>).get(value)
                ColorPickerButton(
                    color = color,
                    enabled = enabled,
                    onColorSelected = { onSet(field.name, it) }
                )
            }
        )
    )
)
```

## Contributing

Development setup:

```bash
git clone https://github.com/mlmgames/kmp-settings.git
cd kmp-settings
./gradlew build
```

The JVM integration fixture in `integration-tests` compiles generated schemas
and runs behavioral tests on Java 17. The root `build` task runs it automatically
after publishing the local project artifacts; it can also be run directly with
`./gradlew integrationTest`. Platform-specific suites are exposed as normal
Gradle test tasks.

To publish locally for testing:
```bash
./gradlew publishToMavenLocal
```

## Support

- Issues: [GitHub Issues](https://github.com/mlmgames/kmp-settings/issues)
- Discussions: [GitHub Discussions](https://github.com/mlmgames/kmp-settings/discussions)

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.
