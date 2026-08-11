package io.morgan.mio.data.account

public fun interface AccountRefreshRequester {
    public suspend fun refreshSelectedAccount()
}
