package io.morgan.mio.data.provider.api

public data class ProviderAuthorizationRequest(
    public val redirectUri: String,
) {
    init {
        require(redirectUri.isNotBlank())
    }
}
