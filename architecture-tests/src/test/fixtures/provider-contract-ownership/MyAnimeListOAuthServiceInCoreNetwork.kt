package io.morgan.mio.core.network

internal interface MyAnimeListOAuthService {
    suspend fun exchangeAuthorizationCode(): io.morgan.mio.data.provider.api.ProviderResult<io.morgan.mio.data.provider.api.ProviderCredentials>
}
