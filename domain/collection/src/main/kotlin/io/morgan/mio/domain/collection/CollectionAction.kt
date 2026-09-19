package io.morgan.mio.domain.collection

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import kotlin.time.Instant

public data class CollectionAction(
    public val id: OperationGroupId,
    public val accountId: AccountId,
    public val entryId: CollectionEntryId,
    public val mediaId: MediaId,
    public val kind: CollectionActionKind,
    public val before: CollectionEntry?,
    public val after: CollectionEntry,
    public val createdAt: Instant,
    public val undoUntil: Instant,
    public val undoneAt: Instant?,
)
