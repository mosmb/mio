package io.morgan.mio.data.provider.api

public class ProviderAuthorizationLaunch(
    public val authorizationUrl: String,
    public val transaction: ProviderAuthorizationTransaction,
) {
    init {
        require(authorizationUrl.isNotBlank())
    }

    override fun toString(): String = "ProviderAuthorizationLaunch([REDACTED])"
}
