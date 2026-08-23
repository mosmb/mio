package io.morgan.mio.data.provider.myanimelist

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

internal interface MyAnimeListApiService {
    @GET("users/@me")
    suspend fun user(
        @Header("Authorization") authorization: String,
    ): Response<MyAnimeListUserResponseDto>

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
}
