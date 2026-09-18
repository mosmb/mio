package io.morgan.mio.data.provider.anilist

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

class AniListMediaDetailDataSourceTest {
    private lateinit var server: MockAniListServer
    private lateinit var dataSource: AniListMediaDetailDataSource

    private val credentials =
        ProviderCredentials(
            provider = ProviderKey.ANILIST,
            accessToken = "fixture-token",
            refreshToken = null,
            expiresAt = null,
            tokenType = "Bearer",
        )

    @Before
    fun setUp() {
        server = MockAniListServer()
        dataSource = AniListMediaDetailDataSource(server.service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun sendsOneValidatedDetailQueryAndMapsTheResponse() =
        runTest {
            server.enqueue(MockAniListServer.loadFixture("media-details.json"))

            val result = dataSource.fetchMediaDetails(credentials, ProviderMediaId("101")) as ProviderResult.Success
            val request = server.takeRequest()

            assertEquals(
                101,
                result.value.root.id.value
                    .toInt(),
            )
            assertEquals("/", request.target)
            assertEquals("Bearer fixture-token", request.headers["Authorization"])
            val body = request.body!!.utf8()
            assertTrue(body.contains("MioMediaDetails"))
            assertTrue(body.contains("relations"))
            assertTrue(body.contains("\"id\":101"))
            assertTrue(!body.contains("relationType"))
            assertEquals(1, server.requestCount())
        }

    @Test
    fun malformedIdAndHttpGraphQlFailuresAreNormalized() =
        runTest {
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.fetchMediaDetails(credentials, ProviderMediaId("not-a-number")),
            )
            server.enqueue("{}", code = 401)
            assertEquals(
                ProviderResult.Failure(ProviderFailure.AuthenticationRequired),
                dataSource.fetchMediaDetails(credentials, ProviderMediaId("101")),
            )
            server.enqueue("{\"errors\":[{\"status\":500}]}")
            assertEquals(
                ProviderResult.Failure(ProviderFailure.ServiceUnavailable),
                dataSource.fetchMediaDetails(credentials, ProviderMediaId("101")),
            )
        }

    @Test
    fun cancellationIsRethrownAndWrongCredentialsFailBeforeRequest() =
        runTest {
            val cancellation = CancellationException("fixture")
            val cancellingDataSource =
                AniListMediaDetailDataSource(
                    object : AniListGraphQlService {
                        override suspend fun viewer(
                            authorization: String,
                            request: AniListViewerRequestDto,
                        ): Response<AniListViewerResponseDto> = error("unused")

                        override suspend fun mediaListPage(
                            authorization: String,
                            request: AniListMediaListRequestDto,
                        ): Response<AniListMediaListResponseDto> = error("unused")

                        override suspend fun mediaDetails(
                            authorization: String,
                            request: AniListMediaDetailsRequestDto,
                        ): Response<AniListMediaDetailsResponseDto> = throw cancellation

                        override suspend fun mediaSearch(
                            authorization: String,
                            request: AniListMediaSearchRequestDto,
                        ): Response<AniListMediaSearchResponseDto> = error("unused")

                        override suspend fun syncEntry(
                            authorization: String,
                            request: AniListSyncEntryRequestDto,
                        ): Response<AniListSyncEntryResponseDto> = error("unused")

                        override suspend fun syncMutation(
                            authorization: String,
                            request: AniListSyncMutationRequestDto,
                        ): Response<AniListSyncMutationResponseDto> = error("unused")
                    },
                )
            val thrown =
                assertThrows(CancellationException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        cancellingDataSource.fetchMediaDetails(credentials, ProviderMediaId("101"))
                    }
                }
            assertSame(cancellation, thrown)

            val wrong =
                ProviderCredentials(
                    provider = ProviderKey.MY_ANIME_LIST,
                    accessToken = "fixture-token",
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                )
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    dataSource.fetchMediaDetails(wrong, ProviderMediaId("101"))
                }
            }
            assertEquals(0, server.requestCount())
        }
}
