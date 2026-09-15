package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlin.time.Instant

public data class SyncEntryState(
    public val accountId: AccountId,
    public val entryId: CollectionEntryId,
    public val unit: ProgressUnit,
    public val progress: ProgressValue,
    public val status: CollectionStatus,
    public val total: UnitTotal?,
    public val updatedAt: Instant,
    public val revision: Long = 0L,
) {
    init {
        require(revision >= 0L)
        require(total == null || progress.value <= total.value)
    }
}
