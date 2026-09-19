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

    @POST(".")
    suspend fun mediaDetails(
        @Header("Authorization") authorization: String,
        @Body request: AniListMediaDetailsRequestDto,
    ): Response<AniListMediaDetailsResponseDto>

    @POST(".")
    suspend fun mediaSearch(
        @Header("Authorization") authorization: String,
        @Body request: AniListMediaSearchRequestDto,
    ): Response<AniListMediaSearchResponseDto>

    @POST(".")
    suspend fun syncEntry(
        @Header("Authorization") authorization: String,
        @Body request: AniListSyncEntryRequestDto,
    ): Response<AniListSyncEntryResponseDto>

    @POST(".")
    suspend fun syncMutation(
        @Header("Authorization") authorization: String,
        @Body request: AniListSyncMutationRequestDto,
    ): Response<AniListSyncMutationResponseDto>

    @POST(".")
    suspend fun syncDelete(
        @Header("Authorization") authorization: String,
        @Body request: AniListSyncDeleteRequestDto,
    ): Response<AniListSyncMutationResponseDto>
}
