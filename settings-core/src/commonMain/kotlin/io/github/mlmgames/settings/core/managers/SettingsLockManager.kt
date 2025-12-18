package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class SettingsLockManager(
    private val dataStore: DataStore<Preferences>,
    private val pinHasher: PinHasher = DefaultPinHasher,
) {
    companion object {
        private val LOCK_ENABLED_KEY = booleanPreferencesKey("__settings_lock_enabled__")
        private val PIN_HASH_KEY = stringPreferencesKey("__settings_pin_hash__")
        private val LOCK_TIMEOUT_KEY = longPreferencesKey("__settings_lock_timeout__")
        private val LAST_UNLOCK_KEY = longPreferencesKey("__settings_last_unlock__")
    }

    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

    val isLockEnabled: Flow<Boolean> = dataStore.data.map { it[LOCK_ENABLED_KEY] ?: false }

    val isLocked: Flow<Boolean> = dataStore.data.map { prefs ->
        val enabled = prefs[LOCK_ENABLED_KEY] ?: false
        if (!enabled) return@map false

        val timeout = prefs[LOCK_TIMEOUT_KEY] ?: 0L
        if (timeout == 0L) return@map true

        val lastUnlock = prefs[LAST_UNLOCK_KEY] ?: 0L
        val now = currentTimeMillis()
        (now - lastUnlock) > timeout
    }

    suspend fun enableLock(pin: String): Boolean {
        if (pin.length < 4) return false

        dataStore.edit { prefs ->
            prefs[LOCK_ENABLED_KEY] = true
            prefs[PIN_HASH_KEY] = pinHasher.hash(pin)
        }
        return true
    }

    suspend fun disableLock(pin: String): Boolean {
        if (!validatePin(pin)) return false

        dataStore.edit { prefs ->
            prefs[LOCK_ENABLED_KEY] = false
            prefs.remove(PIN_HASH_KEY)
            prefs.remove(LAST_UNLOCK_KEY)
        }
        return true
    }

    suspend fun validatePin(pin: String): Boolean {
        val storedHash = dataStore.data.first()[PIN_HASH_KEY] ?: return false
        return pinHasher.verify(pin, storedHash)
    }

    suspend fun unlock(pin: String): UnlockResult {
        if (!validatePin(pin)) return UnlockResult.InvalidPin

        dataStore.edit { prefs ->
            prefs[LAST_UNLOCK_KEY] = currentTimeMillis()
        }

        return UnlockResult.Success
    }

    suspend fun lock() {
        dataStore.edit { prefs -> prefs[LAST_UNLOCK_KEY] = 0L }
    }

    suspend fun setLockTimeout(timeoutMillis: Long) {
        dataStore.edit { prefs -> prefs[LOCK_TIMEOUT_KEY] = timeoutMillis }
    }

    suspend fun changePin(currentPin: String, newPin: String): Boolean {
        if (!validatePin(currentPin)) return false
        if (newPin.length < 4) return false

        dataStore.edit { prefs -> prefs[PIN_HASH_KEY] = pinHasher.hash(newPin) }
        return true
    }

    suspend fun hasPinSet(): Boolean = dataStore.data.first()[PIN_HASH_KEY] != null
}

sealed class UnlockResult {
    object Success : UnlockResult()
    object InvalidPin : UnlockResult()
}

interface PinHasher {
    fun hash(pin: String): String
    fun verify(pin: String, hash: String): Boolean
}

object DefaultPinHasher : PinHasher {
    override fun hash(pin: String): String = pin.hashCode().toString(16)
    override fun verify(pin: String, hash: String): Boolean = hash(pin) == hash
}