package io.morgan.mio.data.media

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.media.local.RoomMediaLocalDataSource
import io.morgan.mio.data.media.mapper.MediaPersistenceSnapshot
import io.morgan.mio.data.media.mapper.MediaProviderIdInput
import io.morgan.mio.data.media.mapper.toEntity
import io.morgan.mio.domain.media.Artwork
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.MediaTotals
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class DefaultMediaRepositoryTest {
    private lateinit var database: MioDatabase
    private lateinit var localDataSource: RoomMediaLocalDataSource

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        localDataSource = RoomMediaLocalDataSource(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observationsMapDeterministicRoomSnapshotsAndRefreshDoesNotMutateState() =
        runTest {
            val mediaId = MediaId("media-1")
            val media = media(mediaId)
            val releaseTwo = release(mediaId, 2)
            val releaseOne = release(mediaId, 1)
            var refreshedId: MediaId? = null
            val repository =
                DefaultMediaRepository(
                    localDataSource = localDataSource,
                    refreshRequester = MediaRefreshRequester { refreshedId = it },
                )

            assertNull(repository.observeMedia(mediaId).first())
            assertEquals(emptyList<Release>(), repository.observeReleases(mediaId).first())

            localDataSource.replaceInitialMediaSnapshot(
                MediaPersistenceSnapshot(
                    media = media,
                    releases = listOf(releaseTwo, releaseOne),
                    providerIds =
                        listOf(
                            MediaProviderIdInput(
                                mediaId = mediaId,
                                provider = StoredConnectedProvider.ANILIST,
                                remoteId = "101",
                            ),
                        ),
                ),
            )

            assertEquals(media, repository.observeMedia(mediaId).first())
            assertEquals(listOf(releaseOne, releaseTwo), repository.observeReleases(mediaId).first())
            repository.refreshMedia(mediaId)
            assertEquals(mediaId, refreshedId)
            assertEquals(media, repository.observeMedia(mediaId).first())
        }

    @Test
    fun replacingMediaReplacesNormalizedListChildrenAndSnapshotsInputs() =
        runTest {
            val mediaId = MediaId("media-1")
            val initial = media(mediaId, genres = listOf("Drama", "Action"))
            localDataSource.replaceInitialMediaSnapshot(MediaPersistenceSnapshot(initial))

            val updated = media(mediaId, genres = listOf("Comedy"), relatedMediaIds = listOf(MediaId("media-2")))
            database.mediaDao().upsertMedia(listOf(media(MediaId("media-2")).toEntity()))
            localDataSource.replaceInitialMediaSnapshot(MediaPersistenceSnapshot(updated))

            assertEquals(updated, localDataSource.observeMedia(mediaId).first())
            assertEquals(listOf("Comedy"), localDataSource.observeMedia(mediaId).first()?.genres)
            assertEquals(listOf(MediaId("media-2")), localDataSource.observeMedia(mediaId).first()?.relatedMediaIds)
        }

    @Test
    fun batchRejectsReleasesAndProviderMappingsOwnedByAnotherSnapshot() =
        runTest {
            val mediaOne = media(MediaId("media-1"))
            val mediaTwo = media(MediaId("media-2"))

            assertIllegalArgument {
                localDataSource.replaceInitialMediaSnapshots(
                    listOf(
                        MediaPersistenceSnapshot(
                            media = mediaOne,
                            releases = listOf(release(mediaTwo.id, 1)),
                        ),
                        MediaPersistenceSnapshot(mediaTwo),
                    ),
                )
            }
            assertIllegalArgument {
                localDataSource.replaceInitialMediaSnapshots(
                    listOf(
                        MediaPersistenceSnapshot(
                            media = mediaOne,
                            providerIds =
                                listOf(
                                    MediaProviderIdInput(
                                        mediaId = mediaTwo.id,
                                        provider = StoredConnectedProvider.ANILIST,
                                        remoteId = "101",
                                    ),
                                ),
                        ),
                        MediaPersistenceSnapshot(mediaTwo),
                    ),
                )
            }

            assertNull(localDataSource.observeMedia(mediaOne.id).first())
            assertNull(localDataSource.observeMedia(mediaTwo.id).first())
        }

    private fun media(
        mediaId: MediaId,
        genres: List<String> = listOf("Action", "Drama"),
        relatedMediaIds: List<MediaId> = emptyList(),
    ): Media =
        Media(
            id = mediaId,
            type = MediaType.ANIME,
            title = MediaTitle("Title ${mediaId.value}"),
            format = MediaFormat.TV,
            publicationStatus = MediaPublicationStatus.RELEASING,
            synopsis = "Synopsis",
            artwork = Artwork(null, null),
            totals = MediaTotals(UnitTotal(12), null, null),
            genres = genres,
            relatedMediaIds = relatedMediaIds,
        )

    private fun release(
        mediaId: MediaId,
        number: Int,
    ): Release =
        Release(
            mediaId = mediaId,
            unit = ProgressUnit.EPISODE,
            number = UnitNumber(number),
            time = ReleaseTime.Trusted(Instant.parse("2026-01-01T00:00:00Z")),
        )

    private suspend fun assertIllegalArgument(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected invalid cross-snapshot association.
        }
    }
}
