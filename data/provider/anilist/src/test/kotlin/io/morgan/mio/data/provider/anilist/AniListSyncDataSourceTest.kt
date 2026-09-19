package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
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

class AniListSyncDataSourceTest {
    private lateinit var server: MockAniListServer
    private lateinit var dataSource: AniListSyncDataSource

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
        dataSource = AniListSyncDataSource(server.service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun authenticatedLookupDistinguishesAbsentAndExistingMembership() =
        runTest {
            server.enqueue("""{"data":{"MediaList":null}}""")
            assertEquals(
                null,
                (
                    dataSource.findEntry(
                        credentials,
                        ProviderAccountId("42"),
                        ProviderMediaId("101"),
                        ProviderProgressUnit.EPISODE,
                    ) as ProviderResult.Success
                ).value,
            )
            val request = server.takeRequest().body!!.utf8()
            assertTrue(request.contains("MioFindSyncEntry"))
            assertTrue(request.contains("\"userId\":42"))
            server.enqueue(entry(progress = 5))
            assertEquals(
                5,
                (
                    dataSource.findEntry(
                        credentials,
                        ProviderAccountId("42"),
                        ProviderMediaId("101"),
                        ProviderProgressUnit.EPISODE,
                    ) as ProviderResult.Success
                ).value?.progress,
            )
        }

    @Test
    fun progressMutationUsesTheAbsoluteFieldAndValidatesTheReadBack() =
        runTest {
            server.enqueue(entry(progress = 4))
            server.enqueue("""{"data":{"SaveMediaListEntry":{"id":7}}}""")
            server.enqueue(entry(progress = 5))

            val result =
                dataSource.setProgress(
                    credentials = credentials,
                    accountId = ProviderAccountId("42"),
                    entryId = ProviderCollectionEntryId("7"),
                    mediaId = ProviderMediaId("101"),
                    unit = ProviderProgressUnit.EPISODE,
                    target = 5,
                ) as ProviderResult.Success

            assertEquals(5, result.value.progress)
            val preflight = server.takeRequest()
            val mutation = server.takeRequest()
            assertTrue(preflight.body!!.utf8().contains("MioSyncEntry"))
            val mutationBody = mutation.body!!.utf8()
            assertTrue(mutationBody.contains("MioSaveMediaListEntry"))
            assertTrue(mutationBody.contains("\"progress\":5"))
            assertFalse(mutationBody.contains("\"progressVolumes\":5"))
            assertFalse(mutationBody.contains("\"status\":\""))
            assertEquals(3, server.requestCount())
        }

    @Test
    fun volumeMutationKeepsTheRequestedUnitAndDoesNotWriteChapterProgress() =
        runTest {
            server.enqueue(entry(progress = 0, progressVolumes = 1, totalEpisodes = null, totalVolumes = 4))
            server.enqueue("""{"data":{"SaveMediaListEntry":{"id":7}}}""")
            server.enqueue(entry(progress = 0, progressVolumes = 2, totalEpisodes = null, totalVolumes = 4))

            val result =
                dataSource.setProgress(
                    credentials = credentials,
                    accountId = ProviderAccountId("42"),
                    entryId = ProviderCollectionEntryId("7"),
                    mediaId = ProviderMediaId("101"),
                    unit = ProviderProgressUnit.VOLUME,
                    target = 2,
                ) as ProviderResult.Success

            assertEquals(ProviderProgressUnit.VOLUME, result.value.progressUnit)
            assertEquals(2, result.value.progress)
            val mutationBody =
                server
                    .takeRequest()
                    .let { server.takeRequest() }
                    .body!!
                    .utf8()
            assertTrue(mutationBody.contains("\"progressVolumes\":2"))
            assertFalse(mutationBody.contains("\"progress\":2"))
        }

    @Test
    fun createUsesMediaIdentityAndRemovalUsesReturnedEntryIdentity() =
        runTest {
            server.enqueue("""{"data":{"SaveMediaListEntry":{"id":7}}}""")
            server.enqueue(entry(progress = 0))

            val created =
                dataSource.createEntry(
                    credentials = credentials,
                    accountId = ProviderAccountId("42"),
                    mediaId = ProviderMediaId("101"),
                    unit = ProviderProgressUnit.EPISODE,
                    status = io.morgan.mio.data.provider.api.ProviderCollectionStatus.CURRENT,
                    progress = 0,
                ) as ProviderResult.Success

            assertEquals("7", created.value.id.value)
            val createBody = server.takeRequest().body!!.utf8()
            assertTrue(createBody.contains("\"mediaId\":101"))
            assertTrue(createBody.contains("\"status\":\"CURRENT\""))
            assertFalse(createBody.contains("\"id\":7"))
            server.takeRequest()
            server.enqueue("""{"data":{"DeleteMediaListEntry":{"deleted":true}}}""")
            assertTrue(
                dataSource.removeEntry(
                    credentials = credentials,
                    accountId = ProviderAccountId("42"),
                    entryId = ProviderCollectionEntryId("7"),
                    mediaId = ProviderMediaId("101"),
                    unit = ProviderProgressUnit.EPISODE,
                ) is ProviderResult.Success,
            )
            val deleteBody = server.takeRequest().body!!.utf8()
            assertTrue(deleteBody.contains("MioDeleteMediaListEntry"))
            assertTrue(deleteBody.contains("\"id\":7"))
        }

    @Test
    fun http200GraphQlErrorsAndForeignUsersAreNotAcknowledged() =
        runTest {
            server.enqueue("""{"data":{"MediaList":null},"errors":[{"message":"denied"}]}""")
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.readEntry(
                    credentials,
                    ProviderAccountId("42"),
                    ProviderCollectionEntryId("7"),
                    ProviderMediaId("101"),
                    ProviderProgressUnit.EPISODE,
                ),
            )

            server.enqueue(entry(progress = 4, userId = 99))
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.readEntry(
                    credentials,
                    ProviderAccountId("42"),
                    ProviderCollectionEntryId("7"),
                    ProviderMediaId("101"),
                    ProviderProgressUnit.EPISODE,
                ),
            )
        }

    private fun entry(
        progress: Int,
        progressVolumes: Int = 0,
        userId: Int = 42,
        totalEpisodes: Int? = 12,
        totalVolumes: Int? = 4,
    ): String =
        """{"data":{"MediaList":{"id":7,"userId":$userId,"mediaId":101,"status":"CURRENT","progress":$progress,"progressVolumes":$progressVolumes,"updatedAt":1720000000,"media":{"id":101,"episodes":${totalEpisodes ?: "null"},"chapters":null,"volumes":${totalVolumes ?: "null"}}}}}"""
}
