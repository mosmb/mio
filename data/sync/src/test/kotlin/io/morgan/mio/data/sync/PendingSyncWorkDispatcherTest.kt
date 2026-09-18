package io.morgan.mio.data.sync

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncWorkScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PendingSyncWorkDispatcherTest {
    private lateinit var database: MioDatabase

    @Before
    fun setUp() =
        runBlocking {
            database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity(
                    selectionKey = "selected",
                    accountId = ACCOUNT_ID,
                    connectedProvider = StoredConnectedProvider.ANILIST,
                    displayName = "Sync account",
                    avatarReference = null,
                ),
                AccountProviderIdEntity(
                    accountId = ACCOUNT_ID,
                    provider = StoredConnectedProvider.ANILIST,
                    remoteAccountId = "42",
                ),
            )
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onlyCommittedGenerationsAreDispatchedAndAcknowledged() =
        runTest {
            val scheduled = mutableListOf<AccountId>()
            val dispatcher =
                PendingSyncWorkDispatcher(
                    database,
                    SyncWorkScheduler { accountId ->
                        scheduled += accountId
                    },
                )
            dispatcher.start(backgroundScope)
            runCurrent()

            database.inTransaction { database.syncDao().requestDispatch(ACCOUNT_ID) }
            awaitCondition { database.syncDao().getSchedule(ACCOUNT_ID)?.dispatchedGeneration == 1L }

            assertEquals(listOf(AccountId(ACCOUNT_ID)), scheduled)
            assertEquals(1L, database.syncDao().getSchedule(ACCOUNT_ID)?.dispatchedGeneration)
        }

    @Test
    fun enqueueFailureLeavesGenerationUnacknowledgedAndRetriesAfterTheBoundedDelay() =
        runTest {
            val attempts = AtomicInteger()
            val firstAttempt = CompletableDeferred<Unit>()
            val dispatcher =
                PendingSyncWorkDispatcher(
                    database,
                    SyncWorkScheduler { _ ->
                        val attempt = attempts.incrementAndGet()
                        if (attempt == 1) {
                            firstAttempt.complete(Unit)
                            error("test enqueue failure")
                        }
                    },
                )
            dispatcher.start(backgroundScope)
            runCurrent()
            database.inTransaction { database.syncDao().requestDispatch(ACCOUNT_ID) }
            firstAttempt.await()

            assertEquals(1, attempts.get())
            advanceTimeBy(999)
            runCurrent()
            assertEquals(1, attempts.get())
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, attempts.get())
            awaitCondition { database.syncDao().getSchedule(ACCOUNT_ID)?.dispatchedGeneration == 1L }
        }

    @Test
    fun acknowledgementCannotSkipAGenerationRequestedWhileEnqueueIsInFlight() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val generations = mutableListOf<AccountId>()
            val dispatcher =
                PendingSyncWorkDispatcher(
                    database,
                    SyncWorkScheduler { accountId ->
                        generations += accountId
                        if (generations.size == 1) {
                            entered.complete(Unit)
                            release.await()
                        }
                    },
                )
            dispatcher.start(backgroundScope)
            runCurrent()
            database.inTransaction { database.syncDao().requestDispatch(ACCOUNT_ID) }
            awaitCondition { entered.isCompleted }
            entered.await()
            database.inTransaction { database.syncDao().requestDispatch(ACCOUNT_ID) }
            release.complete(Unit)
            awaitCondition { generations.size == 2 }

            assertEquals(listOf(AccountId(ACCOUNT_ID), AccountId(ACCOUNT_ID)), generations)
            val schedule = requireNotNull(database.syncDao().getSchedule(ACCOUNT_ID))
            assertTrue(schedule.requestedGeneration == 2L)
            assertEquals(2L, schedule.dispatchedGeneration)
        }

    private suspend fun TestScope.awaitCondition(condition: suspend () -> Boolean) {
        repeat(100) {
            runCurrent()
            if (condition()) return
            withContext(Dispatchers.Default) { Thread.sleep(5) }
        }
        error("Timed out waiting for dispatcher test condition")
    }

    private companion object {
        const val ACCOUNT_ID: String = "account-dispatch"
    }
}
