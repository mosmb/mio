package io.morgan.mio.data.collection

import io.morgan.mio.domain.model.AccountId

public fun interface CollectionRefreshRequester {
    public suspend fun refreshCollection(accountId: AccountId)
}
