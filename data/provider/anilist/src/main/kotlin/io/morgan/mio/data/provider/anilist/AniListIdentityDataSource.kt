package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderIdentityDataSource
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException

internal class AniListIdentityDataSource(
    private val service: AniListGraphQlService,
) : ProviderIdentityDataSource {
    override val provider: ProviderKey = ProviderKey.ANILIST

    override suspend fun fetchIdentity(credentials: ProviderCredentials): ProviderResult<ProviderIdentity> {
        require(credentials.provider == provider)
        return try {
            val response =
                service.viewer(
                    authorization = "${credentials.tokenType} ${credentials.accessToken}",
                    request = AniListViewerRequestDto(AniListQueries.VIEWER),
                )
            if (!response.isSuccessful) {
                return ProviderResult.Failure(
                    AniListFailureMapper.mapHttpStatus(response.code(), response.headers()["Retry-After"]),
                )
            }
            val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val errors = body.errors?.filterNotNull().orEmpty()
            if (errors.isNotEmpty()) {
                val status = errors.first().status
                return ProviderResult.Failure(
                    if (status == null) {
                        ProviderFailure.MalformedResponse
                    } else {
                        AniListFailureMapper.mapHttpStatus(status, null)
                    },
                )
            }
            val viewer = body.data?.viewer ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val id =
                viewer.id?.takeIf { value -> value > 0 }
                    ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val name =
                viewer.name?.takeIf(String::isNotBlank)
                    ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
            val avatar =
                (viewer.avatar?.large ?: viewer.avatar?.medium)
                    ?.takeIf(String::isNotBlank)
                    ?.let(::ProviderImageReference)

            val identity =
                ProviderIdentity(
                    provider = ProviderKey.ANILIST,
                    accountId = ProviderAccountId(id.toString()),
                    displayName = name,
                    avatar = avatar,
                )
            check(identity.provider == provider)
            ProviderResult.Success(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(AniListFailureMapper.mapThrowable(throwable))
        }
    }
}
