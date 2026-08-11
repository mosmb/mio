package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderInitialImportDataSource
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult

public class FakeProviderInitialImportDataSource(
    override val provider: ProviderKey,
) : ProviderInitialImportDataSource {
    private val handlers =
        ArrayDeque<suspend (ProviderCredentials, ProviderAccountId) -> ProviderResult<CompleteProviderImport>>()
    private val callsValue = mutableListOf<InitialImportCall>()

    public val calls: List<InitialImportCall>
        get() = callsValue.toList()

    public fun enqueue(
        handler: suspend (ProviderCredentials, ProviderAccountId) -> ProviderResult<CompleteProviderImport>,
    ) {
        handlers.addLast(handler)
    }

    override suspend fun fetchCompleteImport(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
    ): ProviderResult<CompleteProviderImport> {
        require(credentials.provider == provider)
        callsValue += InitialImportCall(credentials, accountId)
        val result =
            handlers.removeFirstOrNull()?.invoke(credentials, accountId)
                ?: error("No queued fetchCompleteImport handler for provider $provider")
        if (result is ProviderResult.Success) {
            check(result.value.provider == provider) {
                "Scripted import for ${result.value.provider} does not match fake provider $provider"
            }
        }
        return result
    }
}

public data class InitialImportCall(
    public val credentials: ProviderCredentials,
    public val accountId: ProviderAccountId,
)
