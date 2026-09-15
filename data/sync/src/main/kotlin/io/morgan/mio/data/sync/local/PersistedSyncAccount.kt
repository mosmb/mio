package io.morgan.mio.data.sync.local

import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.model.AccountId

public data class PersistedSyncAccount(
    public val accountId: AccountId,
    public val provider: ProviderKey,
)
