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
import io.morgan.mio.data.sync.DefaultPendingOperationWriter
import io.morgan.mio.data.sync.local.RoomPendingOperationLocalDataSource
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.collection.CollectionStatusChange
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds
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
                    displayName = "Alex",
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
    fun undoRejectsChangedRevisionAndReusedActionCannotChangePayload() =
        runTest {
            val writer = DefaultPendingOperationWriter(accountId, RoomPendingOperationLocalDataSource(database))
            val id = OperationGroupId("review-add")
            val result =
                localDataSource.commitStatus(
                    change(id),
                    writer,
                ) as io.morgan.mio.domain.collection.CollectionWriteResult.Committed
            val before = checkNotNull(database.collectionEntryDao().getEntry(result.action.entryId.value))
            database.collectionEntryDao().upsertEntries(
                listOf(
                    before.copy(
                        localProgressRevision =
                            before.localProgressRevision + 1,
                        effectiveProgress = 1,
                    ),
                ),
            )
            assertTrue(
                localDataSource.undo(
                    accountId,
                    id,
                    writer,
                ) is io.morgan.mio.domain.collection.CollectionWriteResult.Rejected,
            )
            assertEquals(1, database.collectionEntryDao().getEntry(before.collectionEntryId)?.effectiveProgress)
            assertNotNull(database.pendingOperationDao().getGroup(id.value))
            assertTrue(
                localDataSource.commitStatus(change(id).copy(targetStatus = CollectionStatus.PAUSED), writer)
                    is io.morgan.mio.domain.collection.CollectionWriteResult.Rejected,
            )
        }

    @Test
    fun laterCollectionActionInvalidatesPreviousUndoEvenOnAnotherTitle() =
        runTest {
            val writer = DefaultPendingOperationWriter(accountId, RoomPendingOperationLocalDataSource(database))
            val first = OperationGroupId("review-first")
            val second = OperationGroupId("review-second")
            localDataSource.commitStatus(change(first), writer)
            localDataSource.commitStatus(change(second).copy(mediaId = mediaTwoId), writer)
            assertTrue(
                localDataSource.undo(
                    accountId,
                    first,
                    writer,
                ) is io.morgan.mio.domain.collection.CollectionWriteResult.Rejected,
            )
            assertNotNull(localDataSource.readEntryForMedia(accountId, mediaOneId))
        }

    @Test
    fun initialObservationIsEmptyAndRefreshIsSeparate() =
        runTest {
            var refreshedAccountId: AccountId? = null
            val repository =
                DefaultCollectionRepository(
                    accountId = accountId,
                    pendingOperationWriter =
                        io.morgan.mio.data.sync.DefaultPendingOperationWriter(
                            accountId,
                            io.morgan.mio.data.sync.local
                                .RoomPendingOperationLocalDataSource(database),
                        ),
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
                    accountId = accountId,
                    pendingOperationWriter =
                        io.morgan.mio.data.sync.DefaultPendingOperationWriter(
                            accountId,
                            io.morgan.mio.data.sync.local
                                .RoomPendingOperationLocalDataSource(database),
                        ),
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
                    accountId = accountId,
                    pendingOperationWriter =
                        io.morgan.mio.data.sync.DefaultPendingOperationWriter(
                            accountId,
                            io.morgan.mio.data.sync.local
                                .RoomPendingOperationLocalDataSource(database),
                        ),
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
                    accountId = accountId,
                    pendingOperationWriter =
                        io.morgan.mio.data.sync.DefaultPendingOperationWriter(
                            accountId,
                            io.morgan.mio.data.sync.local
                                .RoomPendingOperationLocalDataSource(database),
                        ),
                    localDataSource = localDataSource,
                    refreshRequester = CollectionRefreshRequester {},
                )

            assertEquals(
                CollectionStatus.REPEATING,
                repository.observeEntry(CollectionEntryId("entry-1")).first()?.status,
            )
        }

    @Test
    fun hiddenEntryReusesStableIdentityAndUndoCancelsCreationBeforeDelivery() =
        runTest {
            database.collectionEntryDao().upsertEntries(
                listOf(
                    CollectionEntryEntity(
                        collectionEntryId = "entry-hidden",
                        accountId = accountId.value,
                        mediaId = mediaOneId.value,
                        progressUnit = StoredProgressUnit.EPISODE,
                        remoteStatus = StoredCollectionStatus.CURRENT,
                        remoteProgress = 0,
                        remoteTotal = null,
                        remoteUpdatedAtUtcMs = TIME,
                        remotePresent = false,
                        effectiveStatus = StoredCollectionStatus.CURRENT,
                        effectiveProgress = 0,
                        effectiveTotal = null,
                        effectiveUpdatedAtUtcMs = TIME,
                        effectivePresent = false,
                    ),
                ),
            )
            val writer = DefaultPendingOperationWriter(accountId, RoomPendingOperationLocalDataSource(database))
            val firstAction = OperationGroupId("collection-add-1")
            val firstResult =
                localDataSource.commitStatus(
                    change(firstAction),
                    writer,
                )
            assertEquals(true, firstResult is io.morgan.mio.domain.collection.CollectionWriteResult.Committed)
            assertEquals(
                "entry-hidden",
                database.collectionActivityDao().get(accountId.value, firstAction.value)?.collectionEntryId,
            )
            assertEquals(
                io.morgan.mio.core.database.model.StoredPendingOperationKind.CREATE_ENTRY,
                database
                    .pendingOperationDao()
                    .getOperationsForGroup(firstAction.value)
                    .single()
                    .kind,
            )
            assertEquals(
                "entry-hidden",
                localDataSource
                    .observeEntryForMedia(accountId, mediaOneId)
                    .first()
                    ?.id
                    ?.value,
            )

            val undo = localDataSource.undo(accountId, firstAction, writer)
            assertEquals(true, undo is io.morgan.mio.domain.collection.CollectionWriteResult.Committed)
            assertEquals(null, localDataSource.observeEntryForMedia(accountId, mediaOneId).first())
            assertEquals(false, database.collectionEntryDao().getEntry("entry-hidden")?.effectivePresent)
            assertEquals(null, database.pendingOperationDao().getGroup(firstAction.value))

            val secondAction = OperationGroupId("collection-add-2")
            localDataSource.commitStatus(change(secondAction), writer)
            assertEquals(
                "entry-hidden",
                localDataSource
                    .observeEntryForMedia(accountId, mediaOneId)
                    .first()
                    ?.id
                    ?.value,
            )
            writer.cancelUndelivered(secondAction)
        }

    @Test
    fun inFlightAddUndoWaitsForCreateBeforeCompensatingRemoval() =
        runTest {
            val writer = DefaultPendingOperationWriter(accountId, RoomPendingOperationLocalDataSource(database))
            val actionId = OperationGroupId("collection-add-in-flight")
            val result = localDataSource.commitStatus(change(actionId), writer)
            val action = (result as io.morgan.mio.domain.collection.CollectionWriteResult.Committed).action
            val create = database.pendingOperationDao().getOperationsForGroup(actionId.value).single()
            assertEquals(
                1,
                database.syncDao().claimOperation(
                    accountId.value,
                    create.operationId,
                    ACTION_TIME,
                    ACTION_TIME - 120.seconds,
                ),
            )

            val undo = localDataSource.undo(accountId, actionId, writer)

            assertTrue(undo is io.morgan.mio.domain.collection.CollectionWriteResult.Committed)
            assertEquals(
                io.morgan.mio.core.database.model.StoredPendingOperationState.IN_FLIGHT,
                database.pendingOperationDao().getOperation(create.operationId)?.lifecycleState,
            )
            val compensationId = "${actionId.value}:undo"
            val compensation = database.pendingOperationDao().getOperationsForGroup(compensationId).single()
            assertEquals(
                io.morgan.mio.core.database.model.StoredPendingOperationKind.REMOVE_ENTRY,
                compensation.kind,
            )
            assertEquals(
                listOf(create.operationId),
                database
                    .pendingOperationDao()
                    .getDependenciesForOperation(compensation.operationId)
                    .map { dependency -> dependency.dependsOnOperationId },
            )
            assertFalse(checkNotNull(database.collectionEntryDao().getEntry(action.entryId.value)).effectivePresent)
            assertNotNull(database.pendingOperationDao().getGroup(compensationId))
        }

    @Test
    fun deliveredStatusUndoCreatesCompensationAndRestoresBeforeState() =
        runTest {
            val entryId = CollectionEntryId("entry-status")
            database.collectionEntryDao().upsertEntries(
                listOf(
                    CollectionEntryEntity(
                        collectionEntryId = entryId.value,
                        accountId = accountId.value,
                        mediaId = mediaOneId.value,
                        progressUnit = StoredProgressUnit.EPISODE,
                        remoteStatus = StoredCollectionStatus.CURRENT,
                        remoteProgress = 0,
                        remoteTotal = 12,
                        remoteUpdatedAtUtcMs = TIME,
                        effectiveStatus = StoredCollectionStatus.CURRENT,
                        effectiveProgress = 0,
                        effectiveTotal = 12,
                        effectiveUpdatedAtUtcMs = TIME,
                    ),
                ),
            )
            val writer = DefaultPendingOperationWriter(accountId, RoomPendingOperationLocalDataSource(database))
            val actionId = OperationGroupId("collection-status-delivered")
            val result =
                localDataSource.commitStatus(
                    CollectionStatusChange(
                        actionId = actionId,
                        accountId = accountId,
                        mediaId = mediaOneId,
                        progressUnit = ProgressUnit.EPISODE,
                        before = entry(entryId.value, mediaOneId, progress = 0),
                        targetStatus = CollectionStatus.PAUSED,
                        targetProgress = ProgressValue(0),
                        targetTotal = UnitTotal(12),
                        createdAt = ACTION_TIME,
                        undoUntil = ACTION_TIME + 30.seconds,
                    ),
                    writer,
                )
            assertTrue(result is io.morgan.mio.domain.collection.CollectionWriteResult.Committed)
            val operation = database.pendingOperationDao().getOperationsForGroup(actionId.value).single()
            assertEquals(
                1,
                database.syncDao().claimOperation(
                    accountId = accountId.value,
                    operationId = operation.operationId,
                    nowUtcMs = ACTION_TIME,
                    abandonedBeforeUtcMs = ACTION_TIME - 120.seconds,
                ),
            )
            assertEquals(
                1,
                database.syncDao().setOperationState(
                    accountId = accountId.value,
                    operationId = operation.operationId,
                    state = io.morgan.mio.core.database.model.StoredPendingOperationState.DELIVERED,
                    retryNotBeforeUtcMs = null,
                ),
            )

            val undo = localDataSource.undo(accountId, actionId, writer)

            assertTrue(undo is io.morgan.mio.domain.collection.CollectionWriteResult.Committed)
            assertEquals(
                CollectionStatus.CURRENT,
                localDataSource.observeEntryForMedia(accountId, mediaOneId).first()?.status,
            )
            val compensation = database.pendingOperationDao().getOperationsForGroup("${actionId.value}:undo").single()
            assertEquals(
                io.morgan.mio.core.database.model.StoredPendingOperationKind.SET_STATUS,
                compensation.kind,
            )
            assertEquals(
                io.morgan.mio.core.database.model.StoredPendingOperationState.ENQUEUED,
                compensation.lifecycleState,
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
        val ACTION_TIME: Instant = Instant.parse("2099-01-01T00:00:00Z")
    }

    private fun change(actionId: OperationGroupId): CollectionStatusChange =
        CollectionStatusChange(
            actionId = actionId,
            accountId = accountId,
            mediaId = mediaOneId,
            progressUnit = ProgressUnit.EPISODE,
            before = null,
            targetStatus = CollectionStatus.CURRENT,
            targetProgress = ProgressValue(0),
            targetTotal = null,
            createdAt = ACTION_TIME,
            undoUntil = ACTION_TIME + 30.seconds,
        )
}
