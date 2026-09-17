package io.morgan.mio.domain.account

import kotlinx.coroutines.flow.StateFlow

public interface AccountReconnectionRepository {
    public val state: StateFlow<AccountReconnectionState>

    public suspend fun start(): AccountAuthorizationLaunch?

    public suspend fun complete(redirect: AccountAuthorizationRedirect)

    public suspend fun cancel()
}
