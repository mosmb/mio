package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MyAnimeListSyncDataSourceTest {
    private lateinit var server: MockMyAnimeListServer
    private lateinit var dataSource: MyAnimeListSyncDataSource

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
        dataSource = MyAnimeListSyncDataSource(server.apiService)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun authenticatedLookupDistinguishesUntrackedFromMalformedIdentity() =
        runTest {
            server.enqueue("""{"id":101,"my_list_status":null}""")
            assertEquals(
                null,
                (
                    dataSource.findEntry(
                        credentials,
                        ProviderAccountId("42"),
                        ProviderMediaId("anime/101"),
                        ProviderProgressUnit.EPISODE,
                    ) as ProviderResult.Success
                ).value,
            )
            server.enqueue("""{"id":999,"my_list_status":null}""")
            assertTrue(
                dataSource.findEntry(
                    credentials,
                    ProviderAccountId("42"),
                    ProviderMediaId("anime/101"),
                    ProviderProgressUnit.EPISODE,
                ) is ProviderResult.Failure,
            )
        }

    @Test
    fun animeMutationUsesTheAsymmetricAbsoluteRequestKeyAndReadBack() =
        runTest {
            server.enqueue(animeEntry(progress = 4))
            server.enqueue("", code = 204)
            server.enqueue(animeEntry(progress = 5))

            val result =
                dataSource.setProgress(
                    credentials,
                    ProviderAccountId("42"),
                    ProviderCollectionEntryId("anime/1"),
                    ProviderMediaId("anime/1"),
                    ProviderProgressUnit.EPISODE,
                    5,
                ) as ProviderResult.Success

            assertEquals(5, result.value.progress)
            val get = server.takeRequest()
            val patch = server.takeRequest()
            assertTrue(get.target.contains("num_episodes_watched"))
            assertTrue(get.target.contains("my_list_status"))
            assertEquals("GET", get.method)
            assertEquals("PATCH", patch.method)
            assertEquals("num_watched_episodes=5", patch.body!!.utf8())
            assertFalse(patch.body!!.utf8().contains("status="))
        }

    @Test
    fun volumeMutationDoesNotSendTheChapterCounter() =
        runTest {
            server.enqueue(mangaEntry(chaptersRead = 0, volumesRead = 1))
            server.enqueue("", code = 204)
            server.enqueue(mangaEntry(chaptersRead = 0, volumesRead = 2))

            val result =
                dataSource.setProgress(
                    credentials,
                    ProviderAccountId("42"),
                    ProviderCollectionEntryId("manga/2"),
                    ProviderMediaId("manga/2"),
                    ProviderProgressUnit.VOLUME,
                    2,
                ) as ProviderResult.Success

            assertEquals(ProviderProgressUnit.VOLUME, result.value.progressUnit)
            assertEquals(2, result.value.progress)
            server.takeRequest()
            val patch = server.takeRequest()
            assertEquals("num_volumes_read=2", patch.body!!.utf8())
            assertFalse(patch.body!!.utf8().contains("num_chapters_read"))
        }

    @Test
    fun createUsesMediaListPostAndRemovalUsesMatchingAnimeOrMangaEndpoint() =
        runTest {
            server.enqueue("{}", code = 201)
            server.enqueue(animeEntry(progress = 0))

            val created =
                dataSource.createEntry(
                    credentials,
                    ProviderAccountId("42"),
                    ProviderMediaId("anime/1"),
                    ProviderProgressUnit.EPISODE,
                    ProviderCollectionStatus.CURRENT,
                    0,
                ) as ProviderResult.Success

            assertEquals("anime/1", created.value.id.value)
            val post = server.takeRequest()
            assertEquals("PATCH", post.method)
            assertTrue(post.target.contains("/anime/1/my_list_status"))
            assertEquals("status=watching&num_watched_episodes=0", post.body!!.utf8())
            assertEquals("GET", server.takeRequest().method)

            server.enqueue("", code = 204)
            assertTrue(
                dataSource.removeEntry(
                    credentials,
                    ProviderAccountId("42"),
                    ProviderCollectionEntryId("manga/2"),
                    ProviderMediaId("manga/2"),
                    ProviderProgressUnit.VOLUME,
                ) is ProviderResult.Success,
            )
            val delete = server.takeRequest()
            assertEquals("DELETE", delete.method)
            assertTrue(delete.target.contains("/manga/2/my_list_status"))
        }

    private fun animeEntry(progress: Int): String =
        """{"id":1,"num_episodes":12,"my_list_status":{"status":"watching","num_episodes_watched":$progress,"is_rewatching":false,"updated_at":"2026-09-11T10:00:00+00:00"}}"""

    private fun mangaEntry(
        chaptersRead: Int,
        volumesRead: Int,
    ): String =
        """{"id":2,"num_chapters":30,"num_volumes":5,"my_list_status":{"status":"reading","num_chapters_read":$chaptersRead,"num_volumes_read":$volumesRead,"is_rereading":false,"updated_at":"2026-09-11T10:00:00+00:00"}}"""
}
