package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SynchronizeAccountUseCaseTest {
    private val accountId = AccountId("account")
    private val entryId = CollectionEntryId("entry")
    private val now = Instant.parse("2026-09-11T10:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    @Test
    fun busyLeaseKeepsWorkScheduledForTakeoverInsteadOfReportingSuccess() =
        runTest {
            val repository = RecordingRepository(emptyList(), leaseBusy = true)
            assertEquals(
                SyncRunResult.Retry(now + 120.seconds, SyncRunResult.RetryReason.LEASE_BUSY),
                synchronize(repository),
            )
            assertEquals(0, repository.readCount)
        }

    @Test
    fun foregroundRefreshesWithEmptyOutbox() =
        runTest {
            val repository = RecordingRepository(emptyList())
            val refresh = RecordingRefreshRepository()

            assertEquals(
                SyncRunResult.Complete,
                synchronize(repository, refreshContent = true, refreshRepository = refresh),
            )
            assertEquals(1, refresh.refreshCalls)
            assertTrue(repository.failures.isEmpty())
            assertEquals(0, repository.readCount)
        }

    @Test
    fun workerDefaultDoesNotFetchCollection() =
        runTest {
            val repository = RecordingRepository(emptyList())
            val refresh = RecordingRefreshRepository()

            assertEquals(SyncRunResult.Complete, synchronize(repository, refreshRepository = refresh))
            assertEquals(0, refresh.refreshCalls)
        }

    @Test
    fun transientRefreshStillDrainsPending() =
        runTest {
            val operation = progressOperation(target = 5, state = PendingOperationState.IN_FLIGHT)
            val repository = RecordingRepository(listOf(candidate(operation)))
            val refresh = RecordingRefreshRepository(result = SyncRefreshResult.Transient)

            assertEquals(
                SyncRunResult.Complete,
                synchronize(repository, refreshContent = true, refreshRepository = refresh),
            )
            assertEquals(1, refresh.refreshCalls)
            assertEquals(1, repository.readCount)
            assertEquals(setOf(operation.id), repository.settledOperationIds)
        }

    @Test
    fun authenticationRefreshRecordsIssueAndStopsFurtherTransport() =
        runTest {
            val operation = progressOperation(target = 5, state = PendingOperationState.IN_FLIGHT)
            val repository = RecordingRepository(listOf(candidate(operation)))
            val refresh = RecordingRefreshRepository(result = SyncRefreshResult.Authentication)

            assertEquals(
                SyncRunResult.Complete,
                synchronize(repository, refreshContent = true, refreshRepository = refresh),
            )
            assertEquals(listOf(SyncAccountIssueKind.AUTHENTICATION), refresh.failureKinds)
            assertEquals(0, repository.readCount)
            assertTrue(repository.writes.isEmpty())
        }

    @Test
    fun cancelledRefreshReleasesOwnedLease() =
        runTest {
            val repository = RecordingRepository(emptyList())
            val refresh = RecordingRefreshRepository(cancel = true)
            var cancelled = false

            try {
                synchronize(repository, refreshContent = true, refreshRepository = refresh)
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
            assertTrue(repository.released)
            assertEquals(1, repository.releaseCount)
        }

    @Test
    fun remoteAheadAcknowledgesWithoutWriting() =
        runTest {
            val operation = progressOperation(target = 5, state = PendingOperationState.IN_FLIGHT)
            val repository = RecordingRepository(listOf(candidate(operation)), remoteProgress = 7)

            val result = synchronize(repository)

            assertEquals(SyncRunResult.Complete, result)
            assertTrue(repository.writes.isEmpty())
            assertEquals(setOf(operation.id), repository.settledOperationIds)
            assertEquals(1, repository.commitCount)
        }

    @Test
    fun absoluteTargetIsWrittenAndSettled() =
        runTest {
            val operation = progressOperation(target = 5, state = PendingOperationState.IN_FLIGHT)
            val repository = RecordingRepository(listOf(candidate(operation)), remoteProgress = 4)

            val result = synchronize(repository)

            assertEquals(SyncRunResult.Complete, result)
            assertEquals(listOf(OperationValue.Progress(ProgressValue(5))), repository.writes)
            assertEquals(setOf(operation.id), repository.settledOperationIds)
        }

    @Test
    fun transientFailurePersistsBoundedRetryWithoutSleeping() =
        runTest {
            val operation = progressOperation(target = 5, state = PendingOperationState.IN_FLIGHT)
            val repository =
                RecordingRepository(
                    candidates = listOf(candidate(operation)),
                    remoteResult = SyncRemoteResult.Transient(),
                )

            val result = synchronize(repository)

            assertEquals(SyncRunResult.Retry(now + 30.seconds), result)
            assertEquals(1, repository.failures.size)
            assertEquals(null, repository.failures.single().kind)
            assertEquals(now + 30.seconds, repository.failures.single().retryAt)
            assertTrue(repository.writes.isEmpty())
        }

    @Test
    fun heartbeatRenewsLeaseDuringAProviderCall() =
        runTest {
            val operation = progressOperation(target = 5, state = PendingOperationState.IN_FLIGHT)
            val repository =
                RecordingRepository(
                    candidates = listOf(candidate(operation)),
                    remoteDelay = 31.seconds,
                )

            assertEquals(SyncRunResult.Complete, synchronize(repository))
            assertTrue(repository.renewals.isNotEmpty())
            assertEquals("owner", repository.renewals.first().ownerToken)
            assertTrue(repository.released)
            assertEquals(1, repository.releaseCount)
        }

    @Test
    fun cancellationRethrowsAndReleasesTheOwnedLease() =
        runTest {
            val operation = progressOperation(target = 5, state = PendingOperationState.IN_FLIGHT)
            val repository =
                RecordingRepository(
                    candidates = listOf(candidate(operation)),
                    cancelRead = true,
                )
            var cancelled = false
            try {
                synchronize(repository)
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
            assertEquals(1, repository.releaseCount)
        }

    @Test
    fun fifthAttemptStillCallsProviderAndCanSucceed() =
        runTest {
            val operation = progressOperation(5, PendingOperationState.IN_FLIGHT, attemptCount = 5)
            val repository = RecordingRepository(listOf(candidate(operation)))
            assertEquals(SyncRunResult.Complete, synchronize(repository))
            assertEquals(1, repository.writes.size)
            assertEquals(1, repository.commitCount)
        }

    @Test
    fun fifthTransientAttemptPersistsExhaustionWithoutAnotherRetry() =
        runTest {
            val operation = progressOperation(5, PendingOperationState.IN_FLIGHT, attemptCount = 5)
            val repository =
                RecordingRepository(listOf(candidate(operation)), remoteResult = SyncRemoteResult.Transient())
            assertEquals(SyncRunResult.Complete, synchronize(repository))
            assertEquals(SyncIssueKind.RETRY_EXHAUSTED, repository.failures.single().kind)
            assertEquals(null, repository.failures.single().retryAt)
        }

    @Test
    fun abandonedFifthAttemptDoesNotPerformASixthRequest() =
        runTest {
            val operation = progressOperation(5, PendingOperationState.IN_FLIGHT, attemptCount = 5)
            val repository = RecordingRepository(listOf(candidate(operation).copy(attemptBudgetExhausted = true)))
            assertEquals(SyncRunResult.Complete, synchronize(repository))
            assertEquals(0, repository.readCount)
            assertEquals(SyncIssueKind.RETRY_EXHAUSTED, repository.failures.single().kind)
        }

    @Test
    fun successfulHttpWithUnchangedReadBackDoesNotDiscardIntent() =
        runTest {
            val repository =
                RecordingRepository(
                    listOf(candidate(progressOperation(5, PendingOperationState.IN_FLIGHT))),
                    writeProgress = 4,
                )
            synchronize(repository)
            assertEquals(0, repository.commitCount)
            assertEquals(SyncIssueKind.CONFLICT, repository.failures.single().kind)
        }

    @Test
    fun outerTimeoutPropagatesInsteadOfBeingPersistedAsProviderFailure() =
        runTest {
            val repository =
                RecordingRepository(
                    listOf(candidate(progressOperation(5, PendingOperationState.IN_FLIGHT))),
                    remoteDelay = 31.seconds,
                )
            val result = kotlinx.coroutines.withTimeoutOrNull(10.seconds) { synchronize(repository) }
            assertEquals(null, result)
            assertTrue(repository.failures.isEmpty())
            assertEquals(1, repository.releaseCount)
        }

    private suspend fun synchronize(
        repository: RecordingRepository,
        refreshContent: Boolean = false,
        refreshRepository: RecordingRefreshRepository = RecordingRefreshRepository(),
    ): SyncRunResult =
        SynchronizeAccountUseCase(
            repository = repository,
            clock = clock,
            reconcile = ReconcilePendingOperationUseCase(),
            refreshRepository = refreshRepository,
            newLeaseOwner = { "owner" },
        )(refreshContent = refreshContent)

    private fun candidate(operation: PendingOperation): SyncCandidate =
        SyncCandidate(
            operation = operation,
            current = state(progress = 4),
            groupOrder = 1L,
        )

    private fun progressOperation(
        target: Int,
        state: PendingOperationState,
        attemptCount: Int = 1,
    ): PendingOperation =
        PendingOperation(
            id = OperationId("operation"),
            groupId = OperationGroupId("group"),
            accountId = accountId,
            collectionEntryId = entryId,
            kind = PendingOperationKind.SET_PROGRESS_FORWARD,
            base = OperationValue.Progress(ProgressValue(4)),
            target = OperationValue.Progress(ProgressValue(target)),
            positionInGroup = 0,
            state = state,
            attemptCount = attemptCount,
            lastAttemptAt = now,
        )

    private fun state(progress: Int): SyncEntryState =
        SyncEntryState(
            accountId = accountId,
            entryId = entryId,
            unit = ProgressUnit.EPISODE,
            progress = ProgressValue(progress),
            status = CollectionStatus.CURRENT,
            total = UnitTotal(12),
            updatedAt = now,
        )

    private inner class RecordingRepository(
        candidates: List<SyncCandidate>,
        private val remoteProgress: Int = 4,
        private val remoteResult: SyncRemoteResult? = null,
        private val remoteDelay: kotlin.time.Duration = kotlin.time.Duration.ZERO,
        private val cancelRead: Boolean = false,
        private val writeProgress: Int? = null,
        private val leaseBusy: Boolean = false,
    ) : SyncRepository {
        private val candidates = ArrayDeque(candidates)
        val writes = mutableListOf<OperationValue>()
        val settledOperationIds = mutableSetOf<OperationId>()
        val failures = mutableListOf<Failure>()
        val renewals = mutableListOf<SyncLease>()
        var readCount: Int = 0
        var commitCount: Int = 0
        var releaseCount: Int = 0
        var released: Boolean = false

        override suspend fun acquireLease(
            ownerToken: String,
            now: Instant,
            expiresAt: Instant,
        ): SyncLease? = if (leaseBusy) null else SyncLease(accountId, ownerToken, expiresAt)

        override suspend fun renewLease(
            lease: SyncLease,
            now: Instant,
            expiresAt: Instant,
        ): SyncLease {
            val renewed = lease.copy(expiresAt = expiresAt)
            renewals += renewed
            return renewed
        }

        override suspend fun releaseLease(lease: SyncLease) {
            releaseCount += 1
            released = true
        }

        override suspend fun claimNext(
            lease: SyncLease,
            now: Instant,
        ): SyncCandidate? = candidates.removeFirstOrNull()

        override suspend fun readRemote(candidate: SyncCandidate): SyncRemoteResult {
            readCount += 1
            if (cancelRead) throw CancellationException("test cancellation")
            if (remoteDelay > kotlin.time.Duration.ZERO) delay(remoteDelay)
            return remoteResult ?: SyncRemoteResult.Success(state(remoteProgress))
        }

        override suspend fun writeRemote(
            candidate: SyncCandidate,
            value: OperationValue,
        ): SyncRemoteResult {
            writes += value
            return SyncRemoteResult.Success(
                state(
                    progress = writeProgress ?: (value as OperationValue.Progress).value.value,
                ),
            )
        }

        override suspend fun commitSnapshot(
            lease: SyncLease,
            entries: List<SyncEntryState>,
            settledOperationIds: Set<OperationId>,
        ): SyncCommitResult {
            commitCount += 1
            this.settledOperationIds += settledOperationIds
            return SyncCommitResult.Applied
        }

        override suspend fun recordFailure(
            lease: SyncLease,
            candidate: SyncCandidate,
            kind: SyncIssueKind?,
            retryAt: Instant?,
            remote: SyncEntryState?,
        ): SyncCommitResult {
            failures += Failure(kind, retryAt)
            return SyncCommitResult.Applied
        }

        override suspend fun nextWakeAt(
            lease: SyncLease,
            now: Instant,
        ): Instant? = null

        override suspend fun purgeSettled(
            lease: SyncLease,
            now: Instant,
        ) = Unit

        override fun observeIssues() = kotlinx.coroutines.flow.emptyFlow<List<SyncIssue>>()
    }

    private data class Failure(
        val kind: SyncIssueKind?,
        val retryAt: Instant?,
    )

    private class RecordingRefreshRepository(
        private val blocked: Boolean = false,
        private val result: SyncRefreshResult = SyncRefreshResult.Success,
        private val cancel: Boolean = false,
    ) : RefreshRepository {
        var refreshCalls: Int = 0
        val failureKinds = mutableListOf<SyncAccountIssueKind>()

        override suspend fun isAuthenticationBlocked(): Boolean = blocked

        override suspend fun refresh(lease: SyncLease): SyncRefreshResult {
            refreshCalls += 1
            if (cancel) throw CancellationException("refresh cancellation")
            return result
        }

        override suspend fun recordFailure(
            lease: SyncLease,
            kind: SyncAccountIssueKind,
        ): SyncCommitResult {
            failureKinds += kind
            return SyncCommitResult.Applied
        }
    }
}
