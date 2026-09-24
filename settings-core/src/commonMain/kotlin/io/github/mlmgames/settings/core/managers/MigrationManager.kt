@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.mlmgames.settings.core.SettingFieldStorage
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.atomics.AtomicReference

interface Migration {
    val fromVersion: Int
    val toVersion: Int
    suspend fun migrate(prefs: MutablePreferences)
}

class MigrationManager(
    private val dataStore: DataStore<Preferences>,
    private val currentVersion: Int,
    private val schema: SettingsSchema<*>?,
) {
    constructor(dataStore: DataStore<Preferences>, currentVersion: Int) : this(dataStore, currentVersion, null)
    companion object {
        private val VERSION_KEY = intPreferencesKey("__schema_version__")
        private val migrationMutex = Mutex()
    }

    private data class MigrationRegistry(
        val values: List<Migration>,
        val generation: Long,
    )

    private val registry = AtomicReference(MigrationRegistry(emptyList(), 0L))

    fun addMigration(migration: Migration): MigrationManager {
        while (true) {
            val current = registry.load()
            if (registry.compareAndSet(current, MigrationRegistry(current.values + migration, current.generation + 1))) return this
        }
    }

    fun addKeyRename(fromVersion: Int, toVersion: Int, oldKey: String, newKey: String): MigrationManager =
        addMigration(KeyRenameMigration(fromVersion, toVersion, oldKey, newKey))

    fun addKeyDeletion(fromVersion: Int, toVersion: Int, vararg keys: String): MigrationManager =
        addMigration(KeyDeletionMigration(fromVersion, toVersion, keys.toList()))

    suspend fun migrate(): MigrationResult {
        migrationMutex.lock()
        return try {
            while (true) {
                val snapshot = registry.load()
                try {
                    return migrateLocked(snapshot.values, snapshot.generation)
                } catch (_: ConcurrentMigrationChange) {
                }
            }
            @Suppress("UNREACHABLE_CODE")
            MigrationResult.NoMigrationNeeded
        } finally {
            migrationMutex.unlock()
        }
    }

    private suspend fun migrateLocked(
        registeredMigrations: List<Migration>,
        registrationGeneration: Long,
    ): MigrationResult {
        var result: MigrationResult? = null

        try {
            dataStore.updateData { preferences ->
                val mutablePreferences = preferences.toMutablePreferences()
                val storedVersion = mutablePreferences[VERSION_KEY] ?: 0
                val activeSchema = schema
                val schemaMigrations = if (activeSchema == null || storedVersion >= currentVersion) {
                    emptyList()
                } else {
                    activeSchema.fieldMetadata.mapNotNull { (fieldName, metadata) ->
                        val oldKey = metadata.renamedFrom ?: return@mapNotNull null
                        val since = metadata.renamedSinceVersion ?: return@mapNotNull null
                        if (storedVersion >= since) return@mapNotNull null
                        val field = activeSchema.fieldByName(fieldName) ?: return@mapNotNull null
                        KeyRenameMigration(since - 1, since, oldKey, field.keyName)
                    }
                }
                val effectiveMigrations = (registeredMigrations + schemaMigrations).toList()

                when {
                    currentVersion < 0 -> {
                        result = MigrationResult.ChainBroken(
                            storedVersion,
                            currentVersion,
                            listOf("invalid current version $currentVersion"),
                        )
                        preferences
                    }
                    storedVersion < 0 -> {
                        result = MigrationResult.ChainBroken(
                            storedVersion,
                            currentVersion,
                            listOf("invalid stored version $storedVersion"),
                        )
                        preferences
                    }
                    storedVersion == currentVersion -> {
                        result = MigrationResult.NoMigrationNeeded
                        preferences
                    }
                    storedVersion > currentVersion -> {
                        result = MigrationResult.DowngradeDetected(storedVersion, currentVersion)
                        preferences
                    }
                    else -> {
                        val applicable = effectiveMigrations
                            .filter { it.fromVersion < currentVersion && it.toVersion > storedVersion }
                            .sortedWith(compareBy({ it.fromVersion }, { it.toVersion }))
                        val chain = if (registeredMigrations.isEmpty()) {
                            completeVersionChain(applicable, storedVersion, currentVersion)
                        } else {
                            applicable
                        }

                        if (applicable.isEmpty() && effectiveMigrations.isNotEmpty()) {
                            result = MigrationResult.ChainBroken(
                                storedVersion,
                                currentVersion,
                                listOf("no migration covers $storedVersion->$currentVersion"),
                            )
                            preferences
                        } else if (applicable.isEmpty()) {
                            ensureRegistrationUnchanged(registrationGeneration)
                            mutablePreferences[VERSION_KEY] = currentVersion
                            result = MigrationResult.NoMigrationNeeded
                            mutablePreferences.toPreferences()
                        } else {
                            val gaps = findChainGaps(chain, storedVersion, currentVersion)
                            if (gaps.isNotEmpty()) {
                                result = MigrationResult.ChainBroken(storedVersion, currentVersion, gaps)
                                preferences
                            } else {
                                val applied = mutableListOf<Int>()
                                for (migration in chain) {
                                    try {
                                        migration.migrate(mutablePreferences)
                                        if (migration !is VersionOnlyMigration) applied += migration.toVersion
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        throw MigrationFailedException(
                                            MigrationResult.PartialSuccess(
                                                storedVersion,
                                                currentVersion,
                                                0,
                                                listOf(migration.toVersion to (e.message ?: "Unknown error")),
                                            ),
                                        )
                                    }
                                }
                                ensureRegistrationUnchanged(registrationGeneration)
                                mutablePreferences[VERSION_KEY] = currentVersion
                                result = MigrationResult.Success(
                                    storedVersion,
                                    currentVersion,
                                    applied.size,
                                )
                                mutablePreferences.toPreferences()
                            }
                        }
                    }
                }
            }
        } catch (e: MigrationFailedException) {
            return e.result
        }

        return result ?: MigrationResult.NoMigrationNeeded
    }

    private fun ensureRegistrationUnchanged(generation: Long) {
        if (registry.load().generation != generation) throw ConcurrentMigrationChange()
    }

    private fun completeVersionChain(
        migrations: List<Migration>,
        storedVersion: Int,
        currentVersion: Int,
    ): List<Migration> {
        val result = mutableListOf<Migration>()
        var covered = storedVersion
        for (migration in migrations) {
            if (migration.fromVersion > covered) {
                result += VersionOnlyMigration(covered, migration.fromVersion)
            }
            result += migration
            covered = maxOf(covered, migration.toVersion)
        }
        if (covered < currentVersion) result += VersionOnlyMigration(covered, currentVersion)
        return result
    }

    private fun findChainGaps(
        chain: List<Migration>,
        storedVersion: Int,
        currentVersion: Int,
    ): List<String> {
        val gaps = mutableListOf<String>()
        var covered = storedVersion

        for (migration in chain) {
            if (migration.fromVersion < 0 || migration.toVersion < 0) {
                gaps += "invalid range ${migration.fromVersion}->${migration.toVersion}"
            } else if (migration.toVersion <= migration.fromVersion) {
                gaps += "invalid range ${migration.fromVersion}->${migration.toVersion}"
            }
            if (migration.fromVersion > covered) {
                gaps += "gap $covered->${migration.fromVersion} (migration ${migration.fromVersion}->${migration.toVersion})"
            }
            if (migration.toVersion > currentVersion) {
                gaps += "migration ${migration.fromVersion}->${migration.toVersion} exceeds current version $currentVersion"
            }
            covered = maxOf(covered, migration.toVersion)
        }

        if (covered < currentVersion) {
            gaps += "chain ends at $covered, need $currentVersion"
        }
        return gaps.distinct()
    }

    suspend fun getStoredVersion(): Int = dataStore.data.first()[VERSION_KEY] ?: 0

    private class MigrationFailedException(val result: MigrationResult) : Exception("migration failed")
    private class ConcurrentMigrationChange : Exception()
}

sealed class MigrationResult {
    object NoMigrationNeeded : MigrationResult()
    data class Success(val fromVersion: Int, val toVersion: Int, val migrationsApplied: Int) : MigrationResult()
    data class PartialSuccess(val fromVersion: Int, val toVersion: Int, val migrationsApplied: Int, val errors: List<Pair<Int, String>>) : MigrationResult()
    data class DowngradeDetected(val storedVersion: Int, val currentVersion: Int) : MigrationResult()
    data class ChainBroken(val storedVersion: Int, val currentVersion: Int, val gaps: List<String>) : MigrationResult()
}

private const val STORAGE_NAMESPACE = SettingFieldStorage.NAMESPACE
private const val NULL_STORAGE_NAMESPACE = SettingFieldStorage.NULL_NAMESPACE

private fun storageKeyName(keyName: String, kind: String): String =
    "$STORAGE_NAMESPACE$kind:${keyName.length}:$keyName"

private fun nullStorageKeyName(keyName: String, kind: String): String =
    "$NULL_STORAGE_NAMESPACE$kind:${keyName.length}:$keyName"

private val PHYSICAL_KEY_SUFFIXES = listOf("", "_nullable", "_nullable_long")
private val STORAGE_KINDS = listOf(
    "boolean",
    "int",
    "long",
    "float",
    "double",
    "string",
    "nullable_boolean",
    "nullable_int",
    "nullable_long",
    "nullable_float",
    "nullable_double",
    "nullable_string",
    "enum",
    "nullable_enum",
    "enum_ordinal",
    "serialized",
    "nullable_serialized",
    "string_list",
    "int_list",
    "long_list",
    "map",
    "string_set",
)

private data class PhysicalRename(val source: String, val target: String)

private class VersionOnlyMigration(
    override val fromVersion: Int,
    override val toVersion: Int,
) : Migration {
    override suspend fun migrate(prefs: MutablePreferences) = Unit
}

private fun samePreferenceValue(first: Any, second: Any): Boolean =
    if (first is ByteArray && second is ByteArray) first.contentEquals(second) else first == second

private fun physicalRenames(oldKey: String, newKey: String): List<PhysicalRename> {
    val result = mutableListOf<PhysicalRename>()
    PHYSICAL_KEY_SUFFIXES.forEach { suffix ->
        result += PhysicalRename(oldKey + suffix, newKey + suffix)
    }
    STORAGE_KINDS.forEach { kind ->
        result += PhysicalRename(storageKeyName(oldKey, kind), storageKeyName(newKey, kind))
        result += PhysicalRename(nullStorageKeyName(oldKey, kind), nullStorageKeyName(newKey, kind))
    }
    return result
}

private fun physicalKeyNames(key: String): Set<String> =
    physicalRenames(key, key).mapTo(linkedSetOf()) { it.source }

private class KeyRenameMigration(
    override val fromVersion: Int,
    override val toVersion: Int,
    private val oldKey: String,
    private val newKey: String,
) : Migration {
    override suspend fun migrate(prefs: MutablePreferences) {
        if (oldKey == newKey) return

        val renameBySource = physicalRenames(oldKey, newKey).associate { it.source to it.target }
        val allEntries = prefs.asMap()
        val entries = allEntries.filterKeys { it.name in renameBySource }
        if (entries.isEmpty()) return

        val renames = entries.keys.associate { key -> key.name to renameBySource.getValue(key.name) }
        val duplicateTargets = renames.values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateTargets.isNotEmpty()) {
            throw IllegalArgumentException("Key rename produces overlapping targets: ${duplicateTargets.joinToString()}")
        }

        val valuesByName = entries.entries.associate { it.key.name to it.value }
        val existingNames = allEntries.keys.mapTo(hashSetOf()) { it.name }
        val redundantTargets = hashSetOf<String>()
        for ((sourceName, targetName) in renames) {
            if (targetName != sourceName && targetName in existingNames && targetName !in renameBySource) {
                val targetValue = allEntries.entries.first { it.key.name == targetName }.value
                if (!samePreferenceValue(targetValue, valuesByName.getValue(sourceName))) {
                    throw IllegalArgumentException("Key rename target already exists: $targetName")
                }
                redundantTargets += targetName
            }
        }

        for (sourceName in renames.keys) {
            prefs.remove(stringPreferencesKey(sourceName))
        }
        for ((sourceName, targetName) in renames) {
            if (targetName !in redundantTargets) {
                writePreference(prefs, targetName, valuesByName.getValue(sourceName))
            }
        }
    }

    private fun writePreference(prefs: MutablePreferences, name: String, value: Any) {
        when (value) {
            is Boolean -> prefs[booleanPreferencesKey(name)] = value
            is Int -> prefs[intPreferencesKey(name)] = value
            is Long -> prefs[longPreferencesKey(name)] = value
            is Float -> prefs[floatPreferencesKey(name)] = value
            is Double -> prefs[doublePreferencesKey(name)] = value
            is String -> prefs[stringPreferencesKey(name)] = value
            is Set<*> -> prefs[stringSetPreferencesKey(name)] = value.map { it as String }.toSet()
            is ByteArray -> prefs[byteArrayPreferencesKey(name)] = value
            else -> throw IllegalArgumentException("Unsupported preference type: ${value::class}")
        }
    }
}

private class KeyDeletionMigration(
    override val fromVersion: Int,
    override val toVersion: Int,
    private val keys: List<String>,
) : Migration {
    override suspend fun migrate(prefs: MutablePreferences) {
        val names = keys.flatMapTo(hashSetOf()) { physicalKeyNames(it) }
        val existingKeys = prefs.asMap().keys.filter { it.name in names }
        for (key in existingKeys) {
            prefs.remove(stringPreferencesKey(key.name))
        }
    }
}
