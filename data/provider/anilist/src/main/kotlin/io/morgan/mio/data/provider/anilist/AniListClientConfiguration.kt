package io.morgan.mio.data.provider.anilist

public data class AniListClientConfiguration(
    public val clientId: String,
    public val redirectUri: String,
    public val authorizationEndpoint: String = AniListEndpoints.AUTHORIZATION_ENDPOINT,
    public val apiBaseUrl: String = AniListEndpoints.API_BASE_URL,
) {
    init {
        require(clientId.isNotBlank())
        require(redirectUri.isNotBlank())
        require(authorizationEndpoint.isNotBlank())
        require(apiBaseUrl.endsWith("/"))
    }
}
