package io.morgan.mio.data.media

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.FakeProviderMediaDetailDataSource
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.provider.providerCredentialsFixture
import io.morgan.mio.core.testing.provider.providerMediaDetailsFixture
import io.morgan.mio.core.testing.provider.providerMediaFixture
import io.morgan.mio.data.media.local.RoomMediaLocalDataSource
import io.morgan.mio.data.media.mapper.MediaPersistenceSnapshot
import io.morgan.mio.data.media.mapper.MediaProviderIdInput
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.media.Artwork
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.MediaTotals
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ProviderMediaRefreshRequesterTest {
    private lateinit var database: MioDatabase
    private lateinit var local: RoomMediaLocalDataSource
    private lateinit var credentials: FakeProviderCredentialStore
    private lateinit var detailSource: FakeProviderMediaDetailDataSource
    private lateinit var requester: ProviderMediaRefreshRequester

    private val rootId = MediaId(StableOpaqueIdFactory.create("anilist", "media", "101"))

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        local = RoomMediaLocalDataSource(database)
        credentials = FakeProviderCredentialStore()
        detailSource = FakeProviderMediaDetailDataSource(ProviderKey.ANILIST)
        val binding = providerBindingFixture().copyWithDetails(detailSource)
        requester = ProviderMediaRefreshRequester(binding, credentials, local)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun oneRequestAtomicallyEnrichesRootAndCachesOrderedRelatedTitles() =
        runTest {
            seedRoot()
            credentials.write(providerCredentialsFixture())
            detailSource.enqueue { _, id ->
                assertEquals(ProviderMediaId("101"), id)
                ProviderResult.Success(
                    providerMediaDetailsFixture(
                        root =
                            providerMediaFixture(
                                id = "101",
                                relatedMediaIds = listOf("102"),
                                releaseYear = 2026,
                                scorePercent = 87,
                            ),
                    ),
                )
            }

            requester.refreshMedia(rootId)

            val refreshed = local.observeMedia(rootId).firstValue()
            assertEquals(2026, refreshed?.releaseYear)
            assertEquals(87, refreshed?.score?.percent)
            assertEquals(
                listOf(
                    MediaId(StableOpaqueIdFactory.create("anilist", "media", "102")),
                ),
                refreshed?.relatedMediaIds,
            )
            assertEquals(1, local.observeRelatedMedia(rootId).firstValue().size)
            assertEquals(1, detailSource.calls.size)
        }

    @Test
    fun missingMappingOrCredentialsMakesNoNetworkCallAndLeavesCacheUntouched() =
        runTest {
            assertThrows(
                RuntimeException::class.java,
            ) { kotlinx.coroutines.runBlocking { requester.refreshMedia(rootId) } }
            assertEquals(0, detailSource.calls.size)
            assertEquals(0, credentials.readCount)

            seedRoot()
            assertThrows(
                RuntimeException::class.java,
            ) { kotlinx.coroutines.runBlocking { requester.refreshMedia(rootId) } }
            assertEquals(0, detailSource.calls.size)
            assertEquals(1, credentials.readCount)
            assertEquals(
                "Title ${rootId.value}",
                local
                    .observeMedia(rootId)
                    .firstValue()
                    ?.title
                    ?.value,
            )
        }

    @Test
    fun providerFailureIsRedactedAndDoesNotReplaceCachedData() =
        runTest {
            seedRoot()
            credentials.write(providerCredentialsFixture())
            detailSource.enqueue { _, _ -> ProviderResult.Failure(ProviderFailure.Timeout) }

            val failure =
                assertThrows(RuntimeException::class.java) {
                    kotlinx.coroutines.runBlocking { requester.refreshMedia(rootId) }
                }

            assertNull(failure.message)
            assertEquals(
                "Title ${rootId.value}",
                local
                    .observeMedia(rootId)
                    .firstValue()
                    ?.title
                    ?.value,
            )
        }

    @Test
    fun mismatchedRootIsRejectedAsAnOperationalFailureWithoutReplacingTheCache() =
        runTest {
            seedRoot()
            credentials.write(providerCredentialsFixture())
            detailSource.enqueue { _, _ ->
                ProviderResult.Success(
                    providerMediaDetailsFixture(
                        root = providerMediaFixture(id = "999", relatedMediaIds = emptyList()),
                        related = emptyList(),
                    ),
                )
            }

            val failure =
                assertThrows(RuntimeException::class.java) {
                    kotlinx.coroutines.runBlocking { requester.refreshMedia(rootId) }
                }

            assertNull(failure.message)
            assertEquals(
                "Title ${rootId.value}",
                local
                    .observeMedia(rootId)
                    .firstValue()
                    ?.title
                    ?.value,
            )
            assertTrue(local.observeRelatedMedia(rootId).firstValue().isEmpty())
        }

    @Test
    fun databaseFailureRollsBackTheWholeEnrichmentSnapshot() =
        runTest {
            seedRoot()
            credentials.write(providerCredentialsFixture())
            detailSource.enqueue { _, _ ->
                ProviderResult.Success(
                    providerMediaDetailsFixture(
                        root =
                            providerMediaFixture(
                                id = "101",
                                relatedMediaIds = listOf("102"),
                                releaseYear = 2026,
                                scorePercent = 87,
                            ),
                    ),
                )
            }
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER fail_related_mapping
                BEFORE INSERT ON media_provider_id
                WHEN NEW.remote_media_id = '102'
                BEGIN
                    SELECT RAISE(ABORT, 'fixture');
                END
                """.trimIndent(),
            )

            val failure =
                assertThrows(RuntimeException::class.java) {
                    kotlinx.coroutines.runBlocking { requester.refreshMedia(rootId) }
                }

            assertNull(failure.message)
            val cached = local.observeMedia(rootId).firstValue()
            assertEquals("Title ${rootId.value}", cached?.title?.value)
            assertNull(cached?.releaseYear)
            assertNull(cached?.score)
            assertTrue(local.observeRelatedMedia(rootId).firstValue().isEmpty())
        }

    @Test
    fun cancellationFromTheProviderIsPropagated() =
        runTest {
            seedRoot()
            credentials.write(providerCredentialsFixture())
            val cancellation = CancellationException("fixture")
            detailSource.enqueue { _, _ -> throw cancellation }

            val thrown =
                assertThrows(CancellationException::class.java) {
                    kotlinx.coroutines.runBlocking { requester.refreshMedia(rootId) }
                }
            assertTrue(thrown === cancellation)
        }

    private suspend fun seedRoot() {
        local.replaceInitialMediaSnapshot(
            MediaPersistenceSnapshot(
                media = media(rootId),
                providerIds =
                    listOf(
                        MediaProviderIdInput(
                            mediaId = rootId,
                            provider = StoredConnectedProvider.ANILIST,
                            remoteId = "101",
                        ),
                    ),
            ),
        )
    }

    private fun media(id: MediaId): Media =
        Media(
            id = id,
            type = MediaType.ANIME,
            title = MediaTitle("Title ${id.value}"),
            format = MediaFormat.TV,
            publicationStatus = MediaPublicationStatus.UNKNOWN,
            synopsis = null,
            artwork = Artwork(null, null),
            totals = MediaTotals(UnitTotal(1), null, null),
            genres = emptyList(),
            relatedMediaIds = emptyList(),
        )

    private fun io.morgan.mio.data.provider.api.ProviderBinding.copyWithDetails(
        source: FakeProviderMediaDetailDataSource,
    ) = io.morgan.mio.data.provider.api.ProviderBinding(
        provider = provider,
        authenticator = authenticator,
        identityDataSource = identityDataSource,
        initialImportDataSource = initialImportDataSource,
        mediaDetailDataSource = source,
    )

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()
}
