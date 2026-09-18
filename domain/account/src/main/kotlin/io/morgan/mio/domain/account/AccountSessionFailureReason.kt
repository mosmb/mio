package io.morgan.mio.domain.account

public enum class AccountSessionFailureReason {
    UNAVAILABLE,
    PROVIDER_FAILURE,
    AUTHORIZATION_LAUNCH_FAILED,
    CLEANUP_FAILED,
}
