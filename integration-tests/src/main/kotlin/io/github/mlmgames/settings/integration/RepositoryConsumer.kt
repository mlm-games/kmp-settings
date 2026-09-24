package io.github.mlmgames.settings.integration

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.flow.Flow

fun integrationRepository(dataStore: DataStore<Preferences>): Flow<IntegrationSettings> =
    SettingsRepository(dataStore, IntegrationSettingsSchema).flow
