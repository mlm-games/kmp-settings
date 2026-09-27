package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.remote.RemoteBinding
import io.github.mlmgames.settings.core.remote.RemoteFieldState
import io.github.mlmgames.settings.core.remote.RemotePullPolicy
import io.github.mlmgames.settings.core.remote.RemoteSettingsStore
import io.github.mlmgames.settings.core.remote.RemoteUnsupportedException
import io.github.mlmgames.settings.core.remote.SettingsRemoteSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** In-memory stand-in for a server-side account store. */
private class FakeRemoteStore(
    initial: Map<String, String> = emptyMap(),
    var readError: Exception? = null,
    var writeError: Exception? = null,
) : RemoteSettingsStore {
    val values = initial.toMutableMap()
    val writes = mutableListOf<Pair<String, String>>()
    var readCount = 0
    private val incoming = MutableStateFlow<Map<String, String>>(emptyMap())

    override val changes: Flow<Map<String, String>> = incoming

    override suspend fun read(fields: List<String>): Map<String, String> {
        readCount++
        readError?.let { throw it }
        return fields.mapNotNull { field -> values[field]?.let { field to it } }.toMap()
    }

    override suspend fun write(field: String, value: String) {
        writeError?.let { throw it }
        writes += field to value
        record(field, value)
    }

    fun emitRemote(field: String, value: String) {
        record(field, value)
    }

    /** Carries the full known state, so a late subscriber cannot miss an earlier change. */
    private fun record(field: String, value: String) {
        values[field] = value
        incoming.value = incoming.value + (field to value)
    }
}

class SettingsRemoteSyncTest {
    private fun repository(store: TestDataStore) =
        SettingsRepository(store.dataStore, PreviewSettingsSchema)

    private fun sync(
        repository: SettingsRepository<PreviewSettings>,
        remote: RemoteSettingsStore,
        pullPolicy: RemotePullPolicy = RemotePullPolicy.ONCE_PER_ATTACH,
        binding: RemoteBinding = RemoteBinding(field = "previews"),
    ) = SettingsRemoteSync(
        repository = repository,
        schema = PreviewSettingsSchema,
        store = remote,
        bindings = listOf(binding),
        pullPolicy = pullPolicy,
    )

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Real time: the sync runs on [Dispatchers.Default], which the test scheduler skips. */
    private suspend fun awaitReal(predicate: suspend () -> Boolean) =
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { while (!predicate()) delay(10) }
        }

    private suspend fun SettingsRemoteSync<PreviewSettings>.awaitState(
        expected: RemoteFieldState,
    ) = awaitReal { states.value["previews"] == expected }

    private suspend fun FakeRemoteStore.awaitWrite(count: Int = 1) =
        awaitReal { writes.size >= count }

    @Test
    fun adoptsRemoteValueWithoutWritingItBack() = runTest {
        val store = TestDataStore.create("remote-adopt")
        val remote = FakeRemoteStore(mapOf("previews" to "Private"))
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(repository, remote)
            sync.attach(scope)

            sync.awaitState(RemoteFieldState.Synced)
            assertEquals(PreviewMode.Private, repository.get<PreviewMode>("previews"))
            assertEquals(emptyList(), remote.writes)
        } finally {
            scope.cancel()
            store.close()
        }
    }

    /** Absent is not a value: a remote holding nothing must not reset the local choice. */
    @Test
    fun absentRemoteValueLeavesLocalAlone() = runTest {
        val store = TestDataStore.create("remote-absent")
        val remote = FakeRemoteStore()
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(repository, remote)
            repository.set("previews", PreviewMode.Off)
            sync.attach(scope)

            sync.awaitState(RemoteFieldState.Synced)
            assertEquals(PreviewMode.Off, repository.get<PreviewMode>("previews"))
            assertEquals(listOf("previews" to "Off"), remote.writes)
        } finally {
            scope.cancel()
            store.close()
        }
    }

    /**
     * A transport error is not an empty remote. Mirroring here would push the local value to
     * the server on a network blip, creating state the account never asked for.
     */
    @Test
    fun failedReadMirrorsNothing() = runTest {
        val store = TestDataStore.create("remote-read-failed")
        val remote = FakeRemoteStore(readError = IllegalStateException("offline"))
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(repository, remote)
            repository.set("previews", PreviewMode.Off)
            sync.attach(scope)

            awaitReal { sync.states.value["previews"] is RemoteFieldState.Failed }
            assertEquals(emptyList(), remote.writes)
            assertEquals(
                "offline",
                (sync.states.value["previews"] as RemoteFieldState.Failed).reason,
            )
        } finally {
            scope.cancel()
            store.close()
        }
    }

    @Test
    fun unsupportedBackendStopsMirroringButKeepsLocalEditable() = runTest {
        val store = TestDataStore.create("remote-unsupported")
        val remote = FakeRemoteStore(readError = RemoteUnsupportedException("no such event type"))
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(repository, remote)
            sync.attach(scope)

            sync.awaitState(RemoteFieldState.Unsupported)
            repository.set("previews", PreviewMode.Private)
            assertEquals(PreviewMode.Private, repository.get<PreviewMode>("previews"))
            assertEquals(emptyList(), remote.writes)
        } finally {
            scope.cancel()
            store.close()
        }
    }

    @Test
    fun writesLocalChangesAndMarksThemSynced() = runTest {
        val store = TestDataStore.create("remote-write")
        val remote = FakeRemoteStore(mapOf("previews" to "On"))
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(repository, remote)
            sync.attach(scope)
            sync.awaitState(RemoteFieldState.Synced)

            repository.set("previews", PreviewMode.Off)
            remote.awaitWrite()

            assertEquals(listOf("previews" to "Off"), remote.writes)
            assertEquals(RemoteFieldState.Synced, sync.states.value["previews"])
        } finally {
            scope.cancel()
            store.close()
        }
    }

    @Test
    fun failedWriteKeepsTheFieldFailedAndRetriesOnTheNextChange() = runTest {
        val store = TestDataStore.create("remote-write-failed")
        val remote = FakeRemoteStore(mapOf("previews" to "On"))
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(repository, remote)
            sync.attach(scope)
            sync.awaitState(RemoteFieldState.Synced)

            remote.writeError = IllegalStateException("rejected")
            repository.set("previews", PreviewMode.Off)
            awaitReal { sync.states.value["previews"] is RemoteFieldState.Failed }
            assertEquals(emptyList(), remote.writes)

            remote.writeError = null
            repository.set("previews", PreviewMode.Private)
            remote.awaitWrite()


            assertEquals(listOf("previews" to "Private"), remote.writes)
        } finally {
            scope.cancel()
            store.close()
        }
    }

    @Test
    fun livePolicyAdoptsRemoteChanges() = runTest {
        val store = TestDataStore.create("remote-live")
        val remote = FakeRemoteStore()
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(repository, remote, RemotePullPolicy.LIVE)
            sync.attach(scope)
            sync.awaitState(RemoteFieldState.Synced)

            remote.emitRemote("previews", "Off")
            awaitReal { repository.get<PreviewMode>("previews") == PreviewMode.Off }
            assertTrue(remote.writes.none { it == ("previews" to "Off") })
        } finally {
            scope.cancel()
            store.close()
        }
    }

    @Test
    fun detachStopsWriting() = runTest {
        val store = TestDataStore.create("remote-detach")
        val remote = FakeRemoteStore(mapOf("previews" to "On"))
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(repository, remote)
            sync.attach(scope)
            sync.awaitState(RemoteFieldState.Synced)

            sync.detach()
            repository.set("previews", PreviewMode.Off)
            withContext(Dispatchers.Default) { delay(200) }

            assertEquals(emptyList(), remote.writes)
            assertEquals(PreviewMode.Off, repository.get<PreviewMode>("previews"))
        } finally {
            scope.cancel()
            store.close()
        }
    }

    @Test
    fun pushOnlyBindingIsNeverReadOrAdopted() = runTest {
        val store = TestDataStore.create("remote-push-only")
        val remote = FakeRemoteStore(mapOf("previews" to "On"))
        val scope = scope()
        try {
            val repository = repository(store)
            val sync = sync(
                repository,
                remote,
                binding = RemoteBinding(field = "previews", pull = false),
            )
            sync.attach(scope)
            sync.awaitState(RemoteFieldState.Synced)

            assertEquals(0, remote.readCount)
            assertEquals(listOf("previews" to "On"), remote.writes)
        } finally {
            scope.cancel()
            store.close()
        }
    }
}
