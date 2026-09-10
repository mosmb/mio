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
import io.morgan.mio.domain.progress.ProgressChange
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressSnapshot
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProgressPersistenceMappersTest {
    private val accountId = AccountId("account-1")
    private val entryId = CollectionEntryId("entry-1")
    private val mediaId = MediaId("media-1")
    private val instant = Instant.parse("2026-08-23T12:00:00Z")

    @Test
    fun forwardChangeMapsToOneAbsoluteForwardOperation() {
        val change = change(ProgressDirection.INCREASE, ProgressUnit.EPISODE, 8, 9, CollectionStatus.CURRENT)

        val group = change.toPendingOperationGroup(instant)
        val operation = group.operations.single()

        assertEquals(OperationGroupId("group-increase"), group.id)
        assertEquals(accountId, group.accountId)
        assertEquals(instant, group.createdAt)
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

        val operation = change.toPendingOperationGroup(instant).operations.single()

        assertEquals(PendingOperationKind.SET_PROGRESS_CORRECTION, operation.kind)
        assertEquals(OperationValue.Progress(ProgressValue(41)), operation.base)
        assertEquals(OperationValue.Progress(ProgressValue(40)), operation.target)
        assertEquals(ProgressUnit.CHAPTER, change.before.entry.progressUnit)
    }

    @Test
    fun finalChangeMapsProgressThenDependentCompletedStatus() {
        val change = change(ProgressDirection.INCREASE, ProgressUnit.VOLUME, 2, 3, CollectionStatus.COMPLETED)

        val operations = change.toPendingOperationGroup(instant).operations

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
                accountId = accountId.value,
                collectionEntryId = entryId.value,
                mediaId = mediaId.value,
                direction = "decrease",
                progressUnit = StoredProgressUnit.VOLUME,
                beforeProgress = 3,
                afterProgress = 2,
                beforeStatus = StoredCollectionStatus.CURRENT,
                afterStatus = StoredCollectionStatus.CURRENT,
                beforeUpdatedAtUtcMs = instant,
                afterUpdatedAtUtcMs = instant.plus(1.seconds),
                afterRevision = 8,
                createdAtUtcMs = instant.plus(1.seconds),
                undoUntilUtcMs = instant.plus(7.seconds),
                undoneAtUtcMs = null,
            )

        val action = activity.toProgressAction()

        assertEquals(OperationGroupId("group-activity"), action.id)
        assertEquals(7, action.order)
        assertEquals(accountId, action.accountId)
        assertEquals(entryId, action.entryId)
        assertEquals(mediaId, action.mediaId)
        assertEquals(ProgressDirection.DECREASE, action.direction)
        assertEquals(ProgressUnit.VOLUME, action.unit)
        assertEquals(ProgressValue(3), action.beforeProgress)
        assertEquals(ProgressValue(2), action.afterProgress)
        assertEquals(instant, action.beforeUpdatedAt)
        assertEquals(instant.plus(1.seconds), action.afterUpdatedAt)
        assertEquals(8, action.afterRevision)
        assertEquals(instant.plus(7.seconds), action.undoUntil)
        assertNull(action.undoneAt)
    }

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
                            id = entryId,
                            accountId = accountId,
                            mediaId = mediaId,
                            status = CollectionStatus.CURRENT,
                            progress = ProgressValue(beforeProgress),
                            progressUnit = unit,
                            total = UnitTotal(12),
                            updatedAt = instant,
                        ),
                    revision = 7,
                ),
            direction = direction,
            targetProgress = ProgressValue(targetProgress),
            targetStatus = targetStatus,
            undoWindow = 6.seconds,
        )
}
