package io.morgan.mio.domain.progress

import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import kotlinx.coroutines.flow.Flow

public interface ProgressRepository {
    public suspend fun readSnapshot(mediaId: MediaId): ProgressSnapshot?

    public suspend fun findAction(actionId: OperationGroupId): ProgressAction?

    public fun observeLatestAction(): Flow<ProgressAction?>

    public suspend fun readLatestAction(): ProgressAction?

    public suspend fun commit(change: ProgressChange): ProgressWriteResult

    public suspend fun undo(change: ProgressUndo): ProgressWriteResult
}
