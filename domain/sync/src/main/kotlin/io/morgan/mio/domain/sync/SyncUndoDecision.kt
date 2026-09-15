package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.OperationId

public sealed interface SyncUndoDecision {
    public data object Cancel : SyncUndoDecision

    public class Compensate(
        cancelMemberIds: Set<OperationId>,
        waitForMemberIds: Set<OperationId>,
    ) : SyncUndoDecision {
        public val cancelMemberIds: Set<OperationId> = cancelMemberIds.toSet()
        public val waitForMemberIds: Set<OperationId> = waitForMemberIds.toSet()

        override fun equals(other: Any?): Boolean =
            other is Compensate &&
                cancelMemberIds == other.cancelMemberIds &&
                waitForMemberIds == other.waitForMemberIds

        override fun hashCode(): Int = 31 * cancelMemberIds.hashCode() + waitForMemberIds.hashCode()

        override fun toString(): String =
            "Compensate(cancelMemberIds=$cancelMemberIds, waitForMemberIds=$waitForMemberIds)"
    }

    public data object Unavailable : SyncUndoDecision
}
