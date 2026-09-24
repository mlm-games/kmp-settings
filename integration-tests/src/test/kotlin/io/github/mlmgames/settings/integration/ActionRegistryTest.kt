package io.github.mlmgames.settings.integration

import io.github.mlmgames.settings.core.actions.ActionRegistry
import io.github.mlmgames.settings.core.annotations.SettingAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class ActionRegistryTest {
    @Test
    fun retainsConcurrentRegistrations() = runTest {
        val start = CompletableDeferred<Unit>()
        val jobs = (0 until 100).map { index ->
            async(Dispatchers.Default) {
                start.await()
                if (index % 2 == 0) {
                    ActionRegistry.registerAction(ActionOne::class, ActionOne())
                } else {
                    ActionRegistry.registerAction(ActionTwo::class, ActionTwo())
                }
            }
        }

        try {
            start.complete(Unit)
            jobs.awaitAll()

            assertNotNull(ActionRegistry.getAction(ActionOne::class))
            assertNotNull(ActionRegistry.getAction(ActionTwo::class))
        } finally {
            ActionRegistry.clear()
        }
    }
}

private class ActionOne : SettingAction
private class ActionTwo : SettingAction
