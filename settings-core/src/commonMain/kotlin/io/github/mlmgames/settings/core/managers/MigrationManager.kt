package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first

interface Migration {
    val fromVersion: Int
    val toVersion: Int
    suspend fun migrate(prefs: MutablePreferences)
}

class MigrationManager(
    private val dataStore: DataStore<Preferences>,
    private val currentVersion: Int,
) {
    companion object {
        private val VERSION_KEY = intPreferencesKey("__schema_version__")
    }

    private val migrations = mutableListOf<Migration>()

    fun addMigration(migration: Migration): MigrationManager {
        migrations.add(migration)
        return this
    }

    fun addKeyRename(fromVersion: Int, toVersion: Int, oldKey: String, newKey: String): MigrationManager =
        addMigration(KeyRenameMigration(fromVersion, toVersion, oldKey, newKey))

    fun addKeyDeletion(fromVersion: Int, toVersion: Int, vararg keys: String): MigrationManager =
        addMigration(KeyDeletionMigration(fromVersion, toVersion, keys.toList()))

    suspend fun migrate(): MigrationResult {
        val prefs = dataStore.data.first()
        val storedVersion = prefs[VERSION_KEY] ?: 0

        if (storedVersion >= currentVersion) {
            return MigrationResult.NoMigrationNeeded
        }

        val applicableMigrations = migrations
            .filter { it.fromVersion >= storedVersion && it.toVersion <= currentVersion }
            .sortedBy { it.fromVersion }

        if (applicableMigrations.isEmpty()) {
            dataStore.updateData { p ->
                p.toMutablePreferences().apply { this[VERSION_KEY] = currentVersion }.toPreferences()
            }
            return MigrationResult.NoMigrationNeeded
        }

        val applied = mutableListOf<Int>()
        val errors = mutableListOf<Pair<Int, String>>()

        dataStore.updateData { p ->
            val mutablePrefs = p.toMutablePreferences()

            for (migration in applicableMigrations) {
                try {
                    migration.migrate(mutablePrefs)
                    applied.add(migration.toVersion)
                } catch (e: Exception) {
                    errors.add(migration.toVersion to (e.message ?: "Unknown error"))
                }
            }

            mutablePrefs[VERSION_KEY] = currentVersion
            mutablePrefs.toPreferences()
        }

        return if (errors.isEmpty()) {
            MigrationResult.Success(storedVersion, currentVersion, applied.size)
        } else {
            MigrationResult.PartialSuccess(storedVersion, currentVersion, applied.size, errors)
        }
    }

    suspend fun getStoredVersion(): Int = dataStore.data.first()[VERSION_KEY] ?: 0
}

sealed class MigrationResult {
    object NoMigrationNeeded : MigrationResult()
    data class Success(val fromVersion: Int, val toVersion: Int, val migrationsApplied: Int) : MigrationResult()
    data class PartialSuccess(val fromVersion: Int, val toVersion: Int, val migrationsApplied: Int, val errors: List<Pair<Int, String>>) : MigrationResult()
}

private class KeyRenameMigration(
    override val fromVersion: Int,
    override val toVersion: Int,
    private val oldKey: String,
    private val newKey: String,
) : Migration {
    override suspend fun migrate(prefs: MutablePreferences) {
        val prefTypes = listOf(
            stringPreferencesKey(oldKey) to stringPreferencesKey(newKey),
            intPreferencesKey(oldKey) to intPreferencesKey(newKey),
            longPreferencesKey(oldKey) to longPreferencesKey(newKey),
            floatPreferencesKey(oldKey) to floatPreferencesKey(newKey),
            doublePreferencesKey(oldKey) to doublePreferencesKey(newKey),
            booleanPreferencesKey(oldKey) to booleanPreferencesKey(newKey),
            stringSetPreferencesKey(oldKey) to stringSetPreferencesKey(newKey),
        )

        for ((old, new) in prefTypes) {
            @Suppress("UNCHECKED_CAST")
            (prefs[old as Preferences.Key<Any?>])?.let { value ->
                prefs[new as Preferences.Key<Any?>] = value
                prefs.remove(old)
            }
        }
    }
}

private class KeyDeletionMigration(
    override val fromVersion: Int,
    override val toVersion: Int,
    private val keys: List<String>,
) : Migration {
    override suspend fun migrate(prefs: MutablePreferences) {
        for (key in keys) {
            listOf(
                stringPreferencesKey(key),
                intPreferencesKey(key),
                longPreferencesKey(key),
                floatPreferencesKey(key),
                doublePreferencesKey(key),
                booleanPreferencesKey(key),
                stringSetPreferencesKey(key),
            ).forEach { prefs.remove(it) }
        }
    }
}