package io.morgan.mio.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.ReconcilePendingOperationUseCase
import io.morgan.mio.domain.sync.RefreshRepository
import io.morgan.mio.domain.sync.SyncAccountIssueKind
import io.morgan.mio.domain.sync.SyncAccountSession
import io.morgan.mio.domain.sync.SyncAccountSessionFactory
import io.morgan.mio.domain.sync.SyncCandidate
import io.morgan.mio.domain.sync.SyncCommitResult
import io.morgan.mio.domain.sync.SyncEntryState
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncIssueKind
import io.morgan.mio.domain.sync.SyncLease
import io.morgan.mio.domain.sync.SyncRefreshResult
import io.morgan.mio.domain.sync.SyncRemoteResult
import io.morgan.mio.domain.sync.SyncRepository
import io.morgan.mio.domain.sync.SyncRunResult
import io.morgan.mio.domain.sync.SynchronizeAccountUseCase
import io.morgan.mio.sync.work.factory.PendingSyncWorkerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PendingSyncWorkerTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun missingOrInvalidAccountInputFailsWithoutOpeningSession() =
        runTest {
            var opens = 0
            val factory =
                SyncAccountSessionFactory {
                    opens += 1
                    null
                }

            assertEquals(ListenableWorker.Result.failure(), worker(factory, Data.EMPTY).doWork())
            assertEquals(
                ListenableWorker.Result.failure(),
                worker(factory, Data.Builder().putString(PendingSyncWorker.ACCOUNT_ID_KEY, " ").build()).doWork(),
            )
            assertEquals(0, opens)
        }

    @Test
    fun validAccountIsPassedToAReconstructedSessionAndEmptySessionCompletes() =
        runTest {
            var opened: AccountId? = null
            val factory =
                SyncAccountSessionFactory { accountId ->
                    opened = accountId
                    null
                }

            assertEquals(
                ListenableWorker.Result.success(),
                worker(
                    factory,
                    Data.Builder().putString(PendingSyncWorker.ACCOUNT_ID_KEY, "account-1").build(),
                ).doWork(),
            )
            assertEquals(AccountId("account-1"), opened)
        }

    @Test
    fun reconstructedSessionMapsCompleteAndRetryOutcomes() =
        runTest {
            val completeSession = session(SyncRunResult.Complete)
            val retrySession = session(SyncRunResult.Retry(Clock.System.now()))
            val completeFactory = SyncAccountSessionFactory { completeSession }
            val retryFactory = SyncAccountSessionFactory { retrySession }
            val input = Data.Builder().putString(PendingSyncWorker.ACCOUNT_ID_KEY, "account-1").build()

            assertEquals(ListenableWorker.Result.success(), worker(completeFactory, input).doWork())
            assertEquals(ListenableWorker.Result.retry(), worker(retryFactory, input).doWork())
        }

    @Test
    fun configurationFailureIsTerminalAndCancellationIsRethrown() =
        runTest {
            val input = Data.Builder().putString(PendingSyncWorker.ACCOUNT_ID_KEY, "account-1").build()
            val configuration =
                io.morgan.mio.domain.sync.SyncInfrastructureException(
                    io.morgan.mio.domain.sync.SyncInfrastructureException.Kind.CONFIGURATION,
                )
            assertEquals(
                ListenableWorker.Result.failure(),
                worker(SyncAccountSessionFactory { throw configuration }, input).doWork(),
            )
            var cancellation: CancellationException? = null
            try {
                worker(SyncAccountSessionFactory { throw CancellationException("test") }, input).doWork()
            } catch (exception: CancellationException) {
                cancellation = exception
            }
            assertEquals("test", cancellation?.message)
        }

    @Test
    fun workerFactoryClaimsOnlyThePendingSyncWorkerClass() {
        val factory = PendingSyncWorkerFactory(SyncAccountSessionFactory { null })
        val parameters = workerParameters(Data.EMPTY)

        assertTrue(
            factory.createWorker(context, PendingSyncWorker::class.java.name, parameters) is PendingSyncWorker,
        )
        assertNull(factory.createWorker(context, "other.worker", parameters))
    }

    private fun worker(
        factory: SyncAccountSessionFactory,
        input: Data,
    ): PendingSyncWorker = PendingSyncWorker(context, workerParameters(input), factory)

    private fun session(result: SyncRunResult): SyncAccountSession =
        object : SyncAccountSession {
            override val synchronize =
                SynchronizeAccountUseCase(
                    repository = RepositoryForResult(result),
                    clock = Clock.System,
                    reconcile = ReconcilePendingOperationUseCase(),
                    refreshRepository = WorkerRefreshRepository(),
                    newLeaseOwner = { "test-owner" },
                )

            override fun close() = Unit
        }

    @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST", "USELESS_CAST")
    private fun workerParameters(input: Data): WorkerParameters {
        val worker =
            TestListenableWorkerBuilder
                .from(context, ParameterWorker::class.java)
                .setInputData(input)
                .build()
        val field = ListenableWorker::class.java.getDeclaredField("mWorkerParams")
        field.isAccessible = true
        return field.get(worker) as WorkerParameters
    }

    public class ParameterWorker(
        context: Context,
        parameters: WorkerParameters,
    ) : Worker(context, parameters) {
        override fun doWork(): Result = Result.success()
    }

    private class RepositoryForResult(
        private val result: SyncRunResult,
    ) : SyncRepository {
        private val lease =
            SyncLease(
                AccountId("account-1"),
                "test-owner",
                Clock.System.now() +
                    1.hours,
            )

        override suspend fun acquireLease(
            ownerToken: String,
            now: kotlin.time.Instant,
            expiresAt: kotlin.time.Instant,
        ) = lease.copy(ownerToken = ownerToken, expiresAt = expiresAt)

        override suspend fun renewLease(
            lease: SyncLease,
            now: kotlin.time.Instant,
            expiresAt: kotlin.time.Instant,
        ) = lease.copy(expiresAt = expiresAt)

        override suspend fun releaseLease(lease: SyncLease) = Unit

        override suspend fun claimNext(
            lease: SyncLease,
            now: kotlin.time.Instant,
        ): SyncCandidate? = null

        override suspend fun readRemote(candidate: SyncCandidate): SyncRemoteResult = error("unreachable")

        override suspend fun writeRemote(
            candidate: SyncCandidate,
            value: OperationValue,
        ): SyncRemoteResult = error("unreachable")

        override suspend fun commitSnapshot(
            lease: SyncLease,
            entries: List<SyncEntryState>,
            settledOperationIds: Set<OperationId>,
        ): SyncCommitResult = error("unreachable")

        override suspend fun recordFailure(
            lease: SyncLease,
            candidate: SyncCandidate,
            kind: SyncIssueKind?,
            retryAt: kotlin.time.Instant?,
            remote: SyncEntryState?,
        ): SyncCommitResult = error("unreachable")

        override suspend fun nextWakeAt(
            lease: SyncLease,
            now: kotlin.time.Instant,
        ) = if (result is SyncRunResult.Retry) result.notBefore else null

        override suspend fun purgeSettled(
            lease: SyncLease,
            now: kotlin.time.Instant,
        ) = Unit

        override fun observeIssues(): Flow<List<SyncIssue>> = emptyFlow()
    }

    private class WorkerRefreshRepository : RefreshRepository {
        override suspend fun isAuthenticationBlocked(): Boolean = false

        override suspend fun refresh(lease: SyncLease): SyncRefreshResult = SyncRefreshResult.Success

        override suspend fun recordFailure(
            lease: SyncLease,
            kind: SyncAccountIssueKind,
        ): SyncCommitResult = SyncCommitResult.Applied
    }
}
