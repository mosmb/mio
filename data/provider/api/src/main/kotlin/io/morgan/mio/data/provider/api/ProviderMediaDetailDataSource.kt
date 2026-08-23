package io.morgan.mio.data.provider.api

public interface ProviderMediaDetailDataSource {
    public val provider: ProviderKey

    public suspend fun fetchMediaDetails(
        credentials: ProviderCredentials,
        mediaId: ProviderMediaId,
    ): ProviderResult<ProviderMediaDetails>
}
