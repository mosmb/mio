package io.morgan.mio.domain.account

public class AccountAuthorizationRedirect(
    public val redirectUri: String,
) {
    init {
        require(redirectUri.isNotBlank())
    }

    override fun toString(): String = "AccountAuthorizationRedirect([REDACTED])"
}
