package io.morgan.mio.data.provider.api

public class ProviderBinding(
    public val provider: ProviderKey,
    public val authenticator: ProviderAuthenticator,
    public val identityDataSource: ProviderIdentityDataSource,
    public val initialImportDataSource: ProviderInitialImportDataSource,
    public val mediaDetailDataSource: ProviderMediaDetailDataSource,
    public val mediaSearchDataSource: ProviderMediaSearchDataSource,
    public val mediaBrowseDataSource: ProviderMediaBrowseDataSource? = null,
    public val syncDataSource: ProviderSyncDataSource,
) {
    init {
        require(authenticator.provider == provider)
        require(identityDataSource.provider == provider)
        require(initialImportDataSource.provider == provider)
        require(mediaDetailDataSource.provider == provider)
        require(mediaSearchDataSource.provider == provider)
        require(mediaBrowseDataSource == null || mediaBrowseDataSource.provider == provider)
        require(syncDataSource.provider == provider)
    }
}
