package io.morgan.mio.data.sync

import dev.zacsweers.metro.Inject
import io.morgan.mio.data.sync.local.RoomSyncLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.SyncCandidate
import io.morgan.mio.domain.sync.SyncCommitResult
import io.morgan.mio.domain.sync.SyncEntryState
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncIssueKind
import io.morgan.mio.domain.sync.SyncLease
import io.morgan.mio.domain.sync.SyncRemoteResult
import io.morgan.mio.domain.sync.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Inject
public class DefaultSyncRepository(
    private val accountId: AccountId,
    private val localDataSource: RoomSyncLocalDataSource,
    private val remoteDataSource: ProviderSyncRemoteDataSource,
) : SyncRepository {
    override suspend fun acquireLease(
        ownerToken: String,
        now: Instant,
        expiresAt: Instant,
    ): SyncLease? = localDataSource.acquireLease(accountId, ownerToken, now, expiresAt)

    override suspend fun renewLease(
        lease: SyncLease,
        now: Instant,
        expiresAt: Instant,
    ): SyncLease? = localDataSource.renewLease(lease, now, expiresAt)

    override suspend fun releaseLease(lease: SyncLease) {
        localDataSource.releaseLease(lease)
    }

    override suspend fun claimNext(
        lease: SyncLease,
        now: Instant,
    ): SyncCandidate? = localDataSource.claimNext(lease, now)

    override suspend fun readRemote(candidate: SyncCandidate): SyncRemoteResult = remoteDataSource.read(candidate)

    override suspend fun writeRemote(
        candidate: SyncCandidate,
        value: OperationValue,
    ): SyncRemoteResult = remoteDataSource.write(candidate, value)

    override suspend fun commitSnapshot(
        lease: SyncLease,
        entries: List<SyncEntryState>,
        settledOperationIds: Set<OperationId>,
    ): SyncCommitResult = localDataSource.commitSnapshot(lease, entries, settledOperationIds)

    override suspend fun recordFailure(
        lease: SyncLease,
        candidate: SyncCandidate,
        kind: SyncIssueKind?,
        retryAt: Instant?,
        remote: SyncEntryState?,
    ): SyncCommitResult = localDataSource.recordFailure(lease, candidate, kind, retryAt, remote)

    override suspend fun nextWakeAt(
        lease: SyncLease,
        now: Instant,
    ): Instant? = localDataSource.nextWakeAt(lease, now)

    override suspend fun purgeSettled(
        lease: SyncLease,
        now: Instant,
    ) {
        localDataSource.purgeSettled(lease, now)
    }

    override fun observeIssues(): Flow<List<SyncIssue>> = localDataSource.observeIssues(accountId)
}
