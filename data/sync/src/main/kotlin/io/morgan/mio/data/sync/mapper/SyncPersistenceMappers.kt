package io.morgan.mio.data.sync.mapper

import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SyncIssueEntity
import io.morgan.mio.core.database.entity.SyncLeaseEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperation
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.PendingOperationKind
import io.morgan.mio.domain.sync.PendingOperationState
import io.morgan.mio.domain.sync.SyncEntryState
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncIssueKind
import io.morgan.mio.domain.sync.SyncLease

internal fun PendingOperationGroupEntity.toDomain(operations: List<PendingOperation>): PendingOperationGroup =
    PendingOperationGroup(
        id = OperationGroupId(operationGroupId),
        accountId = AccountId(accountId),
        createdAt = createdAtUtcMs,
        operations = operations,
        compensatesGroupId = compensatesGroupId?.let(::OperationGroupId),
    )

internal fun PendingOperationEntity.toDomain(dependencies: Set<OperationId>): PendingOperation =
    PendingOperation(
        id = OperationId(operationId),
        groupId = OperationGroupId(operationGroupId),
        accountId = AccountId(accountId),
        collectionEntryId = CollectionEntryId(collectionEntryId),
        kind = kind.toDomainKind(),
        base = operationValue(baseProgress, baseStatus),
        target = operationValue(targetProgress, targetStatus),
        positionInGroup = positionInGroup,
        dependsOn = dependencies,
        state = lifecycleState.toDomainState(),
        attemptCount = attemptCount,
        lastAttemptAt = lastAttemptAtUtcMs,
        retryNotBefore = retryNotBeforeUtcMs,
    )

internal fun CollectionEntryEntity.toSyncEntryState(): SyncEntryState =
    SyncEntryState(
        accountId = AccountId(accountId),
        entryId = CollectionEntryId(collectionEntryId),
        unit = progressUnit.toDomainUnit(),
        progress = ProgressValue(effectiveProgress),
        status = effectiveStatus.toDomainStatus(),
        total = effectiveTotal?.let(::UnitTotal),
        updatedAt = effectiveUpdatedAtUtcMs,
        revision = localProgressRevision,
    )

internal fun CollectionEntryEntity.toRemoteSyncEntryState(): SyncEntryState =
    SyncEntryState(
        accountId = AccountId(accountId),
        entryId = CollectionEntryId(collectionEntryId),
        unit = progressUnit.toDomainUnit(),
        progress = ProgressValue(remoteProgress),
        status = remoteStatus.toDomainStatus(),
        total = remoteTotal?.let(::UnitTotal),
        updatedAt = remoteUpdatedAtUtcMs,
        revision = 0L,
    )

internal fun ProviderCollectionEntry.toSyncEntryState(local: SyncEntryState): SyncEntryState {
    // Provider identity was validated against the persisted mapping before this conversion.
    // Local account IDs are opaque and intentionally differ from remote account IDs.
    require(id.value.isNotBlank() && mediaId.value.isNotBlank())
    return SyncEntryState(
        accountId = local.accountId,
        entryId = local.entryId,
        unit = progressUnit.toDomainUnit(),
        progress = ProgressValue(progress),
        status = status.toDomainStatus(),
        total = total?.let(::UnitTotal),
        updatedAt = updatedAt,
        revision = 0L,
    )
}

internal fun SyncLeaseEntity.toDomain(): SyncLease = SyncLease(AccountId(accountId), ownerToken, expiresAtUtcMs)

internal fun SyncIssueEntity.toDomain(): SyncIssue =
    SyncIssue(
        accountId = AccountId(accountId),
        entryId = CollectionEntryId(collectionEntryId),
        groupId = OperationGroupId(operationGroupId),
        operationId = OperationId(operationId),
        kind = kind.toDomainIssueKind(),
        base = operationValue(baseProgress, baseStatus),
        target = operationValue(targetProgress, targetStatus),
        local =
            io.morgan.mio.domain.sync.SyncEntryState(
                accountId = AccountId(accountId),
                entryId = CollectionEntryId(collectionEntryId),
                unit = progressUnit.toDomainUnit(),
                progress = ProgressValue(localProgress),
                status = localStatus.toDomainStatus(),
                total = localTotal?.let(::UnitTotal),
                updatedAt = localUpdatedAtUtcMs,
                revision = localRevision,
            ),
        remote =
            remoteProgress?.let { progress ->
                io.morgan.mio.domain.sync.SyncEntryState(
                    accountId = AccountId(accountId),
                    entryId = CollectionEntryId(collectionEntryId),
                    unit = progressUnit.toDomainUnit(),
                    progress = ProgressValue(progress),
                    status = (remoteStatus ?: error("Remote status missing")).toDomainStatus(),
                    total = remoteTotal?.let(::UnitTotal),
                    updatedAt = remoteUpdatedAtUtcMs ?: error("Remote timestamp missing"),
                    revision = 0L,
                )
            },
        detectedAt = detectedAtUtcMs,
    )

private fun operationValue(
    progress: Int?,
    status: StoredCollectionStatus?,
): OperationValue =
    when {
        progress != null && status == null -> OperationValue.Progress(ProgressValue(progress))
        progress == null && status != null -> OperationValue.Status(status.toDomainStatus())
        else -> error("Pending operation field pair is invalid")
    }

private fun StoredPendingOperationKind.toDomainKind(): PendingOperationKind =
    when (this) {
        StoredPendingOperationKind.SET_PROGRESS_FORWARD -> PendingOperationKind.SET_PROGRESS_FORWARD
        StoredPendingOperationKind.SET_PROGRESS_CORRECTION -> PendingOperationKind.SET_PROGRESS_CORRECTION
        StoredPendingOperationKind.SET_STATUS -> PendingOperationKind.SET_STATUS
    }

private fun StoredPendingOperationState.toDomainState(): PendingOperationState =
    when (this) {
        StoredPendingOperationState.ENQUEUED -> PendingOperationState.ENQUEUED
        StoredPendingOperationState.IN_FLIGHT -> PendingOperationState.IN_FLIGHT
        StoredPendingOperationState.RETRYABLE_FAILURE -> PendingOperationState.RETRYABLE_FAILURE
        StoredPendingOperationState.DELIVERED -> PendingOperationState.DELIVERED
        StoredPendingOperationState.BLOCKED -> PendingOperationState.BLOCKED
        StoredPendingOperationState.TERMINAL_FAILURE -> PendingOperationState.TERMINAL_FAILURE
        StoredPendingOperationState.CANCELLED -> PendingOperationState.CANCELLED
    }

private fun StoredProgressUnit.toDomainUnit(): ProgressUnit =
    when (this) {
        StoredProgressUnit.EPISODE -> ProgressUnit.EPISODE
        StoredProgressUnit.CHAPTER -> ProgressUnit.CHAPTER
        StoredProgressUnit.VOLUME -> ProgressUnit.VOLUME
    }

private fun ProviderProgressUnit.toDomainUnit(): ProgressUnit =
    when (this) {
        ProviderProgressUnit.EPISODE -> ProgressUnit.EPISODE
        ProviderProgressUnit.CHAPTER -> ProgressUnit.CHAPTER
        ProviderProgressUnit.VOLUME -> ProgressUnit.VOLUME
    }

private fun StoredCollectionStatus.toDomainStatus(): CollectionStatus =
    when (this) {
        StoredCollectionStatus.CURRENT -> CollectionStatus.CURRENT
        StoredCollectionStatus.PLANNED -> CollectionStatus.PLANNED
        StoredCollectionStatus.PAUSED -> CollectionStatus.PAUSED
        StoredCollectionStatus.COMPLETED -> CollectionStatus.COMPLETED
        StoredCollectionStatus.DROPPED -> CollectionStatus.DROPPED
        StoredCollectionStatus.REPEATING -> CollectionStatus.REPEATING
    }

private fun ProviderCollectionStatus.toDomainStatus(): CollectionStatus =
    when (this) {
        ProviderCollectionStatus.CURRENT -> CollectionStatus.CURRENT
        ProviderCollectionStatus.PLANNED -> CollectionStatus.PLANNED
        ProviderCollectionStatus.PAUSED -> CollectionStatus.PAUSED
        ProviderCollectionStatus.COMPLETED -> CollectionStatus.COMPLETED
        ProviderCollectionStatus.DROPPED -> CollectionStatus.DROPPED
        ProviderCollectionStatus.REPEATING -> CollectionStatus.REPEATING
    }

private fun String.toDomainIssueKind(): SyncIssueKind =
    when (this) {
        "authentication" -> SyncIssueKind.AUTHENTICATION
        "rejected" -> SyncIssueKind.REJECTED
        "conflict" -> SyncIssueKind.CONFLICT
        "retry_exhausted" -> SyncIssueKind.RETRY_EXHAUSTED
        "invalid_remote_data" -> SyncIssueKind.INVALID_REMOTE_DATA
        else -> error("Unknown synchronization issue kind")
    }
