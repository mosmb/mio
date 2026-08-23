package io.morgan.mio.data.account

import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId

public interface AuthenticatedAccountSessionActivator {
    public fun activate(
        accountId: AccountId,
        provider: ConnectedProvider,
    )

    public fun destroy()
}
