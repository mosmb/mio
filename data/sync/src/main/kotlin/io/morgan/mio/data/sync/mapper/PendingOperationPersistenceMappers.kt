package io.morgan.mio.data.sync.mapper

import io.morgan.mio.core.database.entity.PendingOperationDependencyEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperation
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.PendingOperationKind
import io.morgan.mio.domain.sync.PendingOperationState

internal fun PendingOperationGroup.toPendingOperationGroupEntity(): PendingOperationGroupEntity =
    PendingOperationGroupEntity(
        groupOrder = 0L,
        operationGroupId = id.value,
        accountId = accountId.value,
        createdAtUtcMs = createdAt,
        compensatesGroupId = compensatesGroupId?.value,
    )

internal fun PendingOperation.toPendingOperationEntity(): PendingOperationEntity {
    val baseValue = base
    val targetValue = target
    val progressValues =
        if (baseValue is OperationValue.Progress && targetValue is OperationValue.Progress) {
            baseValue.value.value to targetValue.value.value
        } else {
            null
        }
    val statusValues =
        if (baseValue is OperationValue.Status && targetValue is OperationValue.Status) {
            baseValue.value.toStoredCollectionStatus() to targetValue.value.toStoredCollectionStatus()
        } else {
            null
        }

    return PendingOperationEntity(
        operationId = id.value,
        operationGroupId = groupId.value,
        accountId = accountId.value,
        collectionEntryId = collectionEntryId.value,
        positionInGroup = positionInGroup,
        kind = kind.toStoredPendingOperationKind(),
        baseProgress = progressValues?.first,
        targetProgress = progressValues?.second,
        baseStatus = statusValues?.first,
        targetStatus = statusValues?.second,
        lifecycleState = state.toStoredPendingOperationState(),
        attemptCount = attemptCount,
        lastAttemptAtUtcMs = lastAttemptAt,
        retryNotBeforeUtcMs = retryNotBefore,
    )
}

internal fun PendingOperation.toPendingOperationDependencyEntities(): List<PendingOperationDependencyEntity> =
    dependsOn
        .sortedBy { operationId -> operationId.value }
        .map { dependencyId ->
            PendingOperationDependencyEntity(
                operationId = id.value,
                dependsOnOperationId = dependencyId.value,
            )
        }

private fun PendingOperationKind.toStoredPendingOperationKind(): StoredPendingOperationKind =
    when (this) {
        PendingOperationKind.SET_PROGRESS_FORWARD -> StoredPendingOperationKind.SET_PROGRESS_FORWARD
        PendingOperationKind.SET_PROGRESS_CORRECTION -> StoredPendingOperationKind.SET_PROGRESS_CORRECTION
        PendingOperationKind.SET_STATUS -> StoredPendingOperationKind.SET_STATUS
    }

private fun PendingOperationState.toStoredPendingOperationState(): StoredPendingOperationState =
    when (this) {
        PendingOperationState.ENQUEUED -> StoredPendingOperationState.ENQUEUED
        PendingOperationState.IN_FLIGHT -> StoredPendingOperationState.IN_FLIGHT
        PendingOperationState.RETRYABLE_FAILURE -> StoredPendingOperationState.RETRYABLE_FAILURE
        PendingOperationState.DELIVERED -> StoredPendingOperationState.DELIVERED
        PendingOperationState.BLOCKED -> StoredPendingOperationState.BLOCKED
        PendingOperationState.TERMINAL_FAILURE -> StoredPendingOperationState.TERMINAL_FAILURE
    }

private fun CollectionStatus.toStoredCollectionStatus(): StoredCollectionStatus =
    when (this) {
        CollectionStatus.CURRENT -> StoredCollectionStatus.CURRENT
        CollectionStatus.PLANNED -> StoredCollectionStatus.PLANNED
        CollectionStatus.PAUSED -> StoredCollectionStatus.PAUSED
        CollectionStatus.COMPLETED -> StoredCollectionStatus.COMPLETED
        CollectionStatus.DROPPED -> StoredCollectionStatus.DROPPED
    }
