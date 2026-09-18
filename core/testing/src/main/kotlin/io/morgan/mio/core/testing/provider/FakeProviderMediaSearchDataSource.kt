package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaSearchDataSource
import io.morgan.mio.data.provider.api.ProviderMediaSearchPage
import io.morgan.mio.data.provider.api.ProviderMediaSearchRequest
import io.morgan.mio.data.provider.api.ProviderResult

public class FakeProviderMediaSearchDataSource(
    override val provider: ProviderKey,
) : ProviderMediaSearchDataSource {
    private val handlers =
        ArrayDeque<
            suspend (
                ProviderCredentials,
                ProviderMediaSearchRequest,
            ) -> ProviderResult<ProviderMediaSearchPage>,
        >()
    private val callsValue = mutableListOf<MediaSearchCall>()

    public val calls: List<MediaSearchCall>
        get() = callsValue.toList()

    public fun enqueue(
        handler: suspend (ProviderCredentials, ProviderMediaSearchRequest) -> ProviderResult<ProviderMediaSearchPage>,
    ) {
        handlers.addLast(handler)
    }

    override suspend fun search(
        credentials: ProviderCredentials,
        request: ProviderMediaSearchRequest,
    ): ProviderResult<ProviderMediaSearchPage> {
        require(credentials.provider == provider)
        callsValue += MediaSearchCall(credentials, request)
        val result =
            handlers.removeFirstOrNull()?.invoke(credentials, request)
                ?: error("No queued search handler for provider $provider")
        if (result is ProviderResult.Success) {
            check(
                result.value.items.all { item ->
                    when (provider) {
                        ProviderKey.ANILIST -> !item.id.value.contains('/')
                        ProviderKey.MY_ANIME_LIST ->
                            item.id.value.startsWith("anime/") ||
                                item.id.value.startsWith("manga/")
                    }
                },
            ) {
                "Scripted media identities do not match fake provider $provider"
            }
        }
        return result
    }
}

public data class MediaSearchCall(
    public val credentials: ProviderCredentials,
    public val request: ProviderMediaSearchRequest,
)
