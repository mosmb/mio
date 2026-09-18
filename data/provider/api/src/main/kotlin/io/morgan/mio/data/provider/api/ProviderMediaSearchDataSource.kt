package io.morgan.mio.data.provider.api

public interface ProviderMediaSearchDataSource {
    public val provider: ProviderKey

    public suspend fun search(
        credentials: ProviderCredentials,
        request: ProviderMediaSearchRequest,
    ): ProviderResult<ProviderMediaSearchPage>
}
