package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaDetailDataSource
import io.morgan.mio.data.provider.api.ProviderMediaDetails
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderResult

public class FakeProviderMediaDetailDataSource(
    override val provider: ProviderKey,
) : ProviderMediaDetailDataSource {
    private val handlers =
        ArrayDeque<suspend (ProviderCredentials, ProviderMediaId) -> ProviderResult<ProviderMediaDetails>>()
    private val callsValue = mutableListOf<MediaDetailCall>()

    public val calls: List<MediaDetailCall>
        get() = callsValue.toList()

    public fun enqueue(
        handler: suspend (ProviderCredentials, ProviderMediaId) -> ProviderResult<ProviderMediaDetails>,
    ) {
        handlers.addLast(handler)
    }

    override suspend fun fetchMediaDetails(
        credentials: ProviderCredentials,
        mediaId: ProviderMediaId,
    ): ProviderResult<ProviderMediaDetails> {
        require(credentials.provider == provider)
        callsValue += MediaDetailCall(credentials, mediaId)
        val result =
            handlers.removeFirstOrNull()?.invoke(credentials, mediaId)
                ?: error("No queued fetchMediaDetails handler for provider $provider")
        if (result is ProviderResult.Success) {
            check(result.value.provider == provider) {
                "Scripted details for ${result.value.provider} do not match fake provider $provider"
            }
        }
        return result
    }
}

public data class MediaDetailCall(
    public val credentials: ProviderCredentials,
    public val mediaId: ProviderMediaId,
)
