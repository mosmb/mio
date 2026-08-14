package io.morgan.mio.domain.account

public class AccountAuthorizationLaunch(
    public val authorizationUri: String,
) {
    init {
        require(authorizationUri.isNotBlank())
    }

    override fun toString(): String = "AccountAuthorizationLaunch([REDACTED])"
}
