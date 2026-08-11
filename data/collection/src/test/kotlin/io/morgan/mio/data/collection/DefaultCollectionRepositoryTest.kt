package io.morgan.mio.data.collection

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    private fun media(id: String): MediaEntity =
        MediaEntity(
            mediaId = id,
            mediaType = StoredMediaType.ANIME,
            title = "Title $id",
            format = null,
            publicationStatus = StoredMediaPublicationStatus.UNKNOWN,
            synopsis = null,
            posterReference = null,
            bannerReference = null,
            episodeTotal = null,
            chapterTotal = null,
            volumeTotal = null,
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
