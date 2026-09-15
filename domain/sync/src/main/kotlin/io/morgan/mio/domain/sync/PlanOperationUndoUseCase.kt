package io.morgan.mio.domain.sync

public class PlanOperationUndoUseCase {
    public operator fun invoke(operations: List<PendingOperation>): SyncUndoDecision {
        if (operations.isEmpty()) return SyncUndoDecision.Unavailable
        if (operations.any { operation -> operation.state == PendingOperationState.CANCELLED }) {
            return SyncUndoDecision.Unavailable
        }
        val unattempted =
            operations.all { operation ->
                operation.state == PendingOperationState.ENQUEUED &&
                    operation.attemptCount == 0 &&
                    operation.lastAttemptAt == null &&
                    operation.retryNotBefore == null
            }
        if (unattempted) return SyncUndoDecision.Cancel

        val waitFor =
            operations
                .filter { operation -> operation.state == PendingOperationState.IN_FLIGHT }
                .mapTo(linkedSetOf()) { operation -> operation.id }
        val cancel =
            operations
                .filter { operation ->
                    operation.state != PendingOperationState.IN_FLIGHT &&
                        operation.state != PendingOperationState.DELIVERED
                }.mapTo(linkedSetOf()) { operation -> operation.id }
        return SyncUndoDecision.Compensate(cancelMemberIds = cancel, waitForMemberIds = waitFor)
    }
}
