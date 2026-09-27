package io.github.mlmgames.settings.core.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A remote store holding values on the user's behalf, such as a server-side account
 * preference. Implementations own the transport and the shape of the remote document; the
 * library only owns the reconciliation policy, so an implementation is free to do the
 * read-modify-write that keeps sibling values in that document intact.
 *
 * Values cross this boundary as text. A field the remote does not hold is absent from
 * [read]'s result, and absent never overwrites the local value.
 */
interface RemoteSettingsStore {
    /**
     * Current remote text for [fields]. A field the remote does not hold is omitted rather
     * than mapped to an empty value.
     *
     * Throws [RemoteUnsupportedException] when the backend cannot carry these fields at all.
     */
    suspend fun read(fields: List<String>): Map<String, String>

    /** Writes [value] for [field], leaving the rest of the remote document alone. */
    suspend fun write(field: String, value: String)

    /**
     * Remote-side changes, for backends that can notify. Empty by default, which is correct
     * for backends that only report a value when the account is opened.
     */
    val changes: Flow<Map<String, String>>
        get() = emptyFlow()
}

/**
 * The backend has no way to carry these fields: the event type is unimplemented, the server
 * is too old, or the account is not the kind that has this store. The local value stays
 * editable and is simply not synchronised.
 */
class RemoteUnsupportedException(message: String? = null) : Exception(message)

/** When [SettingsRemoteSync] re-reads the remote store. */
enum class RemotePullPolicy {
    /** Read once per attachment. Right for backends with no change notification. */
    ONCE_PER_ATTACH,

    /** Re-read on every emission of [RemoteSettingsStore.changes]. */
    LIVE,
}

/** Mirroring status of one bound field, for display. */
sealed interface RemoteFieldState {
    /** Not bound to a remote store, or detached. */
    object LocalOnly : RemoteFieldState

    /** A read or write is in flight. */
    object Syncing : RemoteFieldState

    /** Local and remote agree. */
    object Synced : RemoteFieldState

    /** The backend cannot carry this field. */
    object Unsupported : RemoteFieldState

    /** The last operation failed. Syncing resumes on the next change. */
    data class Failed(val reason: String) : RemoteFieldState
}

/**
 * Binds one schema field to a remote store.
 *
 * [pull] false marks a push-only field: local changes are written out, remote values are
 * never adopted. That suits a preference the server mirrors but does not arbitrate.
 */
data class RemoteBinding(
    val field: String,
    val pull: Boolean = true,
    /**
     * Text conversion for fields whose own form is not usable on the wire, such as sets and
     * serialised objects. Null uses the field's built-in text form.
     */
    val codec: RemoteCodec? = null,
)

/** Converts between a field value and the text a [RemoteSettingsStore] carries. */
interface RemoteCodec {
    /** Null when [value] has no text form. */
    fun encode(value: Any?): String?

    /** Null when [remote] is not a value this codec understands. */
    fun decode(remote: String): Any?
}
