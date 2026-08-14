package io.morgan.mio.domain.account

import kotlinx.coroutines.flow.StateFlow

public interface AccountSessionRepository {
    public val state: StateFlow<AccountSessionState>

    public suspend fun restore()

    public suspend fun startConnection(provider: ConnectedProvider): AccountAuthorizationLaunch?

    public suspend fun completeAuthorization(redirect: AccountAuthorizationRedirect)

    public suspend fun failAuthorizationLaunch()
}
