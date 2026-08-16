package io.morgan.mio.data.provider.api

public class ProviderAuthorizationRedirect(
    public val redirectUri: String,
) {
    init {
        require(redirectUri.isNotBlank())
    }

    override fun toString(): String = "ProviderAuthorizationRedirect([REDACTED])"
}
