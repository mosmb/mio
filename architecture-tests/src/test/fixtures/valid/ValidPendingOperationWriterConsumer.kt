package io.morgan.mio.data.progress.fixture

import io.morgan.mio.data.sync.PendingOperationWriter
import io.morgan.mio.domain.sync.PendingOperationGroup

internal class ValidPendingOperationWriterConsumer(
    private val writer: PendingOperationWriter,
) {
    suspend fun enqueue(group: PendingOperationGroup) {
        writer.enqueue(group)
    }
}
