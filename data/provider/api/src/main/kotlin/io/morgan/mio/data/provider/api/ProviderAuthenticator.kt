package io.morgan.mio.data.provider.api

public interface ProviderAuthenticator {
    public val provider: ProviderKey

    public suspend fun beginAuthorization(
        request: ProviderAuthorizationRequest,
    ): ProviderResult<ProviderAuthorizationLaunch>

    public suspend fun completeAuthorization(
        transaction: ProviderAuthorizationTransaction,
        redirect: ProviderAuthorizationRedirect,
    ): ProviderResult<ProviderCredentials>

    public suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials>
}
