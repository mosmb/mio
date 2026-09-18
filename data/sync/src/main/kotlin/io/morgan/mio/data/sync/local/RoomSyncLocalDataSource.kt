package io.morgan.mio.data.sync.local

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.SyncIssueEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.sync.mapper.toDomain
import io.morgan.mio.data.sync.mapper.toRemoteSyncEntryState
import io.morgan.mio.data.sync.mapper.toSyncEntryState
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.sync.PendingOperation
import io.morgan.mio.domain.sync.PendingOperationKind
import io.morgan.mio.domain.sync.ProjectSyncEntryUseCase
import io.morgan.mio.domain.sync.SyncCandidate
import io.morgan.mio.domain.sync.SyncCommitResult
import io.morgan.mio.domain.sync.SyncEntryState
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncIssueKind
import io.morgan.mio.domain.sync.SyncLease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Inject
public class RoomSyncLocalDataSource(
    private val database: MioDatabase,
    private val project: ProjectSyncEntryUseCase,
    private val clock: Clock,
) {
    public suspend fun acquireLease(
        accountId: AccountId,
        ownerToken: String,
        now: Instant,
        expiresAt: Instant,
    ): SyncLease? =
        storage {
            database.inTransaction {
                if (database.syncDao().getSelectedAccount()?.accountId != accountId.value) return@inTransaction null
                database.syncDao().tryAcquireLease(accountId.value, ownerToken, now, expiresAt)
                database
                    .syncDao()
                    .getLease(accountId.value)
                    ?.takeIf { lease ->
                        lease.ownerToken == ownerToken &&
                            lease.expiresAtUtcMs.toEpochMilliseconds() == expiresAt.toEpochMilliseconds()
                    }?.toDomain()
            }
        }

    public suspend fun renewLease(
        lease: SyncLease,
        now: Instant,
        expiresAt: Instant,
    ): SyncLease? =
        storage {
            database.inTransaction {
                if (!ownsLease(lease, now)) return@inTransaction null
                database.syncDao().renewLease(lease.accountId.value, lease.ownerToken, now, expiresAt)
                database
                    .syncDao()
                    .getLease(lease.accountId.value)
                    ?.takeIf { value ->
                        value.ownerToken == lease.ownerToken &&
                            value.expiresAtUtcMs.toEpochMilliseconds() == expiresAt.toEpochMilliseconds()
                    }?.toDomain()
            }
        }

    public suspend fun releaseLease(lease: SyncLease) {
        storage {
            database.inTransaction {
                database.syncDao().releaseLease(lease.accountId.value, lease.ownerToken, clock.now())
            }
        }
    }

    public suspend fun claimNext(
        lease: SyncLease,
        now: Instant,
    ): SyncCandidate? =
        storage {
            database.inTransaction {
                if (!ownsLease(lease, now)) return@inTransaction null
                var result: SyncCandidate? = null
                var finished = false
                while (!finished && result == null) {
                    val entity =
                        database.syncDao().getNextCandidate(
                            accountId = lease.accountId.value,
                            nowUtcMs = now,
                            abandonedBeforeUtcMs = now - LEASE_TTL,
                        )
                    if (entity == null) {
                        finished = true
                        continue
                    }
                    val group =
                        database.syncDao().getGroup(entity.operationGroupId)
                            ?: throw IllegalStateException("Pending operation group is missing")
                    if (
                        entity.lifecycleState == StoredPendingOperationState.IN_FLIGHT &&
                        group.compensatesGroupId == null &&
                        database
                            .syncDao()
                            .getCompensatingGroups(
                                lease.accountId.value,
                                group.operationGroupId,
                            ).isNotEmpty()
                    ) {
                        database.syncDao().setOperationState(
                            accountId = lease.accountId.value,
                            operationId = entity.operationId,
                            state = StoredPendingOperationState.CANCELLED,
                            retryNotBeforeUtcMs = null,
                        )
                        continue
                    }
                    if (entity.attemptCount < MAX_ATTEMPTS) {
                        if (
                            database.syncDao().claimOperation(
                                accountId = lease.accountId.value,
                                operationId = entity.operationId,
                                nowUtcMs = now,
                                abandonedBeforeUtcMs = now - LEASE_TTL,
                            ) != 1
                        ) {
                            continue
                        }
                    }
                    val claimed =
                        database.syncDao().getOperation(entity.operationId)
                            ?: throw IllegalStateException("Claimed pending operation disappeared")
                    val entry = database.syncDao().getEntry(claimed.collectionEntryId)
                    if (entry == null || entry.accountId != lease.accountId.value) {
                        finished = true
                        continue
                    }
                    val dependencies =
                        database
                            .syncDao()
                            .getDependenciesForOperation(claimed.operationId)
                            .mapTo(linkedSetOf()) { dependency -> OperationId(dependency.dependsOnOperationId) }
                    val operation = claimed.toDomain(dependencies)
                    result =
                        SyncCandidate(
                            operation = operation,
                            attemptBudgetExhausted = entity.attemptCount >= MAX_ATTEMPTS,
                            current = entry.toSyncEntryState(),
                            groupOrder = group.groupOrder,
                            isAutomaticCompletion = isAutomaticCompletion(operation, group.operationGroupId),
                            correctionProgress = correctionProgress(operation, group.operationGroupId),
                            compensatesGroupId = group.compensatesGroupId?.let(::OperationGroupId),
                        )
                }
                result
            }
        }

    public suspend fun commitSnapshot(
        lease: SyncLease,
        entries: List<SyncEntryState>,
        settledOperationIds: Set<OperationId>,
    ): SyncCommitResult =
        fencedCommit {
            database.inTransaction {
                val now = clock.now()
                if (!ownsLease(lease, now)) return@inTransaction SyncCommitResult.LeaseLost
                if (database.syncDao().getSelectedAccount()?.accountId != lease.accountId.value) {
                    return@inTransaction SyncCommitResult.AccountGone
                }
                require(entries.map { entry -> entry.entryId }.distinct().size == entries.size)
                val rows =
                    entries.map { incoming ->
                        require(incoming.accountId == lease.accountId)
                        val row =
                            database.syncDao().getEntry(incoming.entryId.value)
                                ?: return@inTransaction SyncCommitResult.AccountGone
                        require(row.accountId == lease.accountId.value)
                        require(row.progressUnit == incoming.unit.toStoredUnit())
                        incoming to row
                    }
                val settledOperations =
                    settledOperationIds.map { operationId ->
                        val operation =
                            database.syncDao().getOperation(operationId.value)
                                ?: throw IllegalStateException("Settled operation disappeared")
                        require(operation.accountId == lease.accountId.value)
                        require(operation.collectionEntryId in entries.map { entry -> entry.entryId.value })
                        if (operation.lifecycleState == StoredPendingOperationState.IN_FLIGHT) {
                            check(
                                database.syncDao().setOperationState(
                                    accountId = lease.accountId.value,
                                    operationId = operation.operationId,
                                    state = StoredPendingOperationState.DELIVERED,
                                    retryNotBeforeUtcMs = null,
                                ) == 1,
                            )
                        } else {
                            require(
                                operation.lifecycleState == StoredPendingOperationState.DELIVERED ||
                                    operation.lifecycleState == StoredPendingOperationState.CANCELLED,
                            )
                        }
                        operation
                    }
                rows.forEach { (incoming, row) ->
                    val storedRemote = row.toRemoteSyncEntryState()
                    val remote = if (incoming.updatedAt >= row.remoteUpdatedAtUtcMs) incoming else storedRemote
                    val current = row.toSyncEntryState()
                    val pending = pendingForEntry(lease.accountId.value, row.collectionEntryId)
                    val effective = project(remote = remote, current = current, pending = pending)
                    check(
                        database.syncDao().updateEntrySnapshot(
                            entryId = row.collectionEntryId,
                            accountId = lease.accountId.value,
                            mediaId = row.mediaId,
                            remoteStatus = remote.status.toStoredStatus(),
                            remoteProgress = remote.progress.value,
                            remoteTotal = remote.total?.value,
                            remoteUpdatedAtUtcMs = remote.updatedAt,
                            effectiveStatus = effective.status.toStoredStatus(),
                            effectiveProgress = effective.progress.value,
                            effectiveTotal = effective.total?.value,
                            effectiveUpdatedAtUtcMs = effective.updatedAt,
                            localProgressRevision = effective.revision,
                        ) == 1,
                    )
                }
                settledOperations.forEach { operation ->
                    database.syncDao().deleteIssue(lease.accountId.value, "${operation.operationId}:sync")
                    val group = database.syncDao().getGroup(operation.operationGroupId)
                    val originalGroupId = group?.compensatesGroupId ?: return@forEach
                    database.syncDao().getOperationsForGroup(originalGroupId).forEach { original ->
                        database.syncDao().deleteIssue(lease.accountId.value, "${original.operationId}:sync")
                    }
                }
                if (!ownsLease(lease, clock.now())) throw LeaseExpiredDuringCommit()
                SyncCommitResult.Applied
            }
        }

    public suspend fun recordFailure(
        lease: SyncLease,
        candidate: SyncCandidate,
        kind: SyncIssueKind?,
        retryAt: Instant?,
        remote: SyncEntryState?,
    ): SyncCommitResult =
        fencedCommit {
            database.inTransaction {
                val now = clock.now()
                if (!ownsLease(lease, now)) return@inTransaction SyncCommitResult.LeaseLost
                val row =
                    database.syncDao().getEntry(candidate.operation.collectionEntryId.value)
                        ?: return@inTransaction SyncCommitResult.AccountGone
                require(row.accountId == lease.accountId.value)
                val operationEntity =
                    database.syncDao().getOperation(candidate.operation.id.value)
                        ?: return@inTransaction SyncCommitResult.AccountGone
                if (operationEntity.lifecycleState == StoredPendingOperationState.CANCELLED) {
                    return@inTransaction SyncCommitResult.Applied
                }
                require(operationEntity.lifecycleState == StoredPendingOperationState.IN_FLIGHT)
                require((kind == null) xor (retryAt == null))
                if (retryAt != null) require(retryAt > now)
                val operationGroup = database.syncDao().getGroup(operationEntity.operationGroupId)
                val hasCompensation =
                    operationGroup?.compensatesGroupId == null &&
                        database
                            .syncDao()
                            .getCompensatingGroups(
                                lease.accountId.value,
                                operationEntity.operationGroupId,
                            ).isNotEmpty()
                val state =
                    if (hasCompensation) {
                        StoredPendingOperationState.CANCELLED
                    } else if (kind == null) {
                        StoredPendingOperationState.RETRYABLE_FAILURE
                    } else {
                        StoredPendingOperationState.TERMINAL_FAILURE
                    }
                check(
                    database.syncDao().setOperationState(
                        accountId = lease.accountId.value,
                        operationId = operationEntity.operationId,
                        state = state,
                        retryNotBeforeUtcMs = retryAt.takeUnless { hasCompensation },
                    ) == 1,
                )
                if (kind != null) {
                    val operation =
                        operationEntity.toDomain(
                            database
                                .syncDao()
                                .getDependenciesForOperation(operationEntity.operationId)
                                .mapTo(linkedSetOf()) { dependency -> OperationId(dependency.dependsOnOperationId) },
                        )
                    val local = row.toSyncEntryState()
                    database.syncDao().upsertIssue(
                        SyncIssueEntity(
                            issueId = "${operation.id.value}:sync",
                            accountId = lease.accountId.value,
                            collectionEntryId = operation.collectionEntryId.value,
                            operationGroupId = operation.groupId.value,
                            operationId = operation.id.value,
                            kind = kind.toSerializedValue(),
                            baseProgress = operation.base.progressValueOrNull(),
                            baseStatus = operation.base.statusValueOrNull()?.toStoredStatus(),
                            targetProgress = operation.target.progressValueOrNull(),
                            targetStatus = operation.target.statusValueOrNull()?.toStoredStatus(),
                            progressUnit = local.unit.toStoredUnit(),
                            localProgress = local.progress.value,
                            localStatus = local.status.toStoredStatus(),
                            localTotal = local.total?.value,
                            localUpdatedAtUtcMs = local.updatedAt,
                            localRevision = local.revision,
                            remoteProgress = remote?.progress?.value,
                            remoteStatus = remote?.status?.toStoredStatus(),
                            remoteTotal = remote?.total?.value,
                            remoteUpdatedAtUtcMs = remote?.updatedAt,
                            detectedAtUtcMs = now,
                        ),
                    )
                }
                if (!ownsLease(lease, clock.now())) throw LeaseExpiredDuringCommit()
                SyncCommitResult.Applied
            }
        }

    public suspend fun nextWakeAt(
        lease: SyncLease,
        now: Instant,
    ): Instant? =
        storage {
            database.inTransaction {
                if (!ownsLease(lease, now)) return@inTransaction null
                listOfNotNull(
                    database.syncDao().getNextRetryAt(lease.accountId.value),
                    database.syncDao().getNextUndoDeadline(lease.accountId.value, now),
                    database.syncDao().getOldestInFlightAttempt(lease.accountId.value)?.plus(LEASE_TTL),
                ).filter { value -> value > now }.minOrNull()
            }
        }

    public suspend fun purgeSettled(
        lease: SyncLease,
        now: Instant,
    ) {
        storage {
            database.inTransaction {
                if (!ownsLease(lease, now)) return@inTransaction
                var deleted: Boolean
                do {
                    deleted = false
                    database.syncDao().getGroups(lease.accountId.value).forEach { group ->
                        val operations = database.syncDao().getOperationsForGroup(group.operationGroupId)
                        if (operations.isEmpty() ||
                            operations.any { operation -> !operation.lifecycleState.isSettled() }
                        ) {
                            return@forEach
                        }
                        if (database.pendingOperationDao().countExternalDependents(group.operationGroupId) !=
                            0
                        ) {
                            return@forEach
                        }
                        if (database
                                .syncDao()
                                .getIssuesForGroup(
                                    lease.accountId.value,
                                    group.operationGroupId,
                                ).isNotEmpty()
                        ) {
                            return@forEach
                        }
                        val activity = database.syncDao().getActivity(lease.accountId.value, group.operationGroupId)
                        if (activity != null && activity.undoneAtUtcMs == null && now < activity.undoUntilUtcMs) {
                            return@forEach
                        }
                        if (group.compensatesGroupId == null &&
                            database
                                .syncDao()
                                .getCompensatingGroups(lease.accountId.value, group.operationGroupId)
                                .any { compensation ->
                                    database
                                        .syncDao()
                                        .getOperationsForGroup(compensation.operationGroupId)
                                        .any { operation -> !operation.lifecycleState.isSettled() }
                                }
                        ) {
                            return@forEach
                        }
                        if (database.syncDao().deleteSettledGroup(lease.accountId.value, group.operationGroupId) ==
                            1
                        ) {
                            deleted = true
                        }
                    }
                } while (deleted)
            }
        }
    }

    public fun observeIssues(accountId: AccountId): Flow<List<SyncIssue>> =
        database
            .syncDao()
            .observeIssuesForAccount(accountId.value)
            .map { entities ->
                entities.map { entity -> entity.toDomain() }
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                if (throwable is SQLiteException) {
                    throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
                }
                throw throwable
            }

    private class LeaseExpiredDuringCommit : RuntimeException()

    private suspend fun fencedCommit(block: suspend () -> SyncCommitResult): SyncCommitResult =
        try {
            storage(block)
        } catch (_: LeaseExpiredDuringCommit) {
            SyncCommitResult.LeaseLost
        }

    private suspend fun <T> storage(block: suspend () -> T): T =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }

    private suspend fun ownsLease(
        lease: SyncLease,
        now: Instant,
    ): Boolean {
        val selected = database.syncDao().getSelectedAccount()
        val stored = database.syncDao().getLease(lease.accountId.value)
        return selected?.accountId == lease.accountId.value &&
            stored?.ownerToken == lease.ownerToken &&
            stored.expiresAtUtcMs > now
    }

    private suspend fun pendingForEntry(
        accountId: String,
        entryId: String,
    ): List<PendingOperation> =
        database
            .syncDao()
            .getOrderedOperations(accountId)
            .filter { entity -> entity.collectionEntryId == entryId }
            .map { entity ->
                entity.toDomain(
                    database
                        .syncDao()
                        .getDependenciesForOperation(entity.operationId)
                        .mapTo(linkedSetOf()) { dependency -> OperationId(dependency.dependsOnOperationId) },
                )
            }

    private suspend fun correctionProgress(
        operation: PendingOperation,
        groupId: String,
    ): io.morgan.mio.domain.model.ProgressValue? {
        if (operation.kind != PendingOperationKind.SET_STATUS) return null
        val activity = database.syncDao().getActivity(operation.accountId.value, groupId) ?: return null
        if (activity.direction != "correction" ||
            activity.beforeStatus != StoredCollectionStatus.COMPLETED ||
            activity.afterStatus != StoredCollectionStatus.CURRENT
        ) {
            return null
        }
        val predecessor =
            database.syncDao().getOperationsForGroup(groupId).firstOrNull { pending ->
                pending.collectionEntryId == operation.collectionEntryId.value &&
                    pending.positionInGroup < operation.positionInGroup &&
                    pending.kind == StoredPendingOperationKind.SET_PROGRESS_CORRECTION &&
                    pending.targetProgress == activity.afterProgress &&
                    OperationId(pending.operationId) in operation.dependsOn
            } ?: return null
        return predecessor.targetProgress?.let {
            io.morgan.mio.domain.model
                .ProgressValue(it)
        }
    }

    private suspend fun isAutomaticCompletion(
        operation: PendingOperation,
        groupId: String,
    ): Boolean {
        if (operation.kind != PendingOperationKind.SET_STATUS) return false
        val base = operation.base as? io.morgan.mio.domain.sync.OperationValue.Status ?: return false
        val target = operation.target as? io.morgan.mio.domain.sync.OperationValue.Status ?: return false
        val activity = database.syncDao().getActivity(operation.accountId.value, groupId) ?: return false
        return activity.afterStatus == StoredCollectionStatus.COMPLETED &&
            base.value == io.morgan.mio.domain.collection.CollectionStatus.CURRENT &&
            target.value == io.morgan.mio.domain.collection.CollectionStatus.COMPLETED &&
            database.syncDao().getOperationsForGroup(groupId).any { predecessor ->
                predecessor.collectionEntryId == operation.collectionEntryId.value &&
                    predecessor.positionInGroup < operation.positionInGroup &&
                    predecessor.kind in
                    setOf(
                        StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                        StoredPendingOperationKind.SET_PROGRESS_CORRECTION,
                    ) &&
                    predecessor.targetProgress == activity.afterProgress
            }
    }

    private fun SyncIssueKind.toSerializedValue(): String =
        when (this) {
            SyncIssueKind.AUTHENTICATION -> "authentication"
            SyncIssueKind.REJECTED -> "rejected"
            SyncIssueKind.CONFLICT -> "conflict"
            SyncIssueKind.RETRY_EXHAUSTED -> "retry_exhausted"
            SyncIssueKind.INVALID_REMOTE_DATA -> "invalid_remote_data"
        }

    private fun io.morgan.mio.domain.sync.OperationValue.progressValueOrNull(): Int? =
        (this as? io.morgan.mio.domain.sync.OperationValue.Progress)?.value?.value

    private fun io.morgan.mio.domain.sync.OperationValue.statusValueOrNull():
        io.morgan.mio.domain.collection.CollectionStatus? =
        (this as? io.morgan.mio.domain.sync.OperationValue.Status)?.value

    private fun io.morgan.mio.domain.model.ProgressUnit.toStoredUnit(): StoredProgressUnit =
        when (this) {
            io.morgan.mio.domain.model.ProgressUnit.EPISODE -> StoredProgressUnit.EPISODE
            io.morgan.mio.domain.model.ProgressUnit.CHAPTER -> StoredProgressUnit.CHAPTER
            io.morgan.mio.domain.model.ProgressUnit.VOLUME -> StoredProgressUnit.VOLUME
        }

    private fun io.morgan.mio.domain.collection.CollectionStatus.toStoredStatus(): StoredCollectionStatus =
        when (this) {
            io.morgan.mio.domain.collection.CollectionStatus.CURRENT -> StoredCollectionStatus.CURRENT
            io.morgan.mio.domain.collection.CollectionStatus.PLANNED -> StoredCollectionStatus.PLANNED
            io.morgan.mio.domain.collection.CollectionStatus.PAUSED -> StoredCollectionStatus.PAUSED
            io.morgan.mio.domain.collection.CollectionStatus.COMPLETED -> StoredCollectionStatus.COMPLETED
            io.morgan.mio.domain.collection.CollectionStatus.DROPPED -> StoredCollectionStatus.DROPPED
            io.morgan.mio.domain.collection.CollectionStatus.REPEATING -> StoredCollectionStatus.REPEATING
        }

    private fun StoredPendingOperationState.isSettled(): Boolean =
        this == StoredPendingOperationState.DELIVERED ||
            this == StoredPendingOperationState.CANCELLED ||
            this == StoredPendingOperationState.TERMINAL_FAILURE

    private companion object {
        const val MAX_ATTEMPTS: Int = 5
        val LEASE_TTL = 120.seconds
    }
}
