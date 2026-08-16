package io.morgan.mio.data.provider.api

public class ProviderAuthorizationTransaction(
    public val state: String,
    public val codeVerifier: String?,
) {
    init {
        require(state.isNotBlank())
        require(codeVerifier == null || codeVerifier.isNotBlank())
    }

    override fun toString(): String = "ProviderAuthorizationTransaction([REDACTED])"
}
