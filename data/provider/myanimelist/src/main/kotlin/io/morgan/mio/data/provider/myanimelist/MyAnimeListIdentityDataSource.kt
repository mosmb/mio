package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderIdentityDataSource
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException

internal class MyAnimeListIdentityDataSource(
    private val service: MyAnimeListApiService,
) : ProviderIdentityDataSource {
    override val provider: ProviderKey = ProviderKey.MY_ANIME_LIST

    override suspend fun fetchIdentity(credentials: ProviderCredentials): ProviderResult<ProviderIdentity> {
        require(credentials.provider == provider)
        return try {
            val response = service.user("${credentials.tokenType} ${credentials.accessToken}")
            if (!response.isSuccessful) {
                return ProviderResult.Failure(
                    MyAnimeListFailureMapper.mapHttpStatus(response.code(), response.headers()["Retry-After"]),
                )
            }
            val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val id = body.id?.takeIf { it > 0 } ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val name =
                body.name?.takeIf(String::isNotBlank)
                    ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
            val avatar = body.picture?.takeIf(String::isNotBlank)?.let(::ProviderImageReference)
            val identity = ProviderIdentity(provider, ProviderAccountId(id.toString()), name, avatar)
            check(identity.provider == provider)
            ProviderResult.Success(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }
    }
}
