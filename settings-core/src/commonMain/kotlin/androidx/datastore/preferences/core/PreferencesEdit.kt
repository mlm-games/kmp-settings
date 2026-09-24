package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Deprecated("Use the DataStore.edit extension")
suspend inline fun edit(
    dataStore: DataStore<Preferences>,
    crossinline transform: (MutablePreferences) -> Unit,
) {
    withContext(Dispatchers.Default) {
        dataStore.edit { preferences -> transform(preferences) }
    }
}
