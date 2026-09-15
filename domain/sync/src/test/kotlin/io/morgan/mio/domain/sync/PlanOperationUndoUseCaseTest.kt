package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class PlanOperationUndoUseCaseTest {
    private val now = Instant.parse("2026-09-11T10:00:00Z")
    private val plan = PlanOperationUndoUseCase()

    @Test
    fun whollyUnattemptedGroupIsCancelled() {
        assertEquals(SyncUndoDecision.Cancel, plan(listOf(operation("one"))))
    }

    @Test
    fun attemptedGroupCancelsDeferredMembersAndWaitsForActiveMembers() {
        val deferred = operation("deferred", state = PendingOperationState.RETRYABLE_FAILURE, attemptCount = 1)
        val inFlight = operation("flight", state = PendingOperationState.IN_FLIGHT, attemptCount = 1)
        val delivered = operation("delivered", state = PendingOperationState.DELIVERED, attemptCount = 1)

        assertEquals(
            SyncUndoDecision.Compensate(
                cancelMemberIds = setOf(deferred.id),
                waitForMemberIds = setOf(inFlight.id),
            ),
            plan(listOf(deferred, inFlight, delivered)),
        )
    }

    @Test
    fun emptyOrAlreadyCancelledInputIsUnavailable() {
        assertEquals(SyncUndoDecision.Unavailable, plan(emptyList()))
        assertEquals(
            SyncUndoDecision.Unavailable,
            plan(listOf(operation("cancelled", state = PendingOperationState.CANCELLED))),
        )
    }

    private fun operation(
        id: String,
        state: PendingOperationState = PendingOperationState.ENQUEUED,
        attemptCount: Int = 0,
    ): PendingOperation =
        PendingOperation(
            id = OperationId(id),
            groupId = OperationGroupId("group"),
            accountId = AccountId("account"),
            collectionEntryId = CollectionEntryId("entry"),
            kind = PendingOperationKind.SET_PROGRESS_CORRECTION,
            base = OperationValue.Progress(ProgressValue(2)),
            target = OperationValue.Progress(ProgressValue(1)),
            positionInGroup = 0,
            state = state,
            attemptCount = attemptCount,
            lastAttemptAt = if (attemptCount == 0) null else now,
            retryNotBefore = if (state == PendingOperationState.RETRYABLE_FAILURE) now else null,
        )
}
