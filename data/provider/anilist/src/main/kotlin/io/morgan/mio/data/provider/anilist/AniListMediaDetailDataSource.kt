package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaDetailDataSource
import io.morgan.mio.data.provider.api.ProviderMediaDetails
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException

internal class AniListMediaDetailDataSource(
    private val service: AniListGraphQlService,
) : ProviderMediaDetailDataSource {
    override val provider: ProviderKey = ProviderKey.ANILIST

    override suspend fun fetchMediaDetails(
        credentials: ProviderCredentials,
        mediaId: ProviderMediaId,
    ): ProviderResult<ProviderMediaDetails> {
        require(credentials.provider == provider)
        val remoteId =
            mediaId.value.toIntOrNull()?.takeIf { value -> value > 0 }
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)

        return try {
            val response =
                service.mediaDetails(
                    authorization = "${credentials.tokenType} ${credentials.accessToken}",
                    request =
                        AniListMediaDetailsRequestDto(
                            query = AniListQueries.MEDIA_DETAILS,
                            variables = AniListMediaDetailsVariablesDto(remoteId),
                        ),
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
                    status?.let { value -> AniListFailureMapper.mapHttpStatus(value, null) }
                        ?: ProviderFailure.MalformedResponse,
                )
            }
            val node = body.data?.media ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            AniListMediaDetailsMapper.map(remoteId, node)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(AniListFailureMapper.mapThrowable(throwable))
        }
    }
}
