@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.mlmgames.settings.core.managers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
        private val SHA_PIN_HASH_KEY = stringPreferencesKey("__kmp_settings_v2__:pin_hash")
        private val LOCK_TIMEOUT_KEY = longPreferencesKey("__settings_lock_timeout__")
        private val LAST_UNLOCK_KEY = longPreferencesKey("__settings_last_unlock__")

        const val DEFAULT_LOCK_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }

    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

    val isLockEnabled: Flow<Boolean> = dataStore.data.map { it[LOCK_ENABLED_KEY] ?: false }

    val isLocked: Flow<Boolean> = channelFlow {
        dataStore.data.collectLatest { prefs ->
            when (val state = lockState(prefs)) {
                LockState.Disabled -> send(false)
                LockState.Locked -> send(true)
                is LockState.TimedUnlocked -> {
                    send(false)
                    delay(state.remainingMillis)
                    send(true)
                }
            }
        }
    }.distinctUntilChanged()

    suspend fun enableLock(pin: String): Boolean {
        if (!isValidPinFormat(pin)) return false
        val now = currentTimeMillis()

        dataStore.edit { prefs ->
            prefs[LOCK_ENABLED_KEY] = true
            prefs[SHA_PIN_HASH_KEY] = pinHasher.hash(pin)
            prefs[PIN_HASH_KEY] = LegacyHashCodePinHasher.hash(pin)
            if (LAST_UNLOCK_KEY !in prefs) prefs[LAST_UNLOCK_KEY] = now
            if (LOCK_TIMEOUT_KEY !in prefs) prefs[LOCK_TIMEOUT_KEY] = DEFAULT_LOCK_TIMEOUT_MILLIS
        }
        return true
    }

    suspend fun disableLock(pin: String): Boolean {
        var applied = false
        dataStore.edit { prefs ->
            val storedHash = currentPinHash(prefs) ?: return@edit
            if (verifyPin(pin, storedHash) == null) return@edit

            prefs[LOCK_ENABLED_KEY] = false
            prefs.remove(PIN_HASH_KEY)
            prefs.remove(SHA_PIN_HASH_KEY)
            prefs.remove(LAST_UNLOCK_KEY)
            applied = true
        }
        return applied
    }

    suspend fun validatePin(pin: String): Boolean {
        val storedHash = currentPinHash(dataStore.data.first()) ?: return false
        return verifyPin(pin, storedHash) != null
    }

    suspend fun unlock(pin: String): UnlockResult {
        var applied = false
        dataStore.edit { prefs ->
            if ((prefs[LOCK_TIMEOUT_KEY] ?: 0L) <= 0L) return@edit
            val storedHash = currentPinHash(prefs) ?: return@edit
            val match = verifyPin(pin, storedHash) ?: return@edit

            if (match.legacy) {
                prefs[SHA_PIN_HASH_KEY] = pinHasher.hash(pin)
                prefs[PIN_HASH_KEY] = LegacyHashCodePinHasher.hash(pin)
            }
            prefs[LAST_UNLOCK_KEY] = currentTimeMillis()
            applied = true
        }
        return if (applied) UnlockResult.Success else UnlockResult.InvalidPin
    }

    suspend fun lock() {
        dataStore.edit { prefs -> prefs[LAST_UNLOCK_KEY] = 0L }
    }

    suspend fun setLockTimeout(timeoutMillis: Long) {
        require(timeoutMillis >= 0) { "Lock timeout cannot be negative" }
        dataStore.edit { prefs -> prefs[LOCK_TIMEOUT_KEY] = timeoutMillis }
    }

    suspend fun changePin(currentPin: String, newPin: String): Boolean {
        if (!isValidPinFormat(newPin)) return false

        var applied = false
        dataStore.edit { prefs ->
            val storedHash = currentPinHash(prefs) ?: return@edit
            if (verifyPin(currentPin, storedHash) == null) return@edit

            prefs[SHA_PIN_HASH_KEY] = pinHasher.hash(newPin)
            prefs[PIN_HASH_KEY] = LegacyHashCodePinHasher.hash(newPin)
            applied = true
        }
        return applied
    }

    suspend fun hasPinSet(): Boolean = currentPinHash(dataStore.data.first()) != null

    private fun currentPinHash(prefs: Preferences): String? =
        prefs[SHA_PIN_HASH_KEY] ?: prefs[PIN_HASH_KEY]

    private fun lockState(prefs: Preferences): LockState {
        if (prefs[LOCK_ENABLED_KEY] != true) return LockState.Disabled

        val timeout = prefs[LOCK_TIMEOUT_KEY] ?: 0L
        if (timeout <= 0L) return LockState.Locked

        val lastUnlock = prefs[LAST_UNLOCK_KEY] ?: return LockState.Locked
        if (lastUnlock <= 0L) return LockState.Locked

        val now = currentTimeMillis()
        val elapsed = if (now >= lastUnlock) now - lastUnlock else -1L
        if (elapsed >= timeout) return LockState.Locked
        if (elapsed < 0L) return LockState.TimedUnlocked(timeout)
        return LockState.TimedUnlocked(timeout - elapsed)
    }

    private fun verifyPin(pin: String, storedHash: String): PinHashMatch? {
        if (runVerify(pinHasher, pin, storedHash)) return PinHashMatch(legacy = false)
        if (runVerify(LegacyHashCodePinHasher, pin, storedHash)) return PinHashMatch(legacy = true)
        return null
    }

    private fun runVerify(hasher: PinHasher, pin: String, storedHash: String): Boolean {
        return try {
            hasher.verify(pin, storedHash)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidPinFormat(pin: String): Boolean =
        pin.length in 4..6 && pin.all { it.isDigit() }

    private data class PinHashMatch(val legacy: Boolean)

    private sealed interface LockState {
        data object Disabled : LockState
        data object Locked : LockState
        data class TimedUnlocked(val remainingMillis: Long) : LockState
    }
}

sealed class UnlockResult {
    object Success : UnlockResult()
    object InvalidPin : UnlockResult()
}

interface PinHasher {
    fun hash(pin: String): String
    fun verify(pin: String, hash: String): Boolean
}

object LegacyHashCodePinHasher : PinHasher {
    override fun hash(pin: String): String = pin.hashCode().toString(16)

    override fun verify(pin: String, hash: String): Boolean = hash(pin) == hash
}

object DefaultPinHasher : PinHasher {
    override fun hash(pin: String): String = LegacyHashCodePinHasher.hash(pin)

    override fun verify(pin: String, hash: String): Boolean =
        LegacyHashCodePinHasher.verify(pin, hash)
}

object Sha256PinHasher : PinHasher {
    private const val SALT = "kmp-settings:pin:v1"

    override fun hash(pin: String): String =
        (SALT + pin).encodeUtf8().sha256().hex()

    override fun verify(pin: String, hash: String): Boolean = hash(pin) == hash
}
