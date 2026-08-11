package io.morgan.mio.domain.collection

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlin.time.Instant

public enum class CollectionStatus {
    CURRENT,
    PLANNED,
    PAUSED,
    COMPLETED,
    DROPPED,
}

public data class CollectionEntry(
    public val id: CollectionEntryId,
    public val accountId: AccountId,
    public val mediaId: MediaId,
    public val status: CollectionStatus,
    public val progress: ProgressValue,
    public val progressUnit: ProgressUnit,
    public val total: UnitTotal?,
    public val updatedAt: Instant,
)
