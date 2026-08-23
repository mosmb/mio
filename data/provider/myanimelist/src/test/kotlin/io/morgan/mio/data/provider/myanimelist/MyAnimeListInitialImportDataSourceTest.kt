package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MyAnimeListInitialImportDataSourceTest {
    private val sentinelToken = "fixture-obvious-sentinel-token"
    private val accountId = ProviderAccountId("12345")
    private val credentials =
        ProviderCredentials(
            provider = ProviderKey.MY_ANIME_LIST,
            accessToken = sentinelToken,
            refreshToken = null,
            expiresAt = null,
            tokenType = "Bearer",
        )

    private lateinit var server: MockMyAnimeListServer
    private lateinit var dataSource: MyAnimeListInitialImportDataSource

    @Before
    fun setUp() {
        server = MockMyAnimeListServer()
        dataSource = MyAnimeListInitialImportDataSource(server.apiService)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun emptyPage() = """{"data":[],"paging":{}}"""

    @Test
    fun twoAnimePagesPlusOneMangaPageProduceOneCompleteImport() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-list-page-1.json"))
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-list-page-2.json"))
            server.enqueue(MockMyAnimeListServer.loadFixture("manga-list.json"))

            val result = dataSource.fetchCompleteImport(credentials, accountId) as ProviderResult.Success

            assertEquals(ProviderKey.MY_ANIME_LIST, result.value.provider)
            assertEquals(accountId, result.value.accountId)
            // three anime, plus two distinct manga (the third manga row repeats remote id 1)
            assertEquals(
                listOf("anime/1", "anime/2", "anime/3", "manga/1", "manga/2"),
                result.value.media.map { media -> media.id.value },
            )
            assertEquals(5, result.value.entries.size)
            assertEquals(3, server.requestCount())
        }

    @Test
    fun everyRequestSendsTheExpectedPathFieldsAndPagingParameters() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-list-page-1.json"))
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-list-page-2.json"))
            server.enqueue(MockMyAnimeListServer.loadFixture("manga-list.json"))

            dataSource.fetchCompleteImport(credentials, accountId)

            val targets = List(3) { server.takeRequest().target }

            assertTrue(targets[0].startsWith("/users/@me/animelist?"))
            assertTrue(targets[1].startsWith("/users/@me/animelist?"))
            assertTrue(targets[2].startsWith("/users/@me/mangalist?"))

            // The first anime page returns two entries, so the second request offsets by two, not by limit.
            assertTrue(targets[0].contains("offset=0"))
            assertTrue(targets[1].contains("offset=2"))
            assertTrue(targets[2].contains("offset=0"))

            targets.forEach { target ->
                assertTrue(target.contains("nsfw=true"))
                assertTrue(target.contains("limit=100"))
                assertTrue(!target.contains("sort"))
            }
            assertTrue(targets[0].contains("num_episodes"))
            assertTrue(targets[0].contains("list_status"))
            assertTrue(targets[2].contains("num_chapters"))
            assertTrue(targets[2].contains("num_volumes"))
            assertTrue(targets.none { target -> target.contains("related_anime") || target.contains("related_manga") })
        }

    @Test
    fun anEmptyCollectionProducesAValidEmptyImport() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-list-empty.json"))
            server.enqueue(emptyPage())

            val result = dataSource.fetchCompleteImport(credentials, accountId) as ProviderResult.Success

            assertEquals(emptyList<String>(), result.value.media.map { media -> media.id.value })
            assertEquals(emptyList<String>(), result.value.entries.map { entry -> entry.id.value })
        }

    @Test
    fun aFailureOnTheSecondAnimePageYieldsTheMappedFailureAndNoSnapshot() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-list-page-1.json"))
            server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = 401)

            val result = dataSource.fetchCompleteImport(credentials, accountId)

            assertEquals(ProviderResult.Failure(ProviderFailure.AuthenticationRequired), result)
            assertEquals(2, server.requestCount())
        }

    @Test
    fun aFailureOnTheMangaListYieldsNoSnapshotEvenAfterTheAnimeListSucceeded() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-list-page-2.json"))
            server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = 503)

            val result = dataSource.fetchCompleteImport(credentials, accountId)

            assertEquals(ProviderResult.Failure(ProviderFailure.ServiceUnavailable), result)
        }

    @Test
    fun aProviderThatAlwaysReportsAnotherPageIsMalformedAfterTheCap() =
        runTest {
            val alwaysMore =
                """
                {"data":[{"node":{"id":1,"title":"Endless"},
                "list_status":{"status":"watching","num_episodes_watched":0,
                "updated_at":"2026-04-25T15:59:05+09:00"}}],
                "paging":{"next":"https://api.myanimelist.invalid/next"}}
                """.trimIndent()
            repeat(MyAnimeListRequestParameters.MAX_LIST_PAGES) { server.enqueue(alwaysMore) }

            val result = dataSource.fetchCompleteImport(credentials, accountId)

            assertEquals(ProviderResult.Failure(ProviderFailure.MalformedResponse), result)
            assertEquals(MyAnimeListRequestParameters.MAX_LIST_PAGES, server.requestCount())
        }

    @Test
    fun anEmptyPageStopsTheLoopEvenWhenAnotherPageIsAdvertised() =
        runTest {
            server.enqueue("""{"data":[],"paging":{"next":"https://api.myanimelist.invalid/next"}}""")
            server.enqueue(emptyPage())

            val result = dataSource.fetchCompleteImport(credentials, accountId) as ProviderResult.Success

            assertEquals(emptyList<String>(), result.value.media.map { media -> media.id.value })
            assertEquals(2, server.requestCount())
        }

    @Test
    fun aMalformedBodyIsMalformedResponse() =
        runTest {
            server.enqueue("not json at all")

            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.fetchCompleteImport(credentials, accountId),
            )
        }

    @Test
    fun cancellingTheCallingCoroutineRethrowsCancellation() =
        runTest {
            val deferred = async { dataSource.fetchCompleteImport(credentials, accountId) }
            deferred.cancel()

            val thrown =
                try {
                    deferred.await()
                    null
                } catch (caught: CancellationException) {
                    caught
                }

            assertTrue(thrown is CancellationException)
        }

    @Test
    fun credentialsForAnotherProviderThrowBeforeAnyRequest() =
        runTest {
            val foreign =
                ProviderCredentials(
                    provider = ProviderKey.ANILIST,
                    accessToken = sentinelToken,
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                )

            val thrown =
                try {
                    dataSource.fetchCompleteImport(foreign, accountId)
                    null
                } catch (caught: IllegalArgumentException) {
                    caught
                }

            assertTrue(thrown is IllegalArgumentException)
            assertEquals(0, server.requestCount())
        }

    @Test
    fun noReleaseIsEverImportedForAnyMyAnimeListMedia() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("anime-list-page-2.json"))
            server.enqueue(MockMyAnimeListServer.loadFixture("manga-list.json"))

            val result = dataSource.fetchCompleteImport(credentials, accountId) as ProviderResult.Success

            assertTrue(result.value.media.isNotEmpty())
            assertTrue(result.value.media.all { media -> media.releases.isEmpty() })
            assertTrue(result.value.media.all { media -> media.relatedMediaIds.isEmpty() })
            assertTrue(result.value.media.all { media -> media.banner == null })
        }
}
