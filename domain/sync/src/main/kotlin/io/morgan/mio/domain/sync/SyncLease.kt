package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.AccountId
import kotlin.time.Instant

public data class SyncLease(
    public val accountId: AccountId,
    public val ownerToken: String,
    public val expiresAt: Instant,
) {
    init {
        require(ownerToken.isNotBlank())
    }
}
