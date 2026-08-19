package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderInitialImportDataSource
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException

internal class MyAnimeListInitialImportDataSource(
    private val service: MyAnimeListApiService,
) : ProviderInitialImportDataSource {
    override val provider: ProviderKey = ProviderKey.MY_ANIME_LIST

    override suspend fun fetchCompleteImport(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
    ): ProviderResult<CompleteProviderImport> {
        require(credentials.provider == provider)
        val authorization = "${credentials.tokenType} ${credentials.accessToken}"
        return try {
            val animeEntries = mutableListOf<MyAnimeListAnimeListEntryDto>()
            var animeOffset = 0
            var animePage = 0
            while (true) {
                if (animePage >= MyAnimeListRequestParameters.MAX_LIST_PAGES) {
                    return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                }
                val response =
                    service.animeList(
                        authorization,
                        MyAnimeListRequestParameters.ANIME_FIELDS,
                        MyAnimeListRequestParameters.LIST_PAGE_SIZE,
                        animeOffset,
                        nsfw = true,
                    )
                if (!response.isSuccessful) {
                    return ProviderResult.Failure(
                        MyAnimeListFailureMapper.mapHttpStatus(response.code(), response.headers()["Retry-After"]),
                    )
                }
                val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                val entries = body.data.orEmpty().filterNotNull()
                animeEntries.addAll(entries)
                if (entries.isEmpty() || body.paging?.next.isNullOrBlank()) {
                    break
                }
                animeOffset += entries.size
                animePage++
            }

            val mangaEntries = mutableListOf<MyAnimeListMangaListEntryDto>()
            var mangaOffset = 0
            var mangaPage = 0
            while (true) {
                if (mangaPage >= MyAnimeListRequestParameters.MAX_LIST_PAGES) {
                    return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                }
                val response =
                    service.mangaList(
                        authorization,
                        MyAnimeListRequestParameters.MANGA_FIELDS,
                        MyAnimeListRequestParameters.LIST_PAGE_SIZE,
                        mangaOffset,
                        nsfw = true,
                    )
                if (!response.isSuccessful) {
                    return ProviderResult.Failure(
                        MyAnimeListFailureMapper.mapHttpStatus(response.code(), response.headers()["Retry-After"]),
                    )
                }
                val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                val entries = body.data.orEmpty().filterNotNull()
                mangaEntries.addAll(entries)
                if (entries.isEmpty() || body.paging?.next.isNullOrBlank()) {
                    break
                }
                mangaOffset += entries.size
                mangaPage++
            }

            val result = MyAnimeListMediaListMapper.toCompleteImport(accountId, animeEntries, mangaEntries)
            if (result is ProviderResult.Success) {
                check(result.value.provider == provider)
                check(result.value.accountId == accountId)
            }
            result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }
    }
}
