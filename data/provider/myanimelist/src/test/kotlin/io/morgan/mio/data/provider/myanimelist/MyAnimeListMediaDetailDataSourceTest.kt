package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class MyAnimeListMediaDetailDataSourceTest {
    private lateinit var server: MockMyAnimeListServer
    private lateinit var dataSource: MyAnimeListMediaDetailDataSource

    private val credentials =
        ProviderCredentials(
            provider = ProviderKey.MY_ANIME_LIST,
            accessToken = "fixture-token",
            refreshToken = null,
            expiresAt = null,
            tokenType = "Bearer",
        )

    @Before
    fun setUp() {
        server = MockMyAnimeListServer()
        dataSource = MyAnimeListMediaDetailDataSource(server.apiService)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun selectsOneFixedAnimeOrMangaEndpointAndRequestsNestedFields() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-details.json"))

            val result = dataSource.fetchMediaDetails(credentials, ProviderMediaId("anime/1")) as ProviderResult.Success
            val request = server.takeRequest()

            assertEquals("anime/1", result.value.root.id.value)
            assertTrue(request.target.startsWith("/anime/1?fields="))
            assertTrue(request.target.contains("related_anime"))
            assertTrue(request.target.contains("related_manga"))
            assertEquals("Bearer fixture-token", request.headers["Authorization"])
            assertEquals(1, server.requestCount())
        }

    @Test
    fun rejectsUnsafePathsBeforeRequestAndMapsHttpFailures() =
        runTest {
            listOf("anime/0", "anime/+1", "anime/1/related", "manga/ ").forEach { value ->
                assertEquals(
                    ProviderResult.Failure(ProviderFailure.MalformedResponse),
                    dataSource.fetchMediaDetails(credentials, ProviderMediaId(value)),
                )
            }
            server.enqueue("{}", code = 401)
            assertEquals(
                ProviderResult.Failure(ProviderFailure.AuthenticationRequired),
                dataSource.fetchMediaDetails(credentials, ProviderMediaId("manga/7")),
            )
        }

    @Test
    fun wrongCredentialsFailBeforeAnyRequest() =
        runTest {
            val wrong =
                ProviderCredentials(
                    provider = ProviderKey.ANILIST,
                    accessToken = "fixture-token",
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                )
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    dataSource.fetchMediaDetails(wrong, ProviderMediaId("anime/1"))
                }
            }
            assertEquals(0, server.requestCount())
        }

    @Test
    fun cancellationFromTheDetailRequestIsRethrownUnchanged() =
        runTest {
            val cancellation = CancellationException("fixture")
            val cancellingDataSource =
                MyAnimeListMediaDetailDataSource(
                    object : MyAnimeListApiService {
                        override suspend fun user(authorization: String): Response<MyAnimeListUserResponseDto> =
                            error("unused")

                        override suspend fun searchAnime(
                            authorization: String,
                            query: String,
                            fields: String,
                            limit: Int,
                            offset: Int,
                        ): Response<MyAnimeListMediaSearchResponseDto> = error("unused")

                        override suspend fun searchManga(
                            authorization: String,
                            query: String,
                            fields: String,
                            limit: Int,
                            offset: Int,
                        ): Response<MyAnimeListMediaSearchResponseDto> = error("unused")

                        override suspend fun animeList(
                            authorization: String,
                            fields: String,
                            limit: Int,
                            offset: Int,
                            nsfw: Boolean,
                        ): Response<MyAnimeListAnimeListResponseDto> = error("unused")

                        override suspend fun mangaList(
                            authorization: String,
                            fields: String,
                            limit: Int,
                            offset: Int,
                            nsfw: Boolean,
                        ): Response<MyAnimeListMangaListResponseDto> = error("unused")

                        override suspend fun animeDetails(
                            id: Int,
                            authorization: String,
                            fields: String,
                        ): Response<MyAnimeListMediaDetailsResponseDto> = throw cancellation

                        override suspend fun mangaDetails(
                            id: Int,
                            authorization: String,
                            fields: String,
                        ): Response<MyAnimeListMediaDetailsResponseDto> = error("unused")

                        override suspend fun syncAnimeDetails(
                            id: Int,
                            authorization: String,
                            fields: String,
                        ): Response<MyAnimeListSyncEntryResponseDto> = error("unused")

                        override suspend fun syncMangaDetails(
                            id: Int,
                            authorization: String,
                            fields: String,
                        ): Response<MyAnimeListSyncEntryResponseDto> = error("unused")

                        override suspend fun updateAnimeListStatus(
                            id: Int,
                            authorization: String,
                            status: String?,
                            numWatchedEpisodes: Int?,
                        ): Response<MyAnimeListSyncMutationResponseDto> = error("unused")

                        override suspend fun updateMangaListStatus(
                            id: Int,
                            authorization: String,
                            status: String?,
                            numChaptersRead: Int?,
                            numVolumesRead: Int?,
                        ): Response<MyAnimeListSyncMutationResponseDto> = error("unused")
                    },
                )

            val thrown =
                assertThrows(CancellationException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        cancellingDataSource.fetchMediaDetails(credentials, ProviderMediaId("anime/1"))
                    }
                }

            assertSame(cancellation, thrown)
        }
}
