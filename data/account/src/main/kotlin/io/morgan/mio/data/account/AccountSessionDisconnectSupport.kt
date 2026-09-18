package io.morgan.mio.data.account

import io.morgan.mio.domain.model.AccountId

/** App-owned operations that the account session must finish as part of local disconnect. */
public interface AccountSessionDisconnectSupport {
    public suspend fun cancelAccountWork(accountId: AccountId)

    public suspend fun clearAccountPreferences(accountId: AccountId)

    /** Reports a redacted cleanup failure. Implementations must not include account or provider data. */
    public fun reportCleanupFailure()
}
