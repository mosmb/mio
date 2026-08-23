package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaDetailDataSource
import io.morgan.mio.data.provider.api.ProviderMediaDetails
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException

internal class MyAnimeListMediaDetailDataSource(
    private val service: MyAnimeListApiService,
) : ProviderMediaDetailDataSource {
    override val provider: ProviderKey = ProviderKey.MY_ANIME_LIST

    override suspend fun fetchMediaDetails(
        credentials: ProviderCredentials,
        mediaId: ProviderMediaId,
    ): ProviderResult<ProviderMediaDetails> {
        require(credentials.provider == provider)
        val match =
            Regex("^(anime|manga)/([1-9][0-9]*)$").matchEntire(mediaId.value)
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val kind = match.groupValues[1]
        val remoteId =
            match.groupValues[2].toIntOrNull()
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)

        return try {
            val response =
                if (kind == "anime") {
                    service.animeDetails(
                        id = remoteId,
                        authorization = "${credentials.tokenType} ${credentials.accessToken}",
                        fields = MyAnimeListRequestParameters.ANIME_DETAIL_FIELDS,
                    )
                } else {
                    service.mangaDetails(
                        id = remoteId,
                        authorization = "${credentials.tokenType} ${credentials.accessToken}",
                        fields = MyAnimeListRequestParameters.MANGA_DETAIL_FIELDS,
                    )
                }
            if (!response.isSuccessful) {
                return ProviderResult.Failure(
                    MyAnimeListFailureMapper.mapHttpStatus(response.code(), response.headers()["Retry-After"]),
                )
            }
            val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            MyAnimeListMediaDetailsMapper.map(mediaId.value, body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }
    }
}
