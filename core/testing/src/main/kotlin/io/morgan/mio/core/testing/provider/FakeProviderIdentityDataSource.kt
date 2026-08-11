package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderIdentityDataSource
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult

public class FakeProviderIdentityDataSource(
    override val provider: ProviderKey,
) : ProviderIdentityDataSource {
    private val handlers =
        ArrayDeque<suspend (ProviderCredentials) -> ProviderResult<ProviderIdentity>>()
    private val credentialsValue = mutableListOf<ProviderCredentials>()

    public val credentials: List<ProviderCredentials>
        get() = credentialsValue.toList()

    public fun enqueue(handler: suspend (ProviderCredentials) -> ProviderResult<ProviderIdentity>) {
        handlers.addLast(handler)
    }

    override suspend fun fetchIdentity(credentials: ProviderCredentials): ProviderResult<ProviderIdentity> {
        require(credentials.provider == provider)
        credentialsValue += credentials
        val result =
            handlers.removeFirstOrNull()?.invoke(credentials)
                ?: error("No queued fetchIdentity handler for provider $provider")
        if (result is ProviderResult.Success) {
            check(result.value.provider == provider) {
                "Scripted identity for ${result.value.provider} does not match fake provider $provider"
            }
        }
        return result
    }
}
