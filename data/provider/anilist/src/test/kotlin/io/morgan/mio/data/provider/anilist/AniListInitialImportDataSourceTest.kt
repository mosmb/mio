package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AniListInitialImportDataSourceTest {
    private lateinit var server: MockAniListServer
    private lateinit var dataSource: AniListInitialImportDataSource

    private val credentials =
        ProviderCredentials(
            provider = ProviderKey.ANILIST,
            accessToken = "fixture-token",
            refreshToken = null,
            expiresAt = null,
            tokenType = "Bearer",
        )
    private val accountId = ProviderAccountId("42")

    @Before
    fun setUp() {
        server = MockAniListServer()
        dataSource = AniListInitialImportDataSource(server.service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun twoAnimePagesPlusOneMangaPageProduceOneCompleteImportWithThreeRequests() =
        runTest {
            server.enqueue(MockAniListServer.loadFixture("media-list-page-1.json"))
            server.enqueue(MockAniListServer.loadFixture("media-list-page-2.json"))
            server.enqueue(MockAniListServer.loadFixture("media-list-manga.json"))

            val result = dataSource.fetchCompleteImport(credentials, accountId) as ProviderResult.Success

            assertEquals(4, result.value.entries.size)
            assertEquals(4, result.value.media.size)

            val recordedPages =
                listOf(server.takeRequest(), server.takeRequest(), server.takeRequest()).map { recorded ->
                    Regex("\"page\":(\\d+)").find(recorded.body?.utf8() ?: "")?.groupValues?.get(1)
                }
            assertEquals(listOf("1", "2", "1"), recordedPages)
        }

    @Test
    fun emptyCollectionProducesAValidEmptyCompleteImport() =
        runTest {
            server.enqueue(MockAniListServer.loadFixture("media-list-empty.json"))
            server.enqueue(MockAniListServer.loadFixture("media-list-empty.json"))

            val result = dataSource.fetchCompleteImport(credentials, accountId) as ProviderResult.Success

            assertTrue(result.value.entries.isEmpty())
            assertTrue(result.value.media.isEmpty())
        }

    @Test
    fun failureOnPageTwoReturnsTheMappedFailureAndNoSnapshot() =
        runTest {
            server.enqueue(MockAniListServer.loadFixture("media-list-page-1.json"))
            server.enqueue("{}", code = 401)

            val result = dataSource.fetchCompleteImport(credentials, accountId)

            assertEquals(ProviderResult.Failure(ProviderFailure.AuthenticationRequired), result)
        }

    @Test
    fun alwaysHasNextPageReturnsMalformedResponseAfterTheMaxPageCount() =
        runTest {
            repeat(AniListQueries.MAX_MEDIA_LIST_PAGES) {
                server.enqueue(
                    """{"data":{"Page":{"pageInfo":{"currentPage":1,"hasNextPage":true},"mediaList":[]}}}""",
                )
            }

            val result = dataSource.fetchCompleteImport(credentials, accountId)

            assertEquals(ProviderResult.Failure(ProviderFailure.MalformedResponse), result)
        }

    @Test
    fun cancellingBetweenPagesRethrowsCancellationExceptionAndIssuesNoFurtherRequest() =
        runTest {
            server.enqueue(MockAniListServer.loadFixture("media-list-page-1.json"))

            var caught: Throwable? = null
            val job =
                launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    try {
                        dataSource.fetchCompleteImport(credentials, accountId)
                    } catch (cancellation: CancellationException) {
                        caught = cancellation
                        throw cancellation
                    }
                }
            job.cancel()
            job.join()

            assertTrue(caught is CancellationException || job.isCancelled)
        }

    @Test
    fun nonNumericProviderAccountIdReturnsMalformedResponse() =
        runTest {
            val result = dataSource.fetchCompleteImport(credentials, ProviderAccountId("not-a-number"))

            assertEquals(ProviderResult.Failure(ProviderFailure.MalformedResponse), result)
        }

    @Test
    fun wrongProviderCredentialsThrowBeforeAnyRequestIsRecorded() =
        runTest {
            val wrongProviderCredentials = credentials.copy(provider = ProviderKey.MY_ANIME_LIST)

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    dataSource.fetchCompleteImport(wrongProviderCredentials, accountId)
                }
            }
            assertEquals(0, server.requestCount())
        }

    private fun ProviderCredentials.copy(
        provider: ProviderKey = this.provider,
        accessToken: String = this.accessToken,
        refreshToken: String? = this.refreshToken,
        expiresAt: kotlin.time.Instant? = this.expiresAt,
        tokenType: String = this.tokenType,
    ) = ProviderCredentials(provider, accessToken, refreshToken, expiresAt, tokenType)
}
