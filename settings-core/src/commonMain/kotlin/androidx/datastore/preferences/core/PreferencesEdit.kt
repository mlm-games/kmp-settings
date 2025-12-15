package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend inline fun edit(
  dataStore: DataStore<Preferences>,
  crossinline transform: (MutablePreferences) -> Unit,
) {
  withContext(Dispatchers.Default) {
    dataStore.edit { prefs -> transform(prefs) }
  }
}