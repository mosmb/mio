package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import kotlin.time.Instant

public data class ProgressAction(
    public val id: OperationGroupId,
    public val order: Long,
    public val accountId: AccountId,
    public val entryId: CollectionEntryId,
    public val mediaId: MediaId,
    public val direction: ProgressDirection,
    public val unit: ProgressUnit,
    public val beforeProgress: ProgressValue,
    public val afterProgress: ProgressValue,
    public val beforeStatus: CollectionStatus,
    public val afterStatus: CollectionStatus,
    public val beforeUpdatedAt: Instant,
    public val afterUpdatedAt: Instant,
    public val afterRevision: Long,
    public val createdAt: Instant,
    public val undoUntil: Instant,
    public val undoneAt: Instant?,
) {
    init {
        require(order >= 0L)
        require(afterRevision >= 0L)
        require(undoUntil >= createdAt)
    }
}
