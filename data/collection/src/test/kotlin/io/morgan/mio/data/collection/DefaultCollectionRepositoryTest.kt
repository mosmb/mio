package io.morgan.mio.data.collection

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.collection.RoomCollectionStatisticsLocalDataSource
import io.morgan.mio.data.collection.local.RoomCollectionLocalDataSource
import io.morgan.mio.data.collection.mapper.CollectionEntryPersistenceSnapshot
import io.morgan.mio.data.collection.mapper.CollectionEntryProviderIdInput
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
class DefaultCollectionRepositoryTest {
    private val accountId = AccountId("account-1")
    private val mediaOneId = MediaId("media-1")
    private val mediaTwoId = MediaId("media-2")
    private lateinit var database: MioDatabase
    private lateinit var localDataSource: RoomCollectionLocalDataSource

    @Before
    fun setUp() =
        runBlocking {
            database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
            localDataSource = RoomCollectionLocalDataSource(database)
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity(
                    selectionKey = "selected",
                    accountId = accountId.value,
                    connectedProvider = StoredConnectedProvider.ANILIST,
                    displayName = "Morgan",
                    avatarReference = null,
                ),
                io.morgan.mio.core.database.entity.AccountProviderIdEntity(
                    accountId = accountId.value,
                    provider = StoredConnectedProvider.ANILIST,
                    remoteAccountId = "remote-account",
                ),
            )
            database.mediaDao().upsertMedia(listOf(media("media-1"), media("media-2")))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initialObservationIsEmptyAndRefreshIsSeparate() =
        runTest {
            var refreshedAccountId: AccountId? = null
            val repository =
                DefaultCollectionRepository(
                    localDataSource = localDataSource,
                    refreshRequester = CollectionRefreshRequester { refreshedAccountId = it },
                )

            assertEquals(emptyList<CollectionEntry>(), repository.observeCollection(accountId).first())
            assertNull(repository.observeEntry(CollectionEntryId("missing")).first())
            repository.refreshCollection(accountId)
            assertEquals(accountId, refreshedAccountId)
        }

    @Test
    fun effectiveStateIsObservedAndCompleteAndPartialSnapshotsHaveDifferentDeletionSemantics() =
        runTest {
            val entryOne = entry("entry-1", mediaOneId, progress = 1)
            val entryTwo = entry("entry-2", mediaTwoId, progress = 2)
            val repository =
                DefaultCollectionRepository(
                    localDataSource = localDataSource,
                    refreshRequester = CollectionRefreshRequester {},
                )

            localDataSource.replaceInitialCollectionSnapshot(
                accountId,
                listOf(CollectionEntryPersistenceSnapshot(entryOne), CollectionEntryPersistenceSnapshot(entryTwo)),
                isComplete = true,
            )
            assertEquals(listOf(entryOne, entryTwo), repository.observeCollection(accountId).first())

            val changedEntryOne = entry("entry-1", mediaOneId, progress = 7)
            localDataSource.replaceInitialCollectionSnapshot(
                accountId,
                listOf(CollectionEntryPersistenceSnapshot(changedEntryOne)),
                isComplete = false,
            )
            assertEquals(
                listOf(changedEntryOne, entryTwo),
                repository.observeCollection(accountId).first(),
            )

            localDataSource.replaceInitialCollectionSnapshot(
                accountId,
                listOf(CollectionEntryPersistenceSnapshot(changedEntryOne)),
                isComplete = true,
            )
            assertEquals(listOf(changedEntryOne), repository.observeCollection(accountId).first())
        }

    @Test
    fun observationReadsEffectiveColumnsInsteadOfRemoteBaseline() =
        runTest {
            database.collectionEntryDao().upsertEntries(
                listOf(
                    CollectionEntryEntity(
                        collectionEntryId = "entry-1",
                        accountId = accountId.value,
                        mediaId = mediaOneId.value,
                        progressUnit = StoredProgressUnit.EPISODE,
                        remoteStatus = StoredCollectionStatus.CURRENT,
                        remoteProgress = 1,
                        remoteTotal = 12,
                        remoteUpdatedAtUtcMs = TIME,
                        effectiveStatus = StoredCollectionStatus.PAUSED,
                        effectiveProgress = 9,
                        effectiveTotal = 12,
                        effectiveUpdatedAtUtcMs = TIME,
                    ),
                ),
            )
            val repository =
                DefaultCollectionRepository(
                    localDataSource = localDataSource,
                    refreshRequester = CollectionRefreshRequester {},
                )

            assertEquals(CollectionStatus.PAUSED, repository.observeEntry(CollectionEntryId("entry-1")).first()?.status)
            assertEquals(
                9,
                repository
                    .observeEntry(CollectionEntryId("entry-1"))
                    .first()
                    ?.progress
                    ?.value,
            )
        }

    @Test
    fun repeatingEntryIsObservedAsRepeatingStatus() =
        runTest {
            database.collectionEntryDao().upsertEntries(
                listOf(
                    CollectionEntryEntity(
                        collectionEntryId = "entry-1",
                        accountId = accountId.value,
                        mediaId = mediaOneId.value,
                        progressUnit = StoredProgressUnit.EPISODE,
                        remoteStatus = StoredCollectionStatus.REPEATING,
                        remoteProgress = 1,
                        remoteTotal = 12,
                        remoteUpdatedAtUtcMs = TIME,
                        effectiveStatus = StoredCollectionStatus.REPEATING,
                        effectiveProgress = 1,
                        effectiveTotal = 12,
                        effectiveUpdatedAtUtcMs = TIME,
                    ),
                ),
            )
            val repository =
                DefaultCollectionRepository(
                    localDataSource = localDataSource,
                    refreshRequester = CollectionRefreshRequester {},
                )

            assertEquals(
                CollectionStatus.REPEATING,
                repository.observeEntry(CollectionEntryId("entry-1")).first()?.status,
            )
        }

    @Test
    fun batchRejectsProviderMappingOwnedByAnotherEntry() =
        runTest {
            val entryOne = entry("entry-1", mediaOneId, progress = 1)
            val entryTwo = entry("entry-2", mediaTwoId, progress = 2)

            try {
                localDataSource.replaceInitialCollectionSnapshot(
                    accountId = accountId,
                    input =
                        listOf(
                            CollectionEntryPersistenceSnapshot(
                                entry = entryOne,
                                providerIds =
                                    listOf(
                                        CollectionEntryProviderIdInput(
                                            entryId = entryTwo.id,
                                            provider = StoredConnectedProvider.ANILIST,
                                            remoteId = "remote-entry-2",
                                        ),
                                    ),
                            ),
                            CollectionEntryPersistenceSnapshot(entryTwo),
                        ),
                    isComplete = true,
                )
                fail("Expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
                // Expected invalid cross-snapshot association.
            }

            assertEquals(emptyList<CollectionEntry>(), localDataSource.observeCollection(accountId).first())
        }

    @Test
    fun statisticsUseEffectiveProgressDistinctGenresAndSeparateVolumesFromChapters() =
        runTest {
            val plannedAnimeId = MediaId("media-anime-planned")
            val chapterMangaId = MediaId("media-manga-chapters")
            val volumeMangaId = MediaId("media-manga-volumes")
            database.mediaDao().upsertMedia(
                listOf(
                    media(mediaOneId.value, StoredMediaType.ANIME, poster = "poster:current"),
                    media(mediaTwoId.value, StoredMediaType.ANIME),
                    media(plannedAnimeId.value, StoredMediaType.ANIME),
                    media(chapterMangaId.value, StoredMediaType.MANGA),
                    media(volumeMangaId.value, StoredMediaType.MANGA),
                ),
            )
            database.collectionEntryDao().upsertEntries(
                listOf(
                    statisticsEntry(
                        "entry-anime-current",
                        mediaOneId.value,
                        StoredProgressUnit.EPISODE,
                        StoredCollectionStatus.CURRENT,
                        1_500_000_000,
                    ),
                    statisticsEntry(
                        "entry-anime-completed",
                        mediaTwoId.value,
                        StoredProgressUnit.EPISODE,
                        StoredCollectionStatus.COMPLETED,
                        1_500_000_001,
                    ),
                    statisticsEntry(
                        "entry-anime-planned",
                        plannedAnimeId.value,
                        StoredProgressUnit.EPISODE,
                        StoredCollectionStatus.PLANNED,
                        99,
                    ),
                    statisticsEntry(
                        "entry-manga-chapters",
                        chapterMangaId.value,
                        StoredProgressUnit.CHAPTER,
                        StoredCollectionStatus.CURRENT,
                        12,
                    ),
                    statisticsEntry(
                        "entry-manga-volumes",
                        volumeMangaId.value,
                        StoredProgressUnit.VOLUME,
                        StoredCollectionStatus.COMPLETED,
                        30,
                    ),
                ),
            )
            database.mediaDao().upsertGenres(
                listOf(
                    MediaGenreEntity(mediaOneId.value, "Action", 0),
                    MediaGenreEntity(mediaOneId.value, "action", 1),
                    MediaGenreEntity(mediaOneId.value, "Comedy", 2),
                    MediaGenreEntity(mediaTwoId.value, "action", 0),
                    MediaGenreEntity(mediaTwoId.value, "Drama", 1),
                    MediaGenreEntity(chapterMangaId.value, "Romance", 0),
                    MediaGenreEntity(volumeMangaId.value, "Romance", 0),
                ),
            )
            val statistics = RoomCollectionStatisticsLocalDataSource(database).observeStatistics(accountId).first()

            assertEquals(2L, statistics.completedTitleCount)
            assertEquals(3_000_000_100L, statistics.recordedEpisodes.value)
            assertEquals(3L, statistics.recordedEpisodes.trackedTitleCount)
            assertEquals(12L, statistics.recordedChapters.value)
            assertEquals(3L, statistics.anime.trackedTitleCount)
            assertEquals(1L, statistics.anime.completedTitleCount)
            assertEquals(2L, statistics.anime.genreMetadataTitleCount)
            assertEquals(
                listOf("Action" to 2L, "Comedy" to 1L, "Drama" to 1L),
                statistics.anime.topGenres
                    .take(3)
                    .map { it.name to it.titleCount },
            )
            assertEquals(2L, statistics.manga.trackedTitleCount)
            assertEquals(listOf("Romance" to 2L), statistics.manga.topGenres.map { it.name to it.titleCount })
            assertEquals(
                listOf(mediaOneId.value, mediaTwoId.value, chapterMangaId.value, volumeMangaId.value).sorted(),
                statistics.artwork.map { it.mediaId.value },
            )
            assertEquals(
                0L,
                RoomCollectionStatisticsLocalDataSource(database)
                    .observeStatistics(AccountId("another-account"))
                    .first()
                    .anime
                    .trackedTitleCount,
            )
        }

    @Test
    fun statisticsObservationInvalidatesAfterRoomProgressChanges() =
        runBlocking {
            database.collectionEntryDao().upsertEntries(
                listOf(
                    statisticsEntry(
                        "entry-observed",
                        mediaOneId.value,
                        StoredProgressUnit.EPISODE,
                        StoredCollectionStatus.CURRENT,
                        2,
                    ),
                ),
            )
            val source = RoomCollectionStatisticsLocalDataSource(database)
            val emissions = Channel<io.morgan.mio.domain.collection.CollectionStatisticsSnapshot>(Channel.UNLIMITED)
            val job = launch(Dispatchers.IO) { source.observeStatistics(accountId).take(2).collect(emissions::send) }

            val initial = withTimeout(5_000) { emissions.receive() }
            database.collectionEntryDao().upsertEntries(
                listOf(
                    statisticsEntry(
                        "entry-observed",
                        mediaOneId.value,
                        StoredProgressUnit.EPISODE,
                        StoredCollectionStatus.CURRENT,
                        8,
                    ),
                ),
            )
            val updated = withTimeout(5_000) { emissions.receive() }
            job.join()

            assertEquals(2L, initial.recordedEpisodes.value)
            assertEquals(8L, updated.recordedEpisodes.value)
        }

    private fun media(
        id: String,
        type: StoredMediaType = StoredMediaType.ANIME,
        poster: String? = null,
    ): MediaEntity =
        MediaEntity(
            mediaId = id,
            mediaType = type,
            title = "Title $id",
            format = null,
            publicationStatus = StoredMediaPublicationStatus.UNKNOWN,
            synopsis = null,
            posterReference = poster,
            bannerReference = null,
            episodeTotal = null,
            chapterTotal = null,
            volumeTotal = null,
        )

    private fun statisticsEntry(
        entryId: String,
        mediaId: String,
        unit: StoredProgressUnit,
        status: StoredCollectionStatus,
        progress: Int,
    ): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = entryId,
            accountId = accountId.value,
            mediaId = mediaId,
            progressUnit = unit,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 0,
            remoteTotal = null,
            remoteUpdatedAtUtcMs = TIME,
            effectiveStatus = status,
            effectiveProgress = progress,
            effectiveTotal = null,
            effectiveUpdatedAtUtcMs = TIME,
        )

    private fun entry(
        id: String,
        mediaId: MediaId,
        progress: Int,
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId(id),
            accountId = accountId,
            mediaId = mediaId,
            status = CollectionStatus.CURRENT,
            progress = ProgressValue(progress),
            progressUnit = ProgressUnit.EPISODE,
            total = UnitTotal(12),
            updatedAt = TIME,
        )

    private companion object {
        val TIME: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
