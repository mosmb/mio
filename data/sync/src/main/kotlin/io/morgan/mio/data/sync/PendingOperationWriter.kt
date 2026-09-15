package io.morgan.mio.data.sync

import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.sync.PendingOperationGroup

public interface PendingOperationWriter {
    public suspend fun enqueue(group: PendingOperationGroup)

    public suspend fun cancelUndelivered(groupId: OperationGroupId): Boolean

    /** Applies the persisted cancel-or-compensate decision in the caller's mutation transaction. */
    public suspend fun undo(
        groupId: OperationGroupId,
        compensation: PendingOperationGroup,
    ): Boolean
}
