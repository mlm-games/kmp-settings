package io.github.mlmgames.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import okio.Path.Companion.toPath

/*
    Stores the settings in these locations:
    // Windows: %LOCALAPPDATA%\<appName> or %APPDATA%\<appName>
    // macOS: ~/Library/Application Support/<appName>
    // Linux/BSD/Unix: XDG_DATA_HOME or ~/.local/share/<appName>
*/
fun createSettingsDataStore(name: String): DataStore<Preferences> =
    createDataStore(
        producePath = {
            val dir = getAppDataDir(name)
            dir.mkdirs()
            File(dir, "$name.preferences_pb").absolutePath
        }
    )

private fun getAppDataDir(appName: String): File {
    val os = System.getProperty("os.name").lowercase()

    return when {
        os.contains("win") -> {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?: System.getenv("APPDATA")
                ?: "${System.getProperty("user.home")}\\AppData\\Local"
            File(localAppData, appName)
        }
        os.contains("mac") || os.contains("darwin") -> {
            val home = System.getProperty("user.home")
            File(home, "Library/Application Support/$appName")
        }
        else -> {
            val dataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                ?: "${System.getProperty("user.home")}/.local/share"
            File(dataHome, appName)
        }
    }
}

internal actual fun createPreferencesStorage(path: String): Storage<Preferences> =
    FileStorage(
        serializer = PreferencesFileSerializer,
        produceFile = { path.toPath().toFile() }
    )

internal actual val dataStoreContext: CoroutineContext =
    CoroutineScope(Dispatchers.IO + SupervisorJob()).coroutineContext
