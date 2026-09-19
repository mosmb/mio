package io.morgan.mio.domain.collection

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlin.time.Instant

public data class CollectionStatusChange(
    public val actionId: OperationGroupId,
    public val accountId: AccountId,
    public val mediaId: MediaId,
    public val progressUnit: ProgressUnit,
    public val before: CollectionEntry?,
    public val targetStatus: CollectionStatus,
    public val targetProgress: ProgressValue,
    public val targetTotal: UnitTotal?,
    public val createdAt: Instant,
    public val undoUntil: Instant,
) {
    init {
        require(before == null || before.accountId == accountId)
        require(before == null || before.mediaId == mediaId)
        require(before == null || before.progressUnit == progressUnit)
        require(targetTotal == null || targetProgress.value <= targetTotal.value)
        require(undoUntil >= createdAt)
    }
}
