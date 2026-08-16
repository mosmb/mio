package io.morgan.mio.data.provider.api

public interface ProviderIdentityDataSource {
    public val provider: ProviderKey

    public suspend fun fetchIdentity(credentials: ProviderCredentials): ProviderResult<ProviderIdentity>
}
