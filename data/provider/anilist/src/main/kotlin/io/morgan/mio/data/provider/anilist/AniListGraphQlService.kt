package io.morgan.mio.data.provider.anilist

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

internal interface AniListGraphQlService {
    @POST(".")
    suspend fun viewer(
        @Header("Authorization") authorization: String,
        @Body request: AniListViewerRequestDto,
    ): Response<AniListViewerResponseDto>

    @POST(".")
    suspend fun mediaListPage(
        @Header("Authorization") authorization: String,
        @Body request: AniListMediaListRequestDto,
    ): Response<AniListMediaListResponseDto>
}
