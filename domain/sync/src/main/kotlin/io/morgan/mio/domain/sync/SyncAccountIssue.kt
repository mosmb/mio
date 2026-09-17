package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.AccountId
import kotlin.time.Instant

public data class SyncAccountIssue(
    public val id: String,
    public val accountId: AccountId,
    public val kind: SyncAccountIssueKind,
    public val detectedAt: Instant,
) {
    init {
        require(id.isNotBlank())
    }
}
