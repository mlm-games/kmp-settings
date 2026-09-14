package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val mutex = Mutex()
    private val migrations = mutableListOf<Migration>()

    fun addMigration(migration: Migration): MigrationManager {
        migrations.add(migration)
        return this
    }

    fun addKeyRename(fromVersion: Int, toVersion: Int, oldKey: String, newKey: String): MigrationManager =
        addMigration(KeyRenameMigration(fromVersion, toVersion, oldKey, newKey))

    fun addKeyDeletion(fromVersion: Int, toVersion: Int, vararg keys: String): MigrationManager =
        addMigration(KeyDeletionMigration(fromVersion, toVersion, keys.toList()))

    /**
     * Applies the migration chain storedVersion -> currentVersion.
     *
     * Selection covers any migration overlapping the outstanding range
     * (stored < to && from < current) and the chain is validated for
     * continuity before anything runs. The version marker advances only on
     * full success; on partial failure it stays at the stored version so the
     * next launch retries instead of sealing half-migrated data as done.
     * Downgrades (stored > current) are reported, never silently stamped.
     */
    suspend fun migrate(): MigrationResult = mutex.withLock {
        val prefs = dataStore.data.first()
        val storedVersion = prefs[VERSION_KEY] ?: 0

        if (storedVersion == currentVersion) {
            return MigrationResult.NoMigrationNeeded
        }
        if (storedVersion > currentVersion) {
            return MigrationResult.DowngradeDetected(storedVersion, currentVersion)
        }

        val applicable = migrations
            .filter { it.fromVersion < currentVersion && it.toVersion > storedVersion }
            .sortedWith(compareBy({ it.fromVersion }, { it.toVersion }))

        if (applicable.isEmpty()) {
            dataStore.updateData { p ->
                p.toMutablePreferences().apply { this[VERSION_KEY] = currentVersion }.toPreferences()
            }
            return MigrationResult.NoMigrationNeeded
        }

        // Chain must be continuous: first.from <= stored, each next.from <= prev.to,
        // last.to >= current. Otherwise report instead of half-applying.
        val gaps = findChainGaps(applicable, storedVersion, currentVersion)
        if (gaps.isNotEmpty()) {
            return MigrationResult.ChainBroken(storedVersion, currentVersion, gaps)
        }

        val applied = mutableListOf<Int>()
        val errors = mutableListOf<Pair<Int, String>>()

        try {
            dataStore.updateData { p ->
                val mutablePrefs = p.toMutablePreferences()
                var failed = false

                for (migration in applicable) {
                    // Skip migrations already fully covered by the stored version
                    // only when the chain prefix is intact (validated above).
                    if (migration.toVersion <= storedVersion) continue
                    try {
                        migration.migrate(mutablePrefs)
                        applied.add(migration.toVersion)
                    } catch (e: Exception) {
                        errors.add(migration.toVersion to (e.message ?: "Unknown error"))
                        failed = true
                        break
                    }
                }

                // updateData is atomic: throwing aborts the whole write, so a
                // failed migration never persists partial key changes.
                if (failed) throw MigrationFailedException(errors)
                mutablePrefs[VERSION_KEY] = currentVersion
                mutablePrefs.toPreferences()
            }
        } catch (e: MigrationFailedException) {
            // Version marker untouched; next launch retries from storedVersion.
            return MigrationResult.PartialSuccess(storedVersion, currentVersion, applied.size, errors)
        }

        return MigrationResult.Success(storedVersion, currentVersion, applied.size)
    }

    private fun findChainGaps(
        chain: List<Migration>,
        storedVersion: Int,
        currentVersion: Int,
    ): List<String> {
        val gaps = mutableListOf<String>()
        var covered = storedVersion
        for (m in chain) {
            if (m.fromVersion > covered) {
                gaps.add("gap ${covered}->${m.fromVersion} (migration ${m.fromVersion}->${m.toVersion})")
            }
            if (m.toVersion <= m.fromVersion) {
                gaps.add("invalid range ${m.fromVersion}->${m.toVersion}")
            }
            covered = maxOf(covered, m.toVersion)
            if (covered >= currentVersion) break
        }
        if (covered < currentVersion) {
            gaps.add("chain ends at $covered, need $currentVersion")
        }
        return gaps
    }

    suspend fun getStoredVersion(): Int = dataStore.data.first()[VERSION_KEY] ?: 0

    private class MigrationFailedException(val errors: List<Pair<Int, String>>) : Exception("migration failed")
}

sealed class MigrationResult {
    object NoMigrationNeeded : MigrationResult()
    data class Success(val fromVersion: Int, val toVersion: Int, val migrationsApplied: Int) : MigrationResult()
    data class PartialSuccess(val fromVersion: Int, val toVersion: Int, val migrationsApplied: Int, val errors: List<Pair<Int, String>>) : MigrationResult()
    data class DowngradeDetected(val storedVersion: Int, val currentVersion: Int) : MigrationResult()
    data class ChainBroken(val storedVersion: Int, val currentVersion: Int, val gaps: List<String>) : MigrationResult()
}

/**
 * Renames every physical key variant, including nullable suffixed keys
 * ("${key}_nullable", "${key}_nullable_long"). Callers may additionally pass
 * [field] so custom-suffixed keys stay in sync without hard-coding names.
 */
private class KeyRenameMigration(
    override val fromVersion: Int,
    override val toVersion: Int,
    private val oldKey: String,
    private val newKey: String,
    private val field: SettingField<*, *>? = null,
) : Migration {
    override suspend fun migrate(prefs: MutablePreferences) {
        val suffixes = buildList {
            add("")
            add("_nullable")
            add("_nullable_long")
            // Reflect internal physical keys when the field is available so
            // future suffix changes cannot silently desync.
            (field as? io.github.mlmgames.settings.core.fields.NullableBooleanField<*>)?.let { f ->
                f.physicalKeys.mapTo(this) { it.name.removePrefix(oldKey) }
            }
        }.distinct()

        val prefTypes = listOf(
            ::stringPreferencesKey, ::intPreferencesKey, ::longPreferencesKey,
            ::floatPreferencesKey, ::doublePreferencesKey, ::booleanPreferencesKey,
            ::stringSetPreferencesKey,
        )

        for (suffix in suffixes) {
            for (keyFn in prefTypes) {
                @Suppress("UNCHECKED_CAST")
                val old = keyFn(oldKey + suffix) as Preferences.Key<Any?>
                @Suppress("UNCHECKED_CAST")
                val new = keyFn(newKey + suffix) as Preferences.Key<Any?>
                (prefs[old])?.let { value ->
                    prefs[new] = value
                    prefs.remove(old)
                }
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
        val suffixes = listOf("", "_nullable", "_nullable_long")
        for (key in keys) {
            for (suffix in suffixes) {
                listOf(
                    stringPreferencesKey(key + suffix),
                    intPreferencesKey(key + suffix),
                    longPreferencesKey(key + suffix),
                    floatPreferencesKey(key + suffix),
                    doublePreferencesKey(key + suffix),
                    booleanPreferencesKey(key + suffix),
                    stringSetPreferencesKey(key + suffix),
                ).forEach { prefs.remove(it) }
            }
        }
    }
}
