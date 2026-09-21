package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderBrowseCategory
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaBrowseDataSource
import io.morgan.mio.data.provider.api.ProviderMediaBrowseRequest
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException

internal class MyAnimeListMediaBrowseDataSource(
    private val service: MyAnimeListApiService,
) : ProviderMediaBrowseDataSource {
    override val provider = ProviderKey.MY_ANIME_LIST

    override fun categories(type: ProviderMediaType): List<ProviderBrowseCategory> =
        buildList {
            add(ProviderBrowseCategory.POPULAR)
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
                        ProviderBrowseCategory.COMPLETED,
                        ProviderBrowseCategory.TOP_RATED,
                        ProviderBrowseCategory.ONE_SHOTS,
                    ),
                )
            }
        }

    override suspend fun browse(
        credentials: ProviderCredentials,
        request: ProviderMediaBrowseRequest,
    ): ProviderResult<List<ProviderMedia>> {
        require(credentials.provider == provider)
        require(request.category in categories(request.type))
        return try {
            val kind = if (request.type == ProviderMediaType.ANIME) "anime" else "manga"
            val fields =
                if (request.type ==
                    ProviderMediaType.ANIME
                ) {
                    MyAnimeListRequestParameters.ANIME_SEARCH_FIELDS
                } else {
                    MyAnimeListRequestParameters.MANGA_SEARCH_FIELDS
                }
            val auth = "${credentials.tokenType} ${credentials.accessToken}"
            val response =
                when (request.category) {
                    ProviderBrowseCategory.THIS_SEASON, ProviderBrowseCategory.NEXT_SEASON ->
                        service.season(
                            request.year,
                            request.season.lowercase(),
                            auth,
                            fields,
                            request.limit,
                        )
                    else ->
                        service.ranking(
                            kind,
                            auth,
                            when (request.category) {
                                ProviderBrowseCategory.TOP_RATED -> "all"
                                ProviderBrowseCategory.AIRING -> "airing"
                                ProviderBrowseCategory.MOVIES -> "movie"
                                ProviderBrowseCategory.ONE_SHOTS -> "oneshots"
                                else -> "bypopularity"
                            },
                            fields,
                            if (request.category in
                                listOf(ProviderBrowseCategory.PUBLISHING, ProviderBrowseCategory.COMPLETED)
                            ) {
                                100
                            } else {
                                request.limit
                            },
                        )
                }
            if (!response.isSuccessful) {
                return ProviderResult.Failure(
                    MyAnimeListFailureMapper.mapHttpStatus(response.code(), response.headers()["Retry-After"]),
                )
            }
            val entries = response.body()?.data ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            if (entries.size > 100) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val items = mutableListOf<ProviderMedia>()
            for (entry in entries) {
                val node = entry?.node ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                val remoteId =
                    node.id?.takeIf { it > 0 } ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                val media =
                    when (val result = MyAnimeListMediaDetailsMapper.map("$kind/$remoteId", node)) {
                        is ProviderResult.Success -> result.value.root
                        is ProviderResult.Failure -> return result
                    }
                if (media.type != request.type) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                if (request.category == ProviderBrowseCategory.PUBLISHING &&
                    media.publicationStatus != ProviderMediaPublicationStatus.RELEASING
                ) {
                    continue
                }
                if (request.category == ProviderBrowseCategory.COMPLETED &&
                    media.publicationStatus != ProviderMediaPublicationStatus.FINISHED
                ) {
                    continue
                }
                items += media
            }
            ProviderResult.Success(items.take(request.limit))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }
    }
}
