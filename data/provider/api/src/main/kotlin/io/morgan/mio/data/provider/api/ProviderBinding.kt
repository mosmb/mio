package io.morgan.mio.data.provider.api

public class ProviderBinding(
    public val provider: ProviderKey,
    public val authenticator: ProviderAuthenticator,
    public val identityDataSource: ProviderIdentityDataSource,
    public val initialImportDataSource: ProviderInitialImportDataSource,
) {
    init {
        require(authenticator.provider == provider)
        require(identityDataSource.provider == provider)
        require(initialImportDataSource.provider == provider)
    }
}
