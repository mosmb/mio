package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProjectSyncEntryUseCaseTest {
    private val now = Instant.parse("2026-09-11T10:00:00Z")
    private val remoteTime = now.plus(10.seconds)
    private val project = ProjectSyncEntryUseCase()

    @Test
    fun orderedCorrectionWinsOverHistoricalMaximum() {
        val forward = operation("forward", 4, 5, PendingOperationKind.SET_PROGRESS_FORWARD, 0)
        val correction = operation("correction", 5, 4, PendingOperationKind.SET_PROGRESS_CORRECTION, 1)

        val result = project(state(progress = 4), state(progress = 4), listOf(forward, correction))

        assertEquals(4, result.progress.value)
        assertEquals(0L, result.revision)
        assertEquals(now, result.updatedAt)
    }

    @Test
    fun deliveredAndCancelledMembersAreNotReplayed() {
        val delivered =
            operation(
                "delivered",
                4,
                5,
                PendingOperationKind.SET_PROGRESS_FORWARD,
                0,
                state = PendingOperationState.DELIVERED,
                attemptCount = 1,
                lastAttemptAt = now,
            )
        val cancelled =
            operation(
                "cancelled",
                5,
                4,
                PendingOperationKind.SET_PROGRESS_CORRECTION,
                1,
                state = PendingOperationState.CANCELLED,
            )

        val result = project(state(progress = 5), state(progress = 5), listOf(delivered, cancelled))

        assertEquals(5, result.progress.value)
        assertEquals(0L, result.revision)
        assertEquals(now, result.updatedAt)
    }

    @Test
    fun remoteEffectiveChangeIncrementsRevisionButBaselineOnlyAcknowledgementDoesNot() {
        val current = state(progress = 5, revision = 7)
        val acknowledgement = project(state(progress = 3, updatedAt = remoteTime), current, emptyList())
        assertEquals(5, acknowledgement.progress.value)
        assertEquals(7L, acknowledgement.revision)
        assertEquals(now, acknowledgement.updatedAt)

        val remoteAdvance = project(state(progress = 6, updatedAt = remoteTime), current, emptyList())
        assertEquals(6, remoteAdvance.progress.value)
        assertEquals(8L, remoteAdvance.revision)
        assertEquals(remoteTime, remoteAdvance.updatedAt)
    }

    @Test
    fun completedRemoteStateWinsOverAnOlderCurrentProjectionAndExplicitCorrectionCanLower() {
        val current = state(progress = 11, status = CollectionStatus.CURRENT)
        val completed = state(progress = 12, status = CollectionStatus.COMPLETED)
        assertEquals(CollectionStatus.COMPLETED, project(completed, current, emptyList()).status)

        val correction = operation("undo", 12, 11, PendingOperationKind.SET_PROGRESS_CORRECTION, 0)
        val projected = project(completed, completed, listOf(correction))
        assertEquals(11, projected.progress.value)
    }

    private fun operation(
        id: String,
        base: Int,
        target: Int,
        kind: PendingOperationKind,
        position: Int,
        state: PendingOperationState = PendingOperationState.ENQUEUED,
        attemptCount: Int = 0,
        lastAttemptAt: Instant? = null,
    ): PendingOperation =
        PendingOperation(
            id = OperationId(id),
            groupId = OperationGroupId("group-$id"),
            accountId = ACCOUNT,
            collectionEntryId = ENTRY,
            kind = kind,
            base = OperationValue.Progress(ProgressValue(base)),
            target = OperationValue.Progress(ProgressValue(target)),
            positionInGroup = position,
            state = state,
            attemptCount = attemptCount,
            lastAttemptAt = lastAttemptAt,
        )

    private fun state(
        progress: Int,
        status: CollectionStatus = CollectionStatus.CURRENT,
        total: Int? = 12,
        revision: Long = 0,
        updatedAt: Instant = now,
    ): SyncEntryState =
        SyncEntryState(
            accountId = ACCOUNT,
            entryId = ENTRY,
            unit = ProgressUnit.EPISODE,
            progress = ProgressValue(progress),
            status = status,
            total = total?.let(::UnitTotal),
            updatedAt = updatedAt,
            revision = revision,
        )

    private companion object {
        val ACCOUNT = AccountId("account-1")
        val ENTRY = CollectionEntryId("entry-1")
    }
}
