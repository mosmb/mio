package io.morgan.mio.data.media
import io.morgan.mio.data.provider.api.ProviderCredentials
class GoodRepository {
    private data class PrivateHelper(val credentials: ProviderCredentials)
}
internal data class InternalHelper(val credentials: ProviderCredentials)
class ProviderRemoteDataSource {
    fun read(): ProviderCredentials = TODO()
}
