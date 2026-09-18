package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaSearchDataSource
import io.morgan.mio.data.provider.api.ProviderMediaSearchPage
import io.morgan.mio.data.provider.api.ProviderMediaSearchRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.provider.api.ProviderSearchCursor
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class MyAnimeListMediaSearchDataSource(
    private val service: MyAnimeListApiService,
) : ProviderMediaSearchDataSource {
    override val provider: ProviderKey = ProviderKey.MY_ANIME_LIST

    override suspend fun search(
        credentials: ProviderCredentials,
        request: ProviderMediaSearchRequest,
    ): ProviderResult<ProviderMediaSearchPage> {
        require(credentials.provider == provider)
        if (request.limit > MAX_PAGE_SIZE) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val cursor = request.cursor
        val offset = cursor?.value?.toIntOrNull()?.takeIf { value -> value >= 0 } ?: FIRST_OFFSET
        if (cursor != null && cursor.value != offset.toString()) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }

        return try {
            val authorization = "${credentials.tokenType} ${credentials.accessToken}"
            val response =
                when (request.type) {
                    ProviderMediaType.ANIME ->
                        service.searchAnime(
                            authorization = authorization,
                            query = request.query,
                            fields = MyAnimeListRequestParameters.ANIME_SEARCH_FIELDS,
                            limit = request.limit,
                            offset = offset,
                        )
                    ProviderMediaType.MANGA ->
                        service.searchManga(
                            authorization = authorization,
                            query = request.query,
                            fields = MyAnimeListRequestParameters.MANGA_SEARCH_FIELDS,
                            limit = request.limit,
                            offset = offset,
                        )
                }
            if (!response.isSuccessful) {
                return ProviderResult.Failure(
                    MyAnimeListFailureMapper.mapHttpStatus(response.code(), response.headers()["Retry-After"]),
                )
            }
            val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val entries = body.data ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val paging = body.paging ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val kind = if (request.type == ProviderMediaType.ANIME) "anime" else "manga"
            val items = mutableListOf<io.morgan.mio.data.provider.api.ProviderMedia>()
            entries.forEach { entry ->
                val node = entry?.node ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                val remoteId =
                    node.id?.takeIf { value -> value > 0 }
                        ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                val details =
                    when (val result = MyAnimeListMediaDetailsMapper.map("$kind/$remoteId", node)) {
                        is ProviderResult.Success -> result.value.root
                        is ProviderResult.Failure -> return result
                    }
                if (details.type != request.type) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                items += details
            }
            val nextOffset =
                paging.next?.let { next ->
                    nextOffset(
                        next = next,
                        currentUrl = response.raw().request.url,
                        query = request.query,
                        limit = request.limit,
                        currentOffset = offset,
                    ) ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                }
            ProviderResult.Success(
                ProviderMediaSearchPage(
                    items = items,
                    nextCursor = nextOffset?.let { value -> ProviderSearchCursor(value.toString()) },
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }
    }

    private fun nextOffset(
        next: String,
        currentUrl: HttpUrl,
        query: String,
        limit: Int,
        currentOffset: Int,
    ): Int? {
        val nextUrl = next.toHttpUrlOrNull() ?: return null
        if (
            nextUrl.scheme != currentUrl.scheme ||
            nextUrl.host != currentUrl.host ||
            nextUrl.encodedPath != currentUrl.encodedPath ||
            nextUrl.queryParameter("q") != query ||
            nextUrl.queryParameter("limit")?.toIntOrNull() != limit
        ) {
            return null
        }
        return nextUrl
            .queryParameter("offset")
            ?.toIntOrNull()
            ?.takeIf { value -> value > currentOffset }
    }

    private companion object {
        const val FIRST_OFFSET: Int = 0
        const val MAX_PAGE_SIZE: Int = 100
    }
}
