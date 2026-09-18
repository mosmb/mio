package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaSearchDataSource
import io.morgan.mio.data.provider.api.ProviderMediaSearchPage
import io.morgan.mio.data.provider.api.ProviderMediaSearchRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.provider.api.ProviderSearchCursor
import kotlinx.coroutines.CancellationException

internal class AniListMediaSearchDataSource(
    private val service: AniListGraphQlService,
) : ProviderMediaSearchDataSource {
    override val provider: ProviderKey = ProviderKey.ANILIST

    override suspend fun search(
        credentials: ProviderCredentials,
        request: ProviderMediaSearchRequest,
    ): ProviderResult<ProviderMediaSearchPage> {
        require(credentials.provider == provider)
        if (request.limit > MAX_PAGE_SIZE) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val cursor = request.cursor
        val page = cursor?.value?.toIntOrNull() ?: if (cursor == null) FIRST_PAGE else 0
        val lastPage = MAX_RESULT_DEPTH / request.limit
        if (page !in FIRST_PAGE..lastPage) return ProviderResult.Failure(ProviderFailure.MalformedResponse)

        return try {
            val response =
                service.mediaSearch(
                    authorization = "${credentials.tokenType} ${credentials.accessToken}",
                    request =
                        AniListMediaSearchRequestDto(
                            query = AniListQueries.MEDIA_SEARCH,
                            variables =
                                AniListMediaSearchVariablesDto(
                                    search = request.query,
                                    type = request.type.toAniListValue(),
                                    page = page,
                                    perPage = request.limit,
                                ),
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
            val searchPage = body.data?.page ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val nodes = searchPage.media ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val hasNext =
                searchPage.pageInfo?.hasNextPage ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val items = mutableListOf<ProviderMedia>()
            nodes.forEach { node ->
                val mediaNode = node ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                val remoteId =
                    mediaNode.id?.takeIf { value -> value > 0 }
                        ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                val mapped =
                    when (val result = AniListMediaDetailsMapper.map(remoteId, mediaNode)) {
                        is ProviderResult.Success -> result.value.root
                        is ProviderResult.Failure -> return result
                    }
                if (mapped.type != request.type) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                items += mapped
            }
            ProviderResult.Success(
                ProviderMediaSearchPage(
                    items = items,
                    nextCursor = if (hasNext && page < lastPage) ProviderSearchCursor((page + 1).toString()) else null,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(AniListFailureMapper.mapThrowable(throwable))
        }
    }

    private fun ProviderMediaType.toAniListValue(): String =
        when (this) {
            ProviderMediaType.ANIME -> "ANIME"
            ProviderMediaType.MANGA -> "MANGA"
        }

    private companion object {
        const val FIRST_PAGE: Int = 1
        const val MAX_PAGE_SIZE: Int = 50
        const val MAX_RESULT_DEPTH: Int = 5000
    }
}
