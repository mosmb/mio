package io.morgan.mio.data.provider.api

public interface ProviderCredentialStore {
    public suspend fun read(provider: ProviderKey): ProviderCredentials?

    public suspend fun write(credentials: ProviderCredentials)

    public suspend fun delete(provider: ProviderKey)

    public suspend fun deleteAll()
}

public class ProviderCredentialStorageException : RuntimeException {
    public constructor() : super("Secure credential storage failed")

    override fun toString(): String = "ProviderCredentialStorageException([REDACTED])"
}
