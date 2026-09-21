package io.morgan.mio.data.provider.api

public interface ProviderMediaBrowseDataSource {
    public val provider: ProviderKey

    public fun categories(type: ProviderMediaType): List<ProviderBrowseCategory>

    public suspend fun browse(
        credentials: ProviderCredentials,
        request: ProviderMediaBrowseRequest,
    ): ProviderResult<List<ProviderMedia>>
}
