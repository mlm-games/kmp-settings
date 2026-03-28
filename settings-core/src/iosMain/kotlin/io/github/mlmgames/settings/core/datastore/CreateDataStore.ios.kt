package io.github.mlmgames.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.IO
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalForeignApi::class)
fun createSettingsDataStore(name: String): DataStore<Preferences> =
  createDataStore(
    producePath = {
      val dir: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
      )
      requireNotNull(dir).path + "/$name.preferences_pb"
    }
  )

internal actual fun createPreferencesStorage(path: String): Storage<Preferences> =
    OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = PreferencesSerializer,
        producePath = { path.toPath() }
    )

internal actual val dataStoreContext: CoroutineContext =
    CoroutineScope(Dispatchers.IO + SupervisorJob()).coroutineContext