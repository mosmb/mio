package io.morgan.mio.data.progress.mapper

import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressChange
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressSnapshot
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperation
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.PendingOperationKind
import io.morgan.mio.domain.sync.PendingOperationState

internal fun CollectionEntryEntity.toProgressSnapshot(): ProgressSnapshot =
    ProgressSnapshot(
        entry =
            io.morgan.mio.domain.collection.CollectionEntry(
                id = CollectionEntryId(collectionEntryId),
                accountId = AccountId(accountId),
                mediaId = MediaId(mediaId),
                status = effectiveStatus.toDomainStatus(),
                progress = ProgressValue(effectiveProgress),
                progressUnit = progressUnit.toDomainUnit(),
                total = effectiveTotal?.let(::UnitTotal),
                updatedAt = effectiveUpdatedAtUtcMs,
            ),
        revision = localProgressRevision,
    )

internal fun LocalProgressActivityEntity.toProgressAction(): ProgressAction =
    ProgressAction(
        id = OperationGroupId(operationGroupId),
        order = activityOrder,
        accountId = AccountId(accountId),
        entryId = CollectionEntryId(collectionEntryId),
        mediaId = MediaId(mediaId),
        direction =
            when (direction) {
                "increase" -> ProgressDirection.INCREASE
                "decrease" -> ProgressDirection.DECREASE
                else -> error("Unknown local progress direction: $direction")
            },
        unit = progressUnit.toDomainUnit(),
        beforeProgress = ProgressValue(beforeProgress),
        afterProgress = ProgressValue(afterProgress),
        beforeStatus = beforeStatus.toDomainStatus(),
        afterStatus = afterStatus.toDomainStatus(),
        beforeUpdatedAt = beforeUpdatedAtUtcMs,
        afterUpdatedAt = afterUpdatedAtUtcMs,
        afterRevision = afterRevision,
        createdAt = createdAtUtcMs,
        undoUntil = undoUntilUtcMs,
        undoneAt = undoneAtUtcMs,
    )

internal fun ProgressChange.toPendingOperationGroup(createdAt: kotlin.time.Instant): PendingOperationGroup {
    val entry = before.entry
    val progressOperationId = OperationId("${actionId.value}:progress")
    val progressOperation =
        PendingOperation(
            id = progressOperationId,
            groupId = actionId,
            accountId = entry.accountId,
            collectionEntryId = entry.id,
            kind =
                when (direction) {
                    ProgressDirection.INCREASE -> PendingOperationKind.SET_PROGRESS_FORWARD
                    ProgressDirection.DECREASE -> PendingOperationKind.SET_PROGRESS_CORRECTION
                },
            base = OperationValue.Progress(entry.progress),
            target = OperationValue.Progress(targetProgress),
            positionInGroup = 0,
            dependsOn = emptySet(),
            state = PendingOperationState.ENQUEUED,
        )
    val operations =
        buildList {
            add(progressOperation)
            if (targetStatus != entry.status) {
                add(
                    PendingOperation(
                        id = OperationId("${actionId.value}:status"),
                        groupId = actionId,
                        accountId = entry.accountId,
                        collectionEntryId = entry.id,
                        kind = PendingOperationKind.SET_STATUS,
                        base = OperationValue.Status(entry.status),
                        target = OperationValue.Status(targetStatus),
                        positionInGroup = 1,
                        dependsOn = setOf(progressOperationId),
                        state = PendingOperationState.ENQUEUED,
                    ),
                )
            }
        }
    return PendingOperationGroup(
        id = actionId,
        accountId = entry.accountId,
        createdAt = createdAt,
        operations = operations,
    )
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

private fun StoredProgressUnit.toDomainUnit(): ProgressUnit =
    when (this) {
        StoredProgressUnit.EPISODE -> ProgressUnit.EPISODE
        StoredProgressUnit.CHAPTER -> ProgressUnit.CHAPTER
        StoredProgressUnit.VOLUME -> ProgressUnit.VOLUME
    }

private fun CollectionStatus.toStoredStatus(): StoredCollectionStatus =
    when (this) {
        CollectionStatus.CURRENT -> StoredCollectionStatus.CURRENT
        CollectionStatus.PLANNED -> StoredCollectionStatus.PLANNED
        CollectionStatus.PAUSED -> StoredCollectionStatus.PAUSED
        CollectionStatus.COMPLETED -> StoredCollectionStatus.COMPLETED
        CollectionStatus.DROPPED -> StoredCollectionStatus.DROPPED
        CollectionStatus.REPEATING -> StoredCollectionStatus.REPEATING
    }

private fun ProgressUnit.toStoredUnit(): StoredProgressUnit =
    when (this) {
        ProgressUnit.EPISODE -> StoredProgressUnit.EPISODE
        ProgressUnit.CHAPTER -> StoredProgressUnit.CHAPTER
        ProgressUnit.VOLUME -> StoredProgressUnit.VOLUME
    }
