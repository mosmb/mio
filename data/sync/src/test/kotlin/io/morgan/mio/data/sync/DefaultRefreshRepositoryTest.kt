package io.morgan.mio.data.sync

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.PendingOperationDependencyEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.entity.SyncAccountIssueEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.FakeProviderIdentityDataSource
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.FakeProviderSyncDataSource
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.provider.providerMediaFixture
import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.sync.local.RoomRefreshLocalDataSource
import io.morgan.mio.data.sync.local.RoomSyncAccountLocalDataSource
import io.morgan.mio.data.sync.local.RoomSyncLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.ProjectSyncEntryUseCase
import io.morgan.mio.domain.sync.SyncLease
import io.morgan.mio.domain.sync.SyncRefreshResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class DefaultRefreshRepositoryTest {
    private lateinit var database: MioDatabase

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity("selected", ACCOUNT_ID.value, StoredConnectedProvider.ANILIST, "Fixture", null),
                AccountProviderIdEntity(ACCOUNT_ID.value, StoredConnectedProvider.ANILIST, REMOTE_ACCOUNT_ID),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshDiscoversAnEntryWithStableOpaqueIdentity() =
        runTest {
            val harness = harness()
            harness.imports.enqueue { _, _ -> ProviderResult.Success(snapshot(listOf(remoteEntry("201", "101", 1)))) }
            val lease = lease()

            assertEquals(SyncRefreshResult.Success, harness.repository.refresh(lease))
            val firstEntryId =
                database
                    .syncDao()
                    .findEntryProviderIdByRemote(
                        StoredConnectedProvider.ANILIST,
                        "201",
                    )?.collectionEntryId
            val firstMediaId =
                database
                    .syncDao()
                    .findMediaProviderIdByRemote(
                        StoredConnectedProvider.ANILIST,
                        "101",
                    )?.mediaId
            assertNotNull(firstEntryId)
            assertNotNull(firstMediaId)
            assertNotEquals("201", firstEntryId)
            assertNotEquals("101", firstMediaId)
            assertEquals(
                StableOpaqueIdFactory.create("anilist", "collection-entry", "201"),
                firstEntryId,
            )
            assertEquals(1, database.syncDao().getEntries(ACCOUNT_ID.value).size)

            harness.imports.enqueue { _, _ -> ProviderResult.Success(snapshot(listOf(remoteEntry("201", "101", 1)))) }
            harness.sync.enqueueRead { _, _, _, _, unit ->
                ProviderResult.Success(remoteEntry("201", "101", 1, unit = unit))
            }
            assertEquals(SyncRefreshResult.Success, harness.repository.refresh(lease))
            assertEquals(
                firstEntryId,
                database
                    .syncDao()
                    .findEntryProviderIdByRemote(
                        StoredConnectedProvider.ANILIST,
                        "201",
                    )?.collectionEntryId,
            )
            assertEquals(
                firstMediaId,
                database.syncDao().findMediaProviderIdByRemote(StoredConnectedProvider.ANILIST, "101")?.mediaId,
            )
            assertEquals(1, database.syncDao().getEntries(ACCOUNT_ID.value).size)
        }

    @Test
    fun refreshReconcilesRemoteEntryWithOptimisticLocalMediaIdentity() =
        runTest {
            seedMappedEntry("optimistic-entry", "local-media", "201", "101", progress = 1)
            database.providerIdDao().deleteCollectionEntryProviderIds("optimistic-entry")
            database.openHelper.writableDatabase.execSQL(
                "UPDATE collection_entry SET remote_present = 0 WHERE collection_entry_id = ?",
                arrayOf("optimistic-entry"),
            )

            val harness = harness()
            harness.imports.enqueue { _, _ -> ProviderResult.Success(snapshot(listOf(remoteEntry("202", "101", 2)))) }
            harness.sync.enqueueRead { _, _, _, _, unit ->
                ProviderResult.Success(remoteEntry("202", "101", 2, unit = unit))
            }

            assertEquals(SyncRefreshResult.Success, harness.repository.refresh(lease()))
            assertEquals(1, database.syncDao().getEntries(ACCOUNT_ID.value).size)
            assertEquals(
                "optimistic-entry",
                database
                    .syncDao()
                    .getEntries(ACCOUNT_ID.value)
                    .single()
                    .collectionEntryId,
            )
            assertEquals(
                "optimistic-entry",
                database
                    .syncDao()
                    .findEntryProviderIdByRemote(StoredConnectedProvider.ANILIST, "202")
                    ?.collectionEntryId,
            )
            assertTrue(checkNotNull(database.syncDao().getEntry("optimistic-entry")).remotePresent)
        }

    @Test
    fun refreshKeepsPendingCorrectionUndoActivityAndDependencies() =
        runTest {
            seedMappedEntry(
                "local-entry",
                "local-media",
                "201",
                "101",
                progress = 1,
                effectiveProgress = 3,
                revision = 1,
            )
            seedPendingCorrection()
            val entryBefore = checkNotNull(database.syncDao().getEntry("local-entry"))
            val operationsBefore = database.syncDao().getOrderedOperations(ACCOUNT_ID.value)
            val dependenciesBefore = database.syncDao().getDependenciesForOperation("operation-correction")
            val activityBefore = database.syncDao().getActivity(ACCOUNT_ID.value, "group-correction")
            val harness = harness()
            harness.imports.enqueue { _, _ -> ProviderResult.Success(snapshot(listOf(remoteEntry("201", "101", 1)))) }
            harness.sync.enqueueRead { _, _, _, _, unit ->
                ProviderResult.Success(remoteEntry("201", "101", 1, unit = unit))
            }

            assertEquals(SyncRefreshResult.Success, harness.repository.refresh(lease()))

            assertEquals(entryBefore, database.syncDao().getEntry("local-entry"))
            assertEquals(operationsBefore, database.syncDao().getOrderedOperations(ACCOUNT_ID.value))
            assertEquals(dependenciesBefore, database.syncDao().getDependenciesForOperation("operation-correction"))
            assertEquals(activityBefore, database.syncDao().getActivity(ACCOUNT_ID.value, "group-correction"))
            assertEquals(3, database.syncDao().getEntry("local-entry")?.effectiveProgress)
        }

    @Test
    fun partialOmissionDoesNotDeleteAnExistingEntry() =
        runTest {
            seedMappedEntry("entry-one", "media-one", "201", "101", progress = 1)
            seedMappedEntry("entry-two", "media-two", "202", "102", progress = 4)
            val omittedBefore = checkNotNull(database.syncDao().getEntry("entry-two"))
            val harness = harness()
            harness.imports.enqueue { _, _ ->
                ProviderResult.Success(snapshot(listOf(remoteEntry("201", "101", 2))))
            }
            harness.sync.enqueueRead { _, _, _, _, unit ->
                ProviderResult.Success(remoteEntry("201", "101", 2, unit = unit))
            }

            assertEquals(SyncRefreshResult.Success, harness.repository.refresh(lease()))

            assertEquals(omittedBefore, database.syncDao().getEntry("entry-two"))
            assertEquals("202", database.syncDao().getEntryProviderId("entry-two")?.remoteCollectionEntryId)
            assertEquals(2, database.syncDao().getEntries(ACCOUNT_ID.value).size)
        }

    @Test
    fun failedCompleteReadCommitsNoPartialSnapshot() =
        runTest {
            seedMappedEntry("existing-entry", "existing-media", "200", "100", progress = 3)
            val entryBefore = checkNotNull(database.syncDao().getEntry("existing-entry"))
            val mediaBefore = checkNotNull(database.mediaDao().getMedia("existing-media"))
            val harness = harness()
            harness.imports.enqueue { _, _ -> ProviderResult.Failure(ProviderFailure.ServiceUnavailable) }

            assertEquals(SyncRefreshResult.Transient, harness.repository.refresh(lease()))

            assertEquals(entryBefore, database.syncDao().getEntry("existing-entry"))
            assertEquals(mediaBefore, database.mediaDao().getMedia("existing-media"))
            assertNull(database.syncDao().getAccountIssue(ACCOUNT_ID.value))
            assertFalse(database.syncDao().findMediaProviderIdByRemote(StoredConnectedProvider.ANILIST, "101") != null)
        }

    @Test
    fun accountAuthBlocksEmptyAndNonemptyOutbox() =
        runTest {
            val harness = harness()
            database.syncDao().upsertAccountIssue(
                SyncAccountIssueEntity(
                    issueId = "account-auth-refresh",
                    accountId = ACCOUNT_ID.value,
                    kind = "authentication",
                    detectedAtUtcMs = NOW,
                ),
            )

            assertTrue(harness.repository.isAuthenticationBlocked())
            assertTrue(database.syncDao().getEntries(ACCOUNT_ID.value).isEmpty())
            assertTrue(harness.imports.calls.isEmpty())

            seedMappedEntry("local-entry", "local-media", "201", "101", progress = 1)
            seedPendingCorrection()

            assertTrue(harness.repository.isAuthenticationBlocked())
            assertEquals(2, database.syncDao().getOrderedOperations(ACCOUNT_ID.value).size)
            assertTrue(harness.imports.calls.isEmpty())
        }

    @Test
    fun oldAccountResponseCannotCommitAfterSelectionChanges() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<ProviderResult<CompleteProviderImport>>()
            val harness = harness()
            harness.imports.enqueue { _, _ ->
                entered.complete(Unit)
                releaseResponse.await()
            }
            val lease = lease()
            val refresh = async { harness.repository.refresh(lease) }
            entered.await()
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity("selected", "account-new", StoredConnectedProvider.ANILIST, "New account", null),
                AccountProviderIdEntity("account-new", StoredConnectedProvider.ANILIST, "remote-88"),
            )
            releaseResponse.complete(ProviderResult.Success(snapshot(listOf(remoteEntry("201", "101", 1)))))

            assertEquals(SyncRefreshResult.Transient, refresh.await())
            assertTrue(database.syncDao().getEntries("account-new").isEmpty())
            assertNull(database.syncDao().findEntryProviderIdByRemote(StoredConnectedProvider.ANILIST, "201"))
        }

    @Test
    fun leaseExpiryRollsBackEverySnapshotTable() =
        runTest {
            val response = snapshot(listOf(remoteEntry("201", "101", 1)))
            val harness = harness(localClock = SequenceClock(listOf(NOW, NOW + 121.seconds)))
            harness.imports.enqueue { _, _ -> ProviderResult.Success(response) }
            val lease = lease()

            assertEquals(SyncRefreshResult.Transient, harness.repository.refresh(lease))

            assertTrue(database.syncDao().getEntries(ACCOUNT_ID.value).isEmpty())
            assertNull(database.mediaDao().getMedia(StableOpaqueIdFactory.create("anilist", "media", "101")))
            assertNull(database.syncDao().findEntryProviderIdByRemote(StoredConnectedProvider.ANILIST, "201"))
            assertNull(database.syncDao().findMediaProviderIdByRemote(StoredConnectedProvider.ANILIST, "101"))
        }

    @Test
    fun freshRemoteProgressUpdatesWithoutClearingUnrelatedCachedFields() =
        runTest {
            seedMappedEntry("local-entry", "local-media", "201", "101", progress = 1)
            val before = checkNotNull(database.mediaDao().getMedia("local-media"))
            val harness = harness()
            harness.imports.enqueue { _, _ ->
                ProviderResult.Success(snapshot(listOf(remoteEntry("201", "101", 3, updatedAt = NOW + 1.seconds))))
            }
            harness.sync.enqueueRead { _, _, _, _, unit ->
                ProviderResult.Success(remoteEntry("201", "101", 3, unit = unit, updatedAt = NOW + 1.seconds))
            }

            assertEquals(SyncRefreshResult.Success, harness.repository.refresh(lease()))

            val after = checkNotNull(database.mediaDao().getMedia("local-media"))
            val entry = checkNotNull(database.syncDao().getEntry("local-entry"))
            assertEquals("Cached synopsis", after.synopsis)
            assertEquals("https://images.invalid/cached-banner.png", after.bannerReference)
            assertEquals(before.releaseYear, after.releaseYear)
            assertEquals(3, entry.remoteProgress)
            assertEquals(3, entry.effectiveProgress)
            assertEquals(1L, entry.localProgressRevision)
        }

    private suspend fun seedMappedEntry(
        localEntryId: String,
        localMediaId: String,
        remoteEntryId: String,
        remoteMediaId: String,
        progress: Int,
        effectiveProgress: Int = progress,
        revision: Long = 0,
    ) {
        database.mediaDao().upsertMedia(listOf(media(localMediaId)))
        database.collectionEntryDao().upsertEntries(
            listOf(
                CollectionEntryEntity(
                    collectionEntryId = localEntryId,
                    accountId = ACCOUNT_ID.value,
                    mediaId = localMediaId,
                    progressUnit = StoredProgressUnit.EPISODE,
                    remoteStatus = StoredCollectionStatus.CURRENT,
                    remoteProgress = progress,
                    remoteTotal = 12,
                    remoteUpdatedAtUtcMs = NOW,
                    effectiveStatus = StoredCollectionStatus.CURRENT,
                    effectiveProgress = effectiveProgress,
                    effectiveTotal = 12,
                    effectiveUpdatedAtUtcMs = NOW,
                    localProgressRevision = revision,
                ),
            ),
        )
        database.providerIdDao().upsertMediaProviderIds(
            listOf(MediaProviderIdEntity(StoredConnectedProvider.ANILIST, remoteMediaId, localMediaId)),
        )
        database.providerIdDao().upsertCollectionEntryProviderIds(
            listOf(CollectionEntryProviderIdEntity(StoredConnectedProvider.ANILIST, remoteEntryId, localEntryId)),
        )
    }

    private suspend fun seedPendingCorrection() {
        database.pendingOperationDao().insertGroup(
            PendingOperationGroupEntity(
                operationGroupId = "group-forward",
                accountId = ACCOUNT_ID.value,
                createdAtUtcMs = NOW,
                compensatesGroupId = null,
            ),
        )
        database.pendingOperationDao().insertGroup(
            PendingOperationGroupEntity(
                operationGroupId = "group-correction",
                accountId = ACCOUNT_ID.value,
                createdAtUtcMs = NOW + 1.seconds,
                compensatesGroupId = null,
            ),
        )
        database.pendingOperationDao().insertOperations(
            listOf(
                operation("operation-forward", "group-forward", StoredPendingOperationKind.SET_PROGRESS_FORWARD, 1, 2),
                operation(
                    "operation-correction",
                    "group-correction",
                    StoredPendingOperationKind.SET_PROGRESS_CORRECTION,
                    2,
                    3,
                ),
            ),
        )
        database.pendingOperationDao().insertDependencies(
            listOf(PendingOperationDependencyEntity("operation-correction", "operation-forward")),
        )
        database.progressDao().insertActivity(
            LocalProgressActivityEntity(
                operationGroupId = "group-correction",
                accountId = ACCOUNT_ID.value,
                collectionEntryId = "local-entry",
                mediaId = "local-media",
                direction = "increase",
                progressUnit = StoredProgressUnit.EPISODE,
                beforeProgress = 2,
                afterProgress = 3,
                beforeStatus = StoredCollectionStatus.CURRENT,
                afterStatus = StoredCollectionStatus.CURRENT,
                beforeUpdatedAtUtcMs = NOW,
                afterUpdatedAtUtcMs = NOW + 1.seconds,
                afterRevision = 1,
                createdAtUtcMs = NOW + 1.seconds,
                undoUntilUtcMs = NOW + 7.seconds,
                undoneAtUtcMs = null,
            ),
        )
    }

    private fun operation(
        id: String,
        groupId: String,
        kind: StoredPendingOperationKind,
        base: Int,
        target: Int,
    ): PendingOperationEntity =
        PendingOperationEntity(
            operationId = id,
            operationGroupId = groupId,
            accountId = ACCOUNT_ID.value,
            collectionEntryId = "local-entry",
            positionInGroup = 0,
            kind = kind,
            baseProgress = base,
            targetProgress = target,
            baseStatus = null,
            targetStatus = null,
            lifecycleState = StoredPendingOperationState.ENQUEUED,
            attemptCount = 0,
            lastAttemptAtUtcMs = null,
            retryNotBeforeUtcMs = null,
        )

    private suspend fun harness(localClock: Clock = fixedClock()): Harness {
        val binding = providerBindingFixture(ProviderKey.ANILIST)
        val credentials = FakeProviderCredentialStore()
        credentials.write(ProviderCredentials(ProviderKey.ANILIST, "fixture-access", null, null, "Bearer"))
        val identity = binding.identityDataSource as FakeProviderIdentityDataSource
        identity.enqueue {
            ProviderResult.Success(
                ProviderIdentity(ProviderKey.ANILIST, ProviderAccountId(REMOTE_ACCOUNT_ID), "Fixture", null),
            )
        }
        val imports = binding.initialImportDataSource as FakeProviderInitialImportDataSource
        val sync = binding.syncDataSource as FakeProviderSyncDataSource
        val remote =
            ProviderRefreshRemoteDataSource(
                database,
                ProviderSyncCredentialReader(
                    database,
                    RoomSyncAccountLocalDataSource(database),
                    binding,
                    credentials,
                ),
                binding,
            )
        val local = RoomRefreshLocalDataSource(database, ProjectSyncEntryUseCase(), localClock)
        return Harness(
            repository = DefaultRefreshRepository(ACCOUNT_ID, remote, local),
            imports = imports,
            sync = sync,
        )
    }

    private suspend fun lease(): SyncLease =
        checkNotNull(
            RoomSyncLocalDataSource(database, ProjectSyncEntryUseCase(), fixedClock())
                .acquireLease(ACCOUNT_ID, "refresh-owner", NOW, NOW + 120.seconds),
        )

    private fun snapshot(entries: List<ProviderCollectionEntry>): CompleteProviderImport =
        CompleteProviderImport(
            provider = ProviderKey.ANILIST,
            accountId = ProviderAccountId(REMOTE_ACCOUNT_ID),
            media = entries.map { entry -> providerMediaFixture(id = entry.mediaId.value) },
            entries = entries,
        )

    private fun remoteEntry(
        entryId: String,
        mediaId: String,
        progress: Int,
        unit: ProviderProgressUnit = ProviderProgressUnit.EPISODE,
        updatedAt: Instant = NOW,
    ): ProviderCollectionEntry =
        ProviderCollectionEntry(
            id = ProviderCollectionEntryId(entryId),
            accountId = ProviderAccountId(REMOTE_ACCOUNT_ID),
            mediaId = ProviderMediaId(mediaId),
            status = ProviderCollectionStatus.CURRENT,
            progress = progress,
            progressUnit = unit,
            total = 12,
            updatedAt = updatedAt,
        )

    private fun media(id: String): MediaEntity =
        MediaEntity(
            mediaId = id,
            mediaType = StoredMediaType.ANIME,
            title = "Cached title",
            format = StoredMediaFormat.TV,
            publicationStatus = StoredMediaPublicationStatus.RELEASING,
            synopsis = "Cached synopsis",
            posterReference = "https://images.invalid/cached-poster.png",
            bannerReference = "https://images.invalid/cached-banner.png",
            episodeTotal = 12,
            chapterTotal = null,
            volumeTotal = null,
            releaseYear = 2024,
            scorePercent = 86,
        )

    private fun fixedClock(): Clock =
        object : Clock {
            override fun now(): Instant = NOW
        }

    private data class Harness(
        val repository: DefaultRefreshRepository,
        val imports: FakeProviderInitialImportDataSource,
        val sync: FakeProviderSyncDataSource,
    )

    private class SequenceClock(
        values: List<Instant>,
    ) : Clock {
        private val remaining = ArrayDeque(values)
        private val last = values.last()

        override fun now(): Instant = remaining.removeFirstOrNull() ?: last
    }

    private companion object {
        val ACCOUNT_ID = AccountId("account-sync")
        const val REMOTE_ACCOUNT_ID = "42"
        val NOW = Instant.parse("2026-09-11T10:00:00Z")
    }
}
