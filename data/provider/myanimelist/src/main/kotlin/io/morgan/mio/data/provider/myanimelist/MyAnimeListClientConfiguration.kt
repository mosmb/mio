package io.morgan.mio.data.provider.myanimelist

public data class MyAnimeListClientConfiguration(
    public val clientId: String,
    public val redirectUri: String,
    public val authorizationEndpoint: String = MyAnimeListEndpoints.AUTHORIZATION_ENDPOINT,
    public val tokenBaseUrl: String = MyAnimeListEndpoints.TOKEN_BASE_URL,
    public val apiBaseUrl: String = MyAnimeListEndpoints.API_BASE_URL,
) {
    init {
        require(clientId.isNotBlank())
        require(redirectUri.isNotBlank())
        require(authorizationEndpoint.isNotBlank())
        require(tokenBaseUrl.endsWith("/"))
        require(apiBaseUrl.endsWith("/"))
    }
}
