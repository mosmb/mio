package io.morgan.mio.data.progress.mapper

import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressChange
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressSnapshot
import io.morgan.mio.domain.progress.ProgressUndo
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProgressPersistenceMappersTest {
    @Test
    fun forwardChangeMapsToOneAbsoluteForwardOperation() {
        val change = change(ProgressDirection.INCREASE, ProgressUnit.EPISODE, 8, 9, CollectionStatus.CURRENT)

        val group = change.toPendingOperationGroup(NOW)
        val operation = group.operations.single()

        assertEquals(OperationGroupId("group-increase"), group.id)
        assertEquals(ACCOUNT, group.accountId)
        assertEquals(NOW, group.createdAt)
        assertEquals(OperationGroupId("group-increase"), operation.groupId)
        assertEquals(PendingOperationKind.SET_PROGRESS_FORWARD, operation.kind)
        assertEquals(OperationValue.Progress(ProgressValue(8)), operation.base)
        assertEquals(OperationValue.Progress(ProgressValue(9)), operation.target)
        assertEquals("group-increase:progress", operation.id.value)
        assertEquals(0, operation.positionInGroup)
        assertTrue(operation.dependsOn.isEmpty())
    }

    @Test
    fun backwardChangeMapsToAnExplicitCorrectionWithTheSameUnit() {
        val change = change(ProgressDirection.DECREASE, ProgressUnit.CHAPTER, 41, 40, CollectionStatus.CURRENT)

        val operation = change.toPendingOperationGroup(NOW).operations.single()

        assertEquals(PendingOperationKind.SET_PROGRESS_CORRECTION, operation.kind)
        assertEquals(OperationValue.Progress(ProgressValue(41)), operation.base)
        assertEquals(OperationValue.Progress(ProgressValue(40)), operation.target)
        assertEquals(ProgressUnit.CHAPTER, change.before.entry.progressUnit)
    }

    @Test
    fun finalChangeMapsProgressThenDependentCompletedStatus() {
        val change = change(ProgressDirection.INCREASE, ProgressUnit.VOLUME, 2, 3, CollectionStatus.COMPLETED)

        val operations = change.toPendingOperationGroup(NOW).operations

        assertEquals(2, operations.size)
        assertEquals(listOf(0, 1), operations.map { it.positionInGroup })
        assertEquals(
            listOf(PendingOperationKind.SET_PROGRESS_FORWARD, PendingOperationKind.SET_STATUS),
            operations.map { it.kind },
        )
        assertEquals(OperationValue.Status(CollectionStatus.CURRENT), operations[1].base)
        assertEquals(OperationValue.Status(CollectionStatus.COMPLETED), operations[1].target)
        assertEquals(setOf(operations[0].id), operations[1].dependsOn)
        assertEquals("group-increase:status", operations[1].id.value)
    }

    @Test
    fun activityMappingPreservesBeforeAfterAndUndoMetadata() {
        val activity =
            LocalProgressActivityEntity(
                activityOrder = 7,
                operationGroupId = "group-activity",
                accountId = ACCOUNT.value,
                collectionEntryId = ENTRY.value,
                mediaId = MEDIA.value,
                direction = "decrease",
                progressUnit = StoredProgressUnit.VOLUME,
                beforeProgress = 3,
                afterProgress = 2,
                beforeStatus = StoredCollectionStatus.CURRENT,
                afterStatus = StoredCollectionStatus.CURRENT,
                beforeUpdatedAtUtcMs = NOW,
                afterUpdatedAtUtcMs = NOW.plus(1.seconds),
                afterRevision = 8,
                createdAtUtcMs = NOW.plus(1.seconds),
                undoUntilUtcMs = NOW.plus(7.seconds),
                undoneAtUtcMs = null,
            )

        val action = activity.toProgressAction()

        assertEquals(OperationGroupId("group-activity"), action.id)
        assertEquals(7, action.order)
        assertEquals(ACCOUNT, action.accountId)
        assertEquals(ENTRY, action.entryId)
        assertEquals(MEDIA, action.mediaId)
        assertEquals(ProgressDirection.DECREASE, action.direction)
        assertEquals(ProgressUnit.VOLUME, action.unit)
        assertEquals(ProgressValue(3), action.beforeProgress)
        assertEquals(ProgressValue(2), action.afterProgress)
        assertEquals(NOW, action.beforeUpdatedAt)
        assertEquals(NOW.plus(1.seconds), action.afterUpdatedAt)
        assertEquals(8, action.afterRevision)
        assertEquals(NOW.plus(7.seconds), action.undoUntil)
        assertNull(action.undoneAt)
    }

    @Test
    fun completionUndoMapsToDeterministicExplicitInverseWithStatusDependency() {
        val action = action(beforeStatus = CollectionStatus.CURRENT, afterStatus = CollectionStatus.COMPLETED)
        val compensation =
            ProgressUndo(
                action = action,
                current =
                    ProgressSnapshot(
                        entry = entry(progress = 12, status = CollectionStatus.COMPLETED),
                        revision = 8,
                    ),
                requestedAt = NOW,
            ).toCompensatingOperationGroup(NOW)

        assertEquals(OperationGroupId("action-1:undo"), compensation.id)
        assertEquals(OperationGroupId("action-1"), compensation.compensatesGroupId)
        assertEquals(
            listOf("action-1:undo:progress", "action-1:undo:status"),
            compensation.operations.map { it.id.value },
        )
        val progress = compensation.operations[0]
        assertEquals(PendingOperationKind.SET_PROGRESS_CORRECTION, progress.kind)
        assertEquals(12, (progress.base as io.morgan.mio.domain.sync.OperationValue.Progress).value.value)
        assertEquals(11, (progress.target as io.morgan.mio.domain.sync.OperationValue.Progress).value.value)
        val status = compensation.operations[1]
        assertEquals(
            io.morgan.mio.domain.sync.OperationValue
                .Status(CollectionStatus.COMPLETED),
            status.base,
        )
        assertEquals(
            io.morgan.mio.domain.sync.OperationValue
                .Status(CollectionStatus.CURRENT),
            status.target,
        )
        assertEquals(setOf(progress.id), status.dependsOn)
    }

    @Test
    fun unchangedStatusDoesNotCreateASecondInverseMember() {
        val action = action(beforeStatus = CollectionStatus.CURRENT, afterStatus = CollectionStatus.CURRENT)
        val compensation =
            ProgressUndo(
                action = action,
                current = ProgressSnapshot(entry = entry(progress = 11), revision = 3),
                requestedAt = NOW,
            ).toCompensatingOperationGroup(NOW)

        assertEquals(listOf("action-1:undo:progress"), compensation.operations.map { it.id.value })
        assertTrue(
            compensation.operations
                .single()
                .dependsOn
                .isEmpty(),
        )
    }

    private fun action(
        beforeStatus: CollectionStatus,
        afterStatus: CollectionStatus,
    ): ProgressAction =
        ProgressAction(
            id = OperationGroupId("action-1"),
            order = 1,
            accountId = ACCOUNT,
            entryId = ENTRY,
            mediaId = MEDIA,
            direction = ProgressDirection.INCREASE,
            unit = ProgressUnit.EPISODE,
            beforeProgress = ProgressValue(11),
            afterProgress = ProgressValue(12),
            beforeStatus = beforeStatus,
            afterStatus = afterStatus,
            beforeUpdatedAt = NOW - 1.seconds,
            afterUpdatedAt = NOW,
            afterRevision = 7,
            createdAt = NOW - 1.seconds,
            undoUntil = NOW + 6.seconds,
            undoneAt = null,
        )

    private fun entry(
        progress: Int,
        status: CollectionStatus = CollectionStatus.CURRENT,
    ): CollectionEntry =
        CollectionEntry(
            id = ENTRY,
            accountId = ACCOUNT,
            mediaId = MEDIA,
            status = status,
            progress = ProgressValue(progress),
            progressUnit = ProgressUnit.EPISODE,
            total = UnitTotal(12),
            updatedAt = NOW,
        )

    private fun change(
        direction: ProgressDirection,
        unit: ProgressUnit,
        beforeProgress: Int,
        targetProgress: Int,
        targetStatus: CollectionStatus,
    ): ProgressChange =
        ProgressChange(
            actionId = OperationGroupId("group-${direction.name.lowercase()}"),
            before =
                ProgressSnapshot(
                    entry =
                        CollectionEntry(
                            id = ENTRY,
                            accountId = ACCOUNT,
                            mediaId = MEDIA,
                            status = CollectionStatus.CURRENT,
                            progress = ProgressValue(beforeProgress),
                            progressUnit = unit,
                            total = UnitTotal(12),
                            updatedAt = NOW,
                        ),
                    revision = 7,
                ),
            direction = direction,
            targetProgress = ProgressValue(targetProgress),
            targetStatus = targetStatus,
            undoWindow = 6.seconds,
        )

    private companion object {
        val ACCOUNT = AccountId("account-1")
        val ENTRY = CollectionEntryId("entry-1")
        val MEDIA = MediaId("media-1")
        val NOW = Instant.parse("2026-09-11T10:00:00Z")
    }
}
