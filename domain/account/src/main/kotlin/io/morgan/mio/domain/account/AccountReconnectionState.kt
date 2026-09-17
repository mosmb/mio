package io.morgan.mio.domain.account

import io.morgan.mio.domain.model.AccountId

public sealed interface AccountReconnectionState {
    public data object Idle : AccountReconnectionState

    public data class Authorizing(
        public val accountId: AccountId,
    ) : AccountReconnectionState

    public data class Succeeded(
        public val accountId: AccountId,
    ) : AccountReconnectionState

    public data class Failed(
        public val accountId: AccountId,
        public val reason: AccountSessionFailureReason,
    ) : AccountReconnectionState
}
