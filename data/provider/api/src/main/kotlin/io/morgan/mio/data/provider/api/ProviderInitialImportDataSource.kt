package io.morgan.mio.data.provider.api

public interface ProviderInitialImportDataSource {
    public val provider: ProviderKey

    public suspend fun fetchCompleteImport(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
    ): ProviderResult<CompleteProviderImport>
}
