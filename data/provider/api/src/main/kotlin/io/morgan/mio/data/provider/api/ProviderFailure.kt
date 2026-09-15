package io.morgan.mio.data.provider.api

import kotlin.time.Duration

public sealed interface ProviderResult<out T> {
    public data class Success<T>(
        public val value: T,
    ) : ProviderResult<T>

    public data class Failure(
        public val failure: ProviderFailure,
    ) : ProviderResult<Nothing>
}

public sealed interface ProviderFailure {
    public val recovery: ProviderRecovery

    public data object NetworkUnavailable : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.RETRY
    }

    public data object Timeout : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.RETRY
    }

    public data class RateLimited(
        public val retryAfter: Duration?,
    ) : ProviderFailure {
        init {
            require(retryAfter == null || retryAfter.isFinite() && !retryAfter.isNegative())
        }

        override val recovery: ProviderRecovery = ProviderRecovery.RETRY
    }

    public data object ServiceUnavailable : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.RETRY
    }

    public data object AuthenticationRequired : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.REAUTHENTICATE
    }

    public data object AuthorizationDenied : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.DO_NOT_RETRY
    }

    public data object InvalidAuthorizationCallback : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.DO_NOT_RETRY
    }

    public data object MalformedResponse : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.DO_NOT_RETRY
    }

    public data object UnsupportedMandatoryData : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.DO_NOT_RETRY
    }

    public data object MutationRejected : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.DO_NOT_RETRY
    }

    public data object MutationConflict : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.DO_NOT_RETRY
    }

    public data object Unexpected : ProviderFailure {
        override val recovery: ProviderRecovery = ProviderRecovery.DO_NOT_RETRY
    }
}
