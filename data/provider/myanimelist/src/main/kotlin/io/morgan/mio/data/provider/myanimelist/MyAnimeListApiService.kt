package io.morgan.mio.data.provider.myanimelist

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

internal interface MyAnimeListApiService {
    @GET("{kind}/ranking")
    suspend fun ranking(
        @Path("kind") kind: String,
        @Header("Authorization") authorization: String,
        @Query("ranking_type") rankingType: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
    ): Response<MyAnimeListMediaSearchResponseDto>

    @GET("anime/season/{year}/{season}")
    suspend fun season(
        @Path("year") year: Int,
        @Path("season") season: String,
        @Header("Authorization") authorization: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
        @Query("sort") sort: String = "anime_num_list_users",
    ): Response<MyAnimeListMediaSearchResponseDto>

    @GET("users/@me")
    suspend fun user(
        @Header("Authorization") authorization: String,
    ): Response<MyAnimeListUserResponseDto>

    @GET("anime")
    suspend fun searchAnime(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): Response<MyAnimeListMediaSearchResponseDto>

    @GET("manga")
    suspend fun searchManga(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): Response<MyAnimeListMediaSearchResponseDto>

    @GET("users/@me/animelist")
    suspend fun animeList(
        @Header("Authorization") authorization: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("nsfw") nsfw: Boolean,
    ): Response<MyAnimeListAnimeListResponseDto>

    @GET("users/@me/mangalist")
    suspend fun mangaList(
        @Header("Authorization") authorization: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("nsfw") nsfw: Boolean,
    ): Response<MyAnimeListMangaListResponseDto>

    @GET("anime/{id}")
    suspend fun animeDetails(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
        @Query("fields") fields: String,
    ): Response<MyAnimeListMediaDetailsResponseDto>

    @GET("manga/{id}")
    suspend fun mangaDetails(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
        @Query("fields") fields: String,
    ): Response<MyAnimeListMediaDetailsResponseDto>

    @GET("anime/{id}")
    suspend fun syncAnimeDetails(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
        @Query("fields") fields: String,
    ): Response<MyAnimeListSyncEntryResponseDto>

    @GET("manga/{id}")
    suspend fun syncMangaDetails(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
        @Query("fields") fields: String,
    ): Response<MyAnimeListSyncEntryResponseDto>

    @FormUrlEncoded
    @PATCH("anime/{id}/my_list_status")
    suspend fun updateAnimeListStatus(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
        @Field("status") status: String? = null,
        @Field("num_watched_episodes") numWatchedEpisodes: Int? = null,
    ): Response<MyAnimeListSyncMutationResponseDto>

    @FormUrlEncoded
    @PATCH("manga/{id}/my_list_status")
    suspend fun updateMangaListStatus(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
        @Field("status") status: String? = null,
        @Field("num_chapters_read") numChaptersRead: Int? = null,
        @Field("num_volumes_read") numVolumesRead: Int? = null,
    ): Response<MyAnimeListSyncMutationResponseDto>

    @DELETE("anime/{id}/my_list_status")
    suspend fun deleteAnimeListStatus(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
    ): Response<Unit>

    @DELETE("manga/{id}/my_list_status")
    suspend fun deleteMangaListStatus(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
    ): Response<Unit>
}
