package io.morgan.mio.domain.account

import io.morgan.mio.domain.model.AccountId

public sealed interface AccountSessionState {
    public data object Restoring : AccountSessionState

    public data object SignedOut : AccountSessionState

    public data class Authorizing(
        public val provider: ConnectedProvider,
    ) : AccountSessionState

    public data class Importing(
        public val provider: ConnectedProvider,
    ) : AccountSessionState

    public data class Authenticated(
        public val accountId: AccountId,
        public val provider: ConnectedProvider,
    ) : AccountSessionState

    public data class Disconnecting(
        public val accountId: AccountId,
        public val provider: ConnectedProvider,
    ) : AccountSessionState

    public data class Failed(
        public val provider: ConnectedProvider?,
        public val operation: AccountSessionOperation,
        public val reason: AccountSessionFailureReason,
        public val retryable: Boolean,
    ) : AccountSessionState
}
