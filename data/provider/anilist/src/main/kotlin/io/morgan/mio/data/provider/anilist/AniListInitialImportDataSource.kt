package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderInitialImportDataSource
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException

internal class AniListInitialImportDataSource(
    private val service: AniListGraphQlService,
) : ProviderInitialImportDataSource {
    override val provider: ProviderKey = ProviderKey.ANILIST

    override suspend fun fetchCompleteImport(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
    ): ProviderResult<CompleteProviderImport> {
        require(credentials.provider == provider)
        val userId =
            accountId.value.toIntOrNull()?.takeIf { value -> value > 0 }
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)

        return try {
            val collectedEntries = mutableListOf<AniListMediaListEntryDto>()
            for (type in listOf("ANIME", "MANGA")) {
                var page = 1
                while (true) {
                    if (page > AniListQueries.MAX_MEDIA_LIST_PAGES) {
                        return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                    }
                    val response =
                        service.mediaListPage(
                            authorization = "${credentials.tokenType} ${credentials.accessToken}",
                            request =
                                AniListMediaListRequestDto(
                                    query = AniListQueries.MEDIA_LIST_PAGE,
                                    variables =
                                        AniListMediaListVariablesDto(
                                            userId = userId,
                                            type = type,
                                            page = page,
                                            perPage = AniListQueries.MEDIA_LIST_PAGE_SIZE,
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
                            if (status == null) {
                                ProviderFailure.MalformedResponse
                            } else {
                                AniListFailureMapper.mapHttpStatus(status, null)
                            },
                        )
                    }
                    val pageDto = body.data?.page ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                    collectedEntries += pageDto.mediaList.orEmpty().filterNotNull()

                    if (pageDto.pageInfo?.hasNextPage != true) break
                    page++
                }
            }

            when (val mapped = AniListMediaListMapper.toCompleteImport(accountId, collectedEntries)) {
                is ProviderResult.Success -> {
                    val import = mapped.value
                    check(import.provider == provider)
                    check(import.accountId == accountId)
                    ProviderResult.Success(import)
                }

                is ProviderResult.Failure -> mapped
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(AniListFailureMapper.mapThrowable(throwable))
        }
    }
}
