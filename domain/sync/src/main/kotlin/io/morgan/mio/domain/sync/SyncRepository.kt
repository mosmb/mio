package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.OperationId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

public interface SyncRepository {
    public suspend fun acquireLease(
        ownerToken: String,
        now: Instant,
        expiresAt: Instant,
    ): SyncLease?

    public suspend fun renewLease(
        lease: SyncLease,
        now: Instant,
        expiresAt: Instant,
    ): SyncLease?

    public suspend fun releaseLease(lease: SyncLease)

    public suspend fun claimNext(
        lease: SyncLease,
        now: Instant,
    ): SyncCandidate?

    public suspend fun readRemote(candidate: SyncCandidate): SyncRemoteResult

    public suspend fun writeRemote(
        candidate: SyncCandidate,
        value: OperationValue,
    ): SyncRemoteResult

    public suspend fun commitSnapshot(
        lease: SyncLease,
        entries: List<SyncEntryState>,
        settledOperationIds: Set<OperationId>,
    ): SyncCommitResult

    public suspend fun commitRemoval(
        lease: SyncLease,
        candidate: SyncCandidate,
    ): SyncCommitResult = commitSnapshot(lease, listOf(candidate.current), setOf(candidate.operation.id))

    public suspend fun recordFailure(
        lease: SyncLease,
        candidate: SyncCandidate,
        kind: SyncIssueKind?,
        retryAt: Instant?,
        remote: SyncEntryState?,
    ): SyncCommitResult

    public suspend fun nextWakeAt(
        lease: SyncLease,
        now: Instant,
    ): Instant?

    public suspend fun purgeSettled(
        lease: SyncLease,
        now: Instant,
    )

    public fun observeIssues(): Flow<List<SyncIssue>>
}
