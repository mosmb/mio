package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SerializedTokenRefresh(
    private val attempt: suspend (ProviderCredentials) -> ProviderResult<ProviderCredentials>,
) {
    private val mutex = Mutex()

    suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials> =
        mutex.withLock { attempt(credentials) }
}
