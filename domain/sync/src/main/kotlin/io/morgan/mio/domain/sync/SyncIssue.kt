package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import kotlin.time.Instant

public data class SyncIssue(
    public val accountId: AccountId,
    public val entryId: CollectionEntryId,
    public val groupId: OperationGroupId,
    public val operationId: OperationId,
    public val kind: SyncIssueKind,
    public val base: OperationValue,
    public val target: OperationValue,
    public val local: SyncEntryState,
    public val remote: SyncEntryState?,
    public val detectedAt: Instant,
)
