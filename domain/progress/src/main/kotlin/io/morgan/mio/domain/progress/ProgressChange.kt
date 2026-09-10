package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressValue
import kotlin.time.Duration

public data class ProgressChange(
    public val actionId: OperationGroupId,
    public val before: ProgressSnapshot,
    public val direction: ProgressDirection,
    public val targetProgress: ProgressValue,
    public val targetStatus: CollectionStatus,
    public val undoWindow: Duration,
)
