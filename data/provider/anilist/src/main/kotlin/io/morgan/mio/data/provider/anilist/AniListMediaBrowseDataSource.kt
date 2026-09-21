package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderBrowseCategory
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaBrowseDataSource
import io.morgan.mio.data.provider.api.ProviderMediaBrowseRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException

internal class AniListMediaBrowseDataSource(
    private val service: AniListGraphQlService,
) : ProviderMediaBrowseDataSource {
    override val provider = ProviderKey.ANILIST

    override fun categories(type: ProviderMediaType): List<ProviderBrowseCategory> =
        buildList {
            add(ProviderBrowseCategory.TRENDING)
            if (type == ProviderMediaType.ANIME) {
                addAll(
                    listOf(
                        ProviderBrowseCategory.NEXT_SEASON,
                        ProviderBrowseCategory.THIS_SEASON,
                        ProviderBrowseCategory.AIRING,
                        ProviderBrowseCategory.TOP_RATED,
                        ProviderBrowseCategory.MOVIES,
                        ProviderBrowseCategory.COMPLETED,
                    ),
                )
            } else {
                addAll(
                    listOf(
                        ProviderBrowseCategory.PUBLISHING,
                        ProviderBrowseCategory.NEWLY_STARTED,
                        ProviderBrowseCategory.COMPLETED,
                        ProviderBrowseCategory.TOP_RATED,
                        ProviderBrowseCategory.ONE_SHOTS,
                    ),
                )
            }
            addAll(
                listOf(
                    ProviderBrowseCategory.ACTION,
                    ProviderBrowseCategory.FANTASY,
                    ProviderBrowseCategory.ROMANCE,
                    ProviderBrowseCategory.MYSTERY,
                    ProviderBrowseCategory.SLICE_OF_LIFE,
                    ProviderBrowseCategory.SCI_FI,
                ),
            )
        }

    override suspend fun browse(
        credentials: ProviderCredentials,
        request: ProviderMediaBrowseRequest,
    ): ProviderResult<List<ProviderMedia>> {
        require(credentials.provider == provider)
        require(request.category in categories(request.type))
        val category = request.category
        val seasonal = category == ProviderBrowseCategory.THIS_SEASON || category == ProviderBrowseCategory.NEXT_SEASON
        return try {
            val response =
                service.mediaSearch(
                    authorization = "${credentials.tokenType} ${credentials.accessToken}",
                    request =
                        AniListMediaSearchRequestDto(
                            query = QUERY,
                            variables =
                                AniListMediaSearchVariablesDto(
                                    type = request.type.name,
                                    page = 1,
                                    perPage = request.limit,
                                    sort =
                                        when (category) {
                                            ProviderBrowseCategory.TRENDING -> "TRENDING_DESC"
                                            ProviderBrowseCategory.TOP_RATED -> "SCORE_DESC"
                                            ProviderBrowseCategory.NEWLY_STARTED -> "START_DATE_DESC"
                                            else -> "POPULARITY_DESC"
                                        },
                                    status =
                                        when (category) {
                                            ProviderBrowseCategory.AIRING,
                                            ProviderBrowseCategory.PUBLISHING,
                                            ProviderBrowseCategory.NEWLY_STARTED,
                                            -> "RELEASING"
                                            ProviderBrowseCategory.COMPLETED -> "FINISHED"
                                            else -> null
                                        },
                                    format =
                                        when (category) {
                                            ProviderBrowseCategory.MOVIES -> "MOVIE"
                                            ProviderBrowseCategory.ONE_SHOTS -> "ONE_SHOT"
                                            else -> null
                                        },
                                    genre =
                                        when (category) {
                                            ProviderBrowseCategory.ACTION -> "Action"
                                            ProviderBrowseCategory.FANTASY -> "Fantasy"
                                            ProviderBrowseCategory.ROMANCE -> "Romance"
                                            ProviderBrowseCategory.MYSTERY -> "Mystery"
                                            ProviderBrowseCategory.SLICE_OF_LIFE -> "Slice of Life"
                                            ProviderBrowseCategory.SCI_FI -> "Sci-Fi"
                                            else -> null
                                        },
                                    season = request.season.takeIf { seasonal },
                                    seasonYear = request.year.takeIf { seasonal },
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
            if (items.size > request.limit) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            ProviderResult.Success(items)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(AniListFailureMapper.mapThrowable(throwable))
        }
    }

    private companion object {
        const val QUERY =
            "query MioMediaBrowse(\$type: MediaType!, \$page: Int!, \$perPage: Int!, \$sort: " +
                "[MediaSort], \$status: MediaStatus, \$format: MediaFormat, \$genre: String, " +
                "\$season: MediaSeason, \$seasonYear: Int) { Page(page: \$page, perPage: \$perPage) { " +
                "media(type: \$type, sort: \$sort, status: \$status, format: \$format, genre: " +
                "\$genre, season: \$season, seasonYear: \$seasonYear, isAdult: false) { id type " +
                "format status episodes chapters volumes description bannerImage genres seasonYear " +
                "averageScore title { userPreferred romaji english native } coverImage { extraLarge " +
                "large medium } } } }"
    }
}
