package io.morgan.mio.data.sync

import io.morgan.mio.domain.sync.PendingOperationGroup

public interface PendingOperationWriter {
    public suspend fun enqueue(group: PendingOperationGroup)
}
