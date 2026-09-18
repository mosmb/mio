package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaSearchRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.provider.api.ProviderSearchCursor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MyAnimeListMediaSearchDataSourceTest {
    private lateinit var server: MockMyAnimeListServer
    private lateinit var dataSource: MyAnimeListMediaSearchDataSource

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
        dataSource = MyAnimeListMediaSearchDataSource(server.apiService)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun searchesAnimeAndMangaWithNamespacedIdsAndMALNextOffsets() =
        runTest {
            val nextUrl =
                server.baseUrl
                    .newBuilder()
                    .addPathSegments("manga")
                    .addQueryParameter("q", "castle")
                    .addQueryParameter("limit", "20")
                    .addQueryParameter("offset", "20")
                    .build()
            server.enqueue(
                """
                {"data":[{"node":{"id":12,"title":"Castle Story","media_type":"manga","status":"finished"}}],
                 "paging":{"next":"$nextUrl"}}
                """.trimIndent(),
            )

            val first =
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest("castle", ProviderMediaType.MANGA, 20, cursor = null),
                ) as ProviderResult.Success
            val firstRequest = server.takeRequest()

            assertEquals(
                "manga/12",
                first.value.items
                    .single()
                    .id.value,
            )
            assertEquals(
                ProviderMediaType.MANGA,
                first.value.items
                    .single()
                    .type,
            )
            assertEquals("20", first.value.nextCursor?.value)
            assertTrue(firstRequest.target.startsWith("/manga?q=castle"))
            assertTrue(firstRequest.target.contains("limit=20"))
            assertTrue(firstRequest.target.contains("offset=0"))
            assertTrue(firstRequest.target.contains("fields="))
            assertEquals("Bearer fixture-token", firstRequest.headers["Authorization"])

            server.enqueue("{\"data\":[],\"paging\":{}}")
            val continuation =
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest(
                        "castle",
                        ProviderMediaType.MANGA,
                        20,
                        cursor = ProviderSearchCursor("20"),
                    ),
                ) as ProviderResult.Success
            val continuationRequest = server.takeRequest()
            assertTrue(continuation.value.items.isEmpty())
            assertEquals(null, continuation.value.nextCursor)
            assertTrue(continuationRequest.target.startsWith("/manga?q=castle"))
            assertTrue(continuationRequest.target.contains("offset=20"))

            server.enqueue(
                "{\"data\":[{\"node\":{\"id\":7,\"title\":\"Anime Story\",\"media_type\":\"tv\"}}],\"paging\":{}}",
            )
            val anime =
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest("anime story", ProviderMediaType.ANIME, 20, cursor = null),
                ) as ProviderResult.Success
            val animeRequest = server.takeRequest()
            assertEquals(
                "anime/7",
                anime.value.items
                    .single()
                    .id.value,
            )
            assertEquals("/anime", animeRequest.target.substringBefore('?'))
            assertEquals(null, anime.value.nextCursor)
        }

    @Test
    fun rejectsUnsafeContinuationAndMapsAuthenticationFailures() =
        runTest {
            server.enqueue(
                """{"data":[],"paging":{"next":"https://attacker.invalid/v2/anime?q=title&limit=20&offset=20"}}""",
            )
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest("title", ProviderMediaType.ANIME, 20, cursor = null),
                ),
            )
            assertEquals(1, server.requestCount())

            server.enqueue("{}", code = 401)
            assertEquals(
                ProviderResult.Failure(ProviderFailure.AuthenticationRequired),
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest("title", ProviderMediaType.ANIME, 20, cursor = null),
                ),
            )
        }
}
