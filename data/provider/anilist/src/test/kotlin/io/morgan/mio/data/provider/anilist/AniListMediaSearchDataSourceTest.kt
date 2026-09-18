package io.morgan.mio.data.provider.anilist

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

class AniListMediaSearchDataSourceTest {
    private lateinit var server: MockAniListServer
    private lateinit var dataSource: AniListMediaSearchDataSource

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
        dataSource = AniListMediaSearchDataSource(server.service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun mapsTypeAndContinuationAndSendsQueryVariables() =
        runTest {
            server.enqueue(
                """
                {"data":{"Page":{"pageInfo":{"hasNextPage":true},"media":[
                  {"id":203,"type":"MANGA","format":"MANGA","status":"FINISHED",
                   "title":{"userPreferred":"A Castle in the Sky"},"coverImage":{"large":"https://images.invalid/castle.jpg"}}
                ]}}}
                """.trimIndent(),
            )

            val result =
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest(
                        query = "castle in the sky",
                        type = ProviderMediaType.MANGA,
                        limit = 20,
                        cursor = ProviderSearchCursor("2"),
                    ),
                ) as ProviderResult.Success
            val request = server.takeRequest()
            val body = request.body!!.utf8()

            assertEquals(
                ProviderMediaType.MANGA,
                result.value.items
                    .single()
                    .type,
            )
            assertEquals(
                "203",
                result.value.items
                    .single()
                    .id.value,
            )
            assertEquals(
                "A Castle in the Sky",
                result.value.items
                    .single()
                    .title,
            )
            assertEquals("3", result.value.nextCursor?.value)
            assertEquals("Bearer fixture-token", request.headers["Authorization"])
            assertTrue(body.contains("\"search\":\"castle in the sky\""))
            assertTrue(body.contains("\"type\":\"MANGA\""))
            assertTrue(body.contains("\"page\":2"))
            assertTrue(body.contains("\"perPage\":20"))
        }

    @Test
    fun finalAccessiblePageRemainsUsableWithoutOfferingAnUnreachableContinuation() =
        runTest {
            server.enqueue("""{"data":{"Page":{"pageInfo":{"hasNextPage":true},"media":[]}}}""")
            val result =
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest("title", ProviderMediaType.ANIME, 10, ProviderSearchCursor("500")),
                ) as ProviderResult.Success
            assertEquals(null, result.value.nextCursor)
        }

    @Test
    fun exhaustedPageHasNoCursorAndMalformedOrFailedResponsesAreRejected() =
        runTest {
            server.enqueue("{\"data\":{\"Page\":{\"pageInfo\":{\"hasNextPage\":false},\"media\":[]}}}")
            val exhausted =
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest("empty title", ProviderMediaType.ANIME, 20, cursor = null),
                ) as ProviderResult.Success
            assertTrue(exhausted.value.items.isEmpty())
            assertEquals(null, exhausted.value.nextCursor)

            server.enqueue(
                """{"data":{"Page":{"pageInfo":{"hasNextPage":false},"media":[{"id":5,"type":"ANIME"}]}}}""",
            )
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.search(
                    credentials,
                    ProviderMediaSearchRequest("bad title", ProviderMediaType.MANGA, 20, cursor = null),
                ),
            )
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
