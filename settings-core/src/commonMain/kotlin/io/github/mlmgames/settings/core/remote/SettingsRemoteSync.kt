package io.github.mlmgames.settings.core.remote

import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.SettingsSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mirrors schema fields between a local [SettingsRepository] and a [RemoteSettingsStore], so
 * a preference follows the account rather than the device.
 *
 * The reconciliation rules, which callers otherwise reimplement:
 *
 * - A field the remote does not hold is left alone. Absent is not a value, so it never
 *   overwrites the local one.
 * - A read that fails mirrors nothing. A transport error and an empty remote are different
 *   things, and treating the first as the second writes local state to the server on a
 *   network blip.
 * - A value adopted from the remote is not written straight back. Local text is compared
 *   against the last known remote text, so an attachment that changes nothing sends nothing.
 * - Nothing is written after [detach], which is what keeps a signed-out account from pushing
 *   at a backend it no longer has a session with.
 *
 * Call [attach] when the remote becomes reachable and [detach] when it stops being so.
 */
class SettingsRemoteSync<T>(
    private val repository: SettingsRepository<T>,
    private val schema: SettingsSchema<T>,
    private val store: RemoteSettingsStore,
    bindings: List<RemoteBinding>,
    private val pullPolicy: RemotePullPolicy = RemotePullPolicy.ONCE_PER_ATTACH,
) : AutoCloseable {
    private val resolved: List<ResolvedBinding<T>> = bindings.map { binding ->
        val field = schema.fieldByName(binding.field)
            ?: throw IllegalArgumentException("Unknown remote field: '${binding.field}'")
        @Suppress("UNCHECKED_CAST")
        ResolvedBinding(binding, field as SettingField<T, Any?>)
    }

    private val state = MutableStateFlow<Map<String, RemoteFieldState>>(
        resolved.associate { it.binding.field to RemoteFieldState.LocalOnly },
    )

    /** Mirroring status per bound field, for a settings row to display. */
    val states: StateFlow<Map<String, RemoteFieldState>> = state.asStateFlow()

    private val lastKnownRemote = mutableMapOf<String, String>()

    /**
     * Serialises the per-field reconcile. Under [RemotePullPolicy.LIVE] a remote change and a
     * local change can arrive on different coroutines, and both consult and update
     * [lastKnownRemote].
     */
    private val guard = Mutex()
    private var job: Job? = null

    /**
     * Reads the remote store, adopts what it holds, then mirrors local changes until
     * [detach]. Replaces any previous attachment.
     */
    fun attach(scope: CoroutineScope) {
        detach()
        if (resolved.isEmpty()) return
        job = scope.launch { run() }
    }

    /** Stops mirroring. Local values are untouched and no further writes are made. */
    fun detach() {
        job?.cancel()
        job = null
        lastKnownRemote.clear()
        state.update { current ->
            current.mapValues { (_, value) ->
                if (value == RemoteFieldState.Unsupported) value else RemoteFieldState.LocalOnly
            }
        }
    }

    override fun close() {
        detach()
    }

    private suspend fun run() {
        val initial = readOrReport() ?: return
        adopt(initial)
        coroutineScope {
            if (pullPolicy == RemotePullPolicy.LIVE) {
                launch { store.changes.collect { adopt(it) } }
            }
            launch { repository.flow.collect { mirror(it) } }
        }
    }

    /** Current remote text for the pull-enabled fields, or null when it cannot be read. */
    private suspend fun readOrReport(): Map<String, String>? {
        val pullFields = resolved.filter { it.binding.pull }.map { it.binding.field }
        if (pullFields.isEmpty()) return emptyMap()
        return try {
            store.read(pullFields)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unsupported: RemoteUnsupportedException) {
            resolved.forEach { setState(it.binding.field, RemoteFieldState.Unsupported) }
            null
        } catch (error: Exception) {
            resolved.forEach { setState(it.binding.field, RemoteFieldState.Failed(describe(error))) }
            null
        }
    }

    private suspend fun adopt(remote: Map<String, String>) {
        resolved.forEach { entry ->
            if (!entry.binding.pull) return@forEach
            val text = remote[entry.binding.field] ?: return@forEach
            if (entry.decode(text) == null) return@forEach
            guard.withLock { pullInto(entry, text) }
        }
    }

    /**
     * Writes out whatever the local model holds. A value the remote is already known to hold
     * is skipped, which is what stops an adopted value being written straight back.
     */
    private suspend fun mirror(model: T) {
        resolved.forEach { entry ->
            val text = entry.encode(entry.field.get(model)) ?: return@forEach
            guard.withLock { pushOut(entry, text) }
        }
    }

    private suspend fun pullInto(entry: ResolvedBinding<T>, text: String) {
        val name = entry.binding.field
        val decoded = entry.decode(text) ?: return
        lastKnownRemote[name] = text
        if (entry.field.get(repository.flow.first()) == decoded) {
            setState(name, RemoteFieldState.Synced)
            return
        }
        setState(name, RemoteFieldState.Syncing)
        try {
            repository.set(name, decoded)
            setState(name, RemoteFieldState.Synced)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setState(name, RemoteFieldState.Failed(describe(error)))
        }
    }

    private suspend fun pushOut(entry: ResolvedBinding<T>, text: String) {
        val name = entry.binding.field
        if (state.value[name] == RemoteFieldState.Unsupported) return
        if (text == lastKnownRemote[name]) {
            setState(name, RemoteFieldState.Synced)
            return
        }
        setState(name, RemoteFieldState.Syncing)
        try {
            store.write(name, text)
            lastKnownRemote[name] = text
            setState(name, RemoteFieldState.Synced)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unsupported: RemoteUnsupportedException) {
            setState(name, RemoteFieldState.Unsupported)
        } catch (error: Exception) {
            setState(name, RemoteFieldState.Failed(describe(error)))
        }
    }

    private fun setState(field: String, value: RemoteFieldState) {
        state.update { current ->
            if (current[field] == value) current else current + (field to value)
        }
    }

    private fun describe(error: Exception): String =
        error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName.orEmpty()

    private class ResolvedBinding<T>(
        val binding: RemoteBinding,
        val field: SettingField<T, Any?>,
    ) {
        fun encode(value: Any?): String? =
            binding.codec?.encode(value) ?: field.toRemoteValue(value)

        fun decode(text: String): Any? =
            binding.codec?.decode(text) ?: field.fromRemoteValue(text)
    }
}
