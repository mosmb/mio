package io.morgan.mio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.network.NetworkAvailability
import io.morgan.mio.core.testing.provider.FakeProviderIdentityDataSource
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.di.AccountGraphRequest
import io.morgan.mio.di.AppGraph
import io.morgan.mio.di.TestGraphFixture
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AccountRefreshObserverTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun coldOfflineLaunchWaitsForAvailableAndIgnoresRepeatedAvailable() =
        runTest {
            val fixture = fixture(testScheduler, NetworkAvailability.UNAVAILABLE)
            try {
                val entered = CompletableDeferred<Unit>()
                fixture.enqueueImport(entered)
                fixture.observer.start(backgroundScope)
                runCurrent()
                assertTrue(fixture.imports.calls.isEmpty())

                fixture.graphFixture.networkMonitor.values.value = NetworkAvailability.AVAILABLE
                runCurrent()
                awaitRoomPipeline(fixture, testScheduler)
                awaitProviderCall(entered)
                assertEquals(1, fixture.imports.calls.size)

                fixture.graphFixture.networkMonitor.values.value = NetworkAvailability.AVAILABLE
                runCurrent()
                assertEquals(1, fixture.imports.calls.size)
            } finally {
                fixture.close()
            }
        }

    @Test
    fun accountRestorationWhileStartedTriggersRefreshOnce() =
        runTest {
            val fixture = fixture(testScheduler, NetworkAvailability.AVAILABLE, authenticated = false)
            try {
                val entered = CompletableDeferred<Unit>()
                fixture.enqueueImport(entered)
                fixture.observer.start(backgroundScope)
                runCurrent()
                assertTrue(fixture.imports.calls.isEmpty())

                fixture.graphFixture.accountSessionRepository.setStateForTest(
                    AccountSessionState.Authenticated(ACCOUNT_ID, ConnectedProvider.ANILIST),
                )
                runCurrent()
                awaitRoomPipeline(fixture, testScheduler)
                awaitProviderCall(entered)
                assertEquals(1, fixture.imports.calls.size)
            } finally {
                fixture.close()
            }
        }

    @Test
    fun simultaneousRefreshSignalsCollapseToOneTrailingRead() =
        runTest {
            val fixture = fixture(testScheduler, NetworkAvailability.AVAILABLE)
            try {
                val entered = CompletableDeferred<Unit>()
                val secondEntered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<ProviderResult<CompleteProviderImport>>()
                fixture.imports.enqueue { _, _ ->
                    entered.complete(Unit)
                    release.await()
                }
                fixture.enqueueImport(secondEntered)
                fixture.observer.start(backgroundScope)
                runCurrent()
                awaitRoomPipeline(fixture, testScheduler)
                awaitProviderCall(entered)

                repeat(20) { fixture.observer.requestRefresh() }
                release.complete(ProviderResult.Success(observerEmptySnapshot()))
                runCurrent()
                awaitRoomPipeline(fixture, testScheduler)
                awaitProviderCall(secondEntered)

                assertEquals(2, fixture.imports.calls.size)
            } finally {
                fixture.close()
            }
        }

    @Test
    fun leaseContentionSchedulesOneBoundedRetryWhenLeaseIsStillBusy() =
        runTest {
            val clock = MutableTestClock(NOW)
            val fixture =
                fixture(
                    testScheduler,
                    NetworkAvailability.AVAILABLE,
                    clock = clock,
                    testDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            try {
                val entered = CompletableDeferred<Unit>()
                fixture.enqueueImport(entered)
                fixture.graph.database.syncDao().tryAcquireLease(
                    ACCOUNT_ID.value,
                    "another-owner",
                    NOW,
                    NOW + 120.seconds,
                )
                fixture.observer.start(backgroundScope)
                runCurrent()
                awaitRoomPipeline(fixture, testScheduler)
                assertTrue(fixture.imports.calls.isEmpty())

                advanceUntilIdle()
                awaitRoomPipeline(fixture, testScheduler)
                assertEquals(120_000, testScheduler.currentTime)
                assertTrue(fixture.imports.calls.isEmpty())
                assertEquals(
                    "another-owner",
                    fixture.graph.database
                        .syncDao()
                        .getLease(ACCOUNT_ID.value)
                        ?.ownerToken,
                )
            } finally {
                fixture.close()
            }
        }

    @Test
    fun stopCancelsAnActiveRefreshAndPreventsLaterTransport() =
        runTest {
            val fixture = fixture(testScheduler, NetworkAvailability.AVAILABLE)
            try {
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<ProviderResult<CompleteProviderImport>>()
                fixture.imports.enqueue { _, _ ->
                    entered.complete(Unit)
                    release.await()
                }
                fixture.observer.start(backgroundScope)
                runCurrent()
                awaitRoomPipeline(fixture, testScheduler)
                awaitProviderCall(entered)

                fixture.observer.stop()
                runCurrent()
                fixture.observer.requestRefresh()
                fixture.graphFixture.networkMonitor.values.value = NetworkAvailability.UNAVAILABLE
                fixture.graphFixture.networkMonitor.values.value = NetworkAvailability.AVAILABLE
                runCurrent()
                awaitLeaseReleased(fixture)

                assertEquals(1, fixture.imports.calls.size)
                assertNull(
                    fixture.graph.database
                        .syncDao()
                        .getLease(ACCOUNT_ID.value),
                )
            } finally {
                fixture.close()
            }
        }

    @Test
    fun storageFailureDoesNotCrashTheObserverOrPreventTheNextRefresh() =
        runTest {
            val fixture = fixture(testScheduler, NetworkAvailability.AVAILABLE)
            try {
                val entered = CompletableDeferred<Unit>()
                fixture.imports.enqueue { _, _ ->
                    entered.complete(Unit)
                    throw io.morgan.mio.domain.sync.SyncInfrastructureException(
                        io.morgan.mio.domain.sync.SyncInfrastructureException.Kind.STORAGE,
                    )
                }
                fixture.observer.start(backgroundScope)
                runCurrent()
                awaitRoomPipeline(fixture, testScheduler)
                awaitProviderCall(entered)
                awaitLeaseReleased(fixture)
                val next = CompletableDeferred<Unit>()
                fixture.enqueueImport(next)
                fixture.observer.requestRefresh()
                runCurrent()
                awaitRoomPipeline(fixture, testScheduler)
                awaitProviderCall(next)
                assertEquals(2, fixture.imports.calls.size)
            } finally {
                fixture.close()
            }
        }

    private suspend fun fixture(
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        availability: NetworkAvailability,
        clock: MutableTestClock = MutableTestClock(NOW),
        authenticated: Boolean = true,
        testDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): ObserverFixture {
        val binding = providerBindingFixture(ProviderKey.ANILIST)
        val graphFixture =
            TestGraphFixture(
                context,
                scheduler,
                binding,
                taskClock = clock,
                testDispatcher = testDispatcher,
            )
        graphFixture.networkMonitor.values.value = availability
        graphFixture.database.selectedAccountDao().replaceSelectedAccount(
            SelectedAccountEntity("selected", ACCOUNT_ID.value, StoredConnectedProvider.ANILIST, "Fixture", null),
            AccountProviderIdEntity(ACCOUNT_ID.value, StoredConnectedProvider.ANILIST, REMOTE_ACCOUNT_ID),
        )
        graphFixture.providerCredentialStore.write(
            ProviderCredentials(ProviderKey.ANILIST, "fixture-access", null, null, "Bearer"),
        )
        (binding.identityDataSource as FakeProviderIdentityDataSource).enqueue {
            ProviderResult.Success(
                ProviderIdentity(ProviderKey.ANILIST, ProviderAccountId(REMOTE_ACCOUNT_ID), "Fixture", null),
            )
        }
        val graph = createDynamicGraphFactory<AppGraph.Factory>(graphFixture.bindings).create(context)
        check(graph.accountSessionRepository === graphFixture.accountSessionRepository)
        check(graph.networkMonitor === graphFixture.networkMonitor)
        check(graph.ioDispatcher === graphFixture.dispatcher)
        graph.accountGraphLifecycle.create(AccountGraphRequest(ACCOUNT_ID, binding))
        if (authenticated) {
            graphFixture.accountSessionRepository.setStateForTest(
                AccountSessionState.Authenticated(ACCOUNT_ID, ConnectedProvider.ANILIST),
            )
        }
        return ObserverFixture(
            graphFixture = graphFixture,
            graph = graph,
            observer = graph.accountRefreshObserver,
            imports = binding.initialImportDataSource as FakeProviderInitialImportDataSource,
        )
    }

    private suspend fun awaitProviderCall(signal: CompletableDeferred<Unit>) {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) { signal.await() }
        }
    }

    private suspend fun awaitRoomPipeline(
        fixture: ObserverFixture,
        scheduler: TestCoroutineScheduler,
    ) {
        repeat(4) {
            fixture.graph.database
                .syncDao()
                .getLease(ACCOUNT_ID.value)
            scheduler.runCurrent()
        }
    }

    private suspend fun awaitLeaseReleased(fixture: ObserverFixture) {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (fixture.graph.database
                        .syncDao()
                        .getLease(ACCOUNT_ID.value) != null
                ) {
                    delay(10)
                }
            }
        }
    }

    private data class ObserverFixture(
        val graphFixture: TestGraphFixture,
        val graph: AppGraph,
        val observer: AccountRefreshObserver,
        val imports: FakeProviderInitialImportDataSource,
    ) {
        fun enqueueImport(entered: CompletableDeferred<Unit>? = null) {
            imports.enqueue { _, _ ->
                entered?.complete(Unit)
                ProviderResult.Success(observerEmptySnapshot())
            }
        }

        fun close() {
            observer.stop()
            graph.accountGraphLifecycle.destroy()
            graphFixture.close()
        }
    }

    private class MutableTestClock(
        var current: Instant,
    ) : Clock {
        override fun now(): Instant = current
    }

    private companion object {
        val ACCOUNT_ID = AccountId("account-observer")
        const val REMOTE_ACCOUNT_ID = "42"
        val NOW = Instant.parse("2026-09-16T10:00:00Z")
    }
}

private fun observerEmptySnapshot(): CompleteProviderImport =
    CompleteProviderImport(
        provider = ProviderKey.ANILIST,
        accountId = ProviderAccountId("42"),
        media = emptyList(),
        entries = emptyList(),
    )
