@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock

class SettingsLockManager(
    private val dataStore: DataStore<Preferences>,
    private val pinHasher: PinHasher = Sha256PinHasher,
) {
    companion object {
        private val LOCK_ENABLED_KEY = booleanPreferencesKey("__settings_lock_enabled__")
        private val PIN_HASH_KEY = stringPreferencesKey("__settings_pin_hash__")
        private val LOCK_TIMEOUT_KEY = longPreferencesKey("__settings_lock_timeout__")
        private val LAST_UNLOCK_KEY = longPreferencesKey("__settings_last_unlock__")

        /**
         * Default re-lock timeout: 5 minutes. A non-null default is required —
         * a 0/absent timeout previously meant "always locked", making unlock()
         * a permanent no-op.
         */
        const val DEFAULT_LOCK_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }

    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

    val isLockEnabled: Flow<Boolean> = dataStore.data.map { it[LOCK_ENABLED_KEY] ?: false }

    val isLocked: Flow<Boolean> = dataStore.data.map { prefs ->
        val enabled = prefs[LOCK_ENABLED_KEY] ?: false
        if (!enabled) return@map false

        // Absent timeout falls back to the default (locked only after expiry),
        // so a freshly enabled lock is usable until the timeout elapses.
        val timeout = prefs[LOCK_TIMEOUT_KEY] ?: DEFAULT_LOCK_TIMEOUT_MILLIS
        if (timeout <= 0L) return@map false

        val lastUnlock = prefs[LAST_UNLOCK_KEY] ?: 0L
        val now = currentTimeMillis()
        (now - lastUnlock) > timeout
    }

    suspend fun enableLock(pin: String): Boolean {
        if (!isValidPinFormat(pin)) return false

        dataStore.edit { prefs ->
            prefs[LOCK_ENABLED_KEY] = true
            prefs[PIN_HASH_KEY] = pinHasher.hash(pin)
            // Fresh installs start unlocked only if a timeout exists; otherwise
            // default-timeout logic above keeps them locked after first lock().
            if (LAST_UNLOCK_KEY !in prefs) {
                prefs[LAST_UNLOCK_KEY] = currentTimeMillis()
            }
            if (LOCK_TIMEOUT_KEY !in prefs) {
                prefs[LOCK_TIMEOUT_KEY] = DEFAULT_LOCK_TIMEOUT_MILLIS
            }
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
        require(timeoutMillis > 0) { "Lock timeout must be positive" }
        dataStore.edit { prefs -> prefs[LOCK_TIMEOUT_KEY] = timeoutMillis }
    }

    suspend fun changePin(currentPin: String, newPin: String): Boolean {
        if (!validatePin(currentPin)) return false
        if (!isValidPinFormat(newPin)) return false

        // Validate-then-write inside one transaction so a concurrent changePin
        // cannot silently overwrite without knowing the current PIN.
        var applied = false
        dataStore.edit { prefs ->
            val stored = prefs[PIN_HASH_KEY] ?: return@edit
            if (!pinHasher.verify(currentPin, stored)) return@edit
            prefs[PIN_HASH_KEY] = pinHasher.hash(newPin)
            applied = true
        }
        return applied
    }

    suspend fun hasPinSet(): Boolean = dataStore.data.first()[PIN_HASH_KEY] != null

    private fun isValidPinFormat(pin: String): Boolean =
        pin.length in 4..6 && pin.all { it.isDigit() }
}

sealed class UnlockResult {
    object Success : UnlockResult()
    object InvalidPin : UnlockResult()
}

interface PinHasher {
    fun hash(pin: String): String
    fun verify(pin: String, hash: String): Boolean
}

/**
 * SHA-256 hash with a fixed application salt. A salted one-way hash replaces
 * the previous unsalted `String.hashCode()` (32-bit, reversible by brute force
 * in milliseconds). For stronger protection (per-install salt, stretching),
 * inject a custom [PinHasher].
 */
object Sha256PinHasher : PinHasher {
    private const val SALT = "kmp-settings:pin:v1"

    override fun hash(pin: String): String =
        (SALT + pin).encodeUtf8().sha256().hex()

    override fun verify(pin: String, hash: String): Boolean = hash(pin) == hash
}
