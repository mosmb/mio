package io.morgan.mio.data.sync

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.sync.local.RoomSyncLocalDataSource
import io.morgan.mio.data.sync.mapper.toSyncEntryState
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.ProjectSyncEntryUseCase
import io.morgan.mio.domain.sync.SyncCommitResult
import io.morgan.mio.domain.sync.SyncIssueKind
import io.morgan.mio.domain.sync.SyncLease
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class DefaultSyncRepositoryTest {
    private lateinit var database: MioDatabase

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        runTest { seed(database) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private var now = NOW
    private val local get() =
        RoomSyncLocalDataSource(
            database,
            ProjectSyncEntryUseCase(),
            object : Clock {
                override fun now() = now
            },
        )

    private suspend fun lease(): SyncLease =
        checkNotNull(
            local.acquireLease(
                AccountId("account-sync"),
                "owner",
                now,
                now + 120.seconds,
            ),
        )

    @Test
    fun leaseAcquisitionAndRenewalTolerateRoomMillisecondPrecision() =
        runTest {
            now = Instant.parse("2026-09-11T10:00:00.123456789Z")
            val acquired = lease()
            assertEquals((now + 120.seconds).toEpochMilliseconds(), acquired.expiresAt.toEpochMilliseconds())
            assertNotNull(local.renewLease(acquired, now, now + 120.seconds))
        }

    @Test
    fun recentlyInterruptedOperationRetainsWakeUpUntilItCanBeReclaimed() =
        runTest {
            insertGroup("group", 1, "entry-one", "operation")
            val owner = lease()
            assertNotNull(local.claimNext(owner, now))
            local.releaseLease(owner)
            now += 1.seconds
            val restarted = lease()
            assertNull(local.claimNext(restarted, now))
            assertEquals(NOW + 120.seconds, local.nextWakeAt(restarted, now))
            now = NOW + 120.seconds
            assertEquals(2, local.claimNext(restarted, now)?.operation?.attemptCount)
        }

    @Test
    fun fifthClaimIsAvailableButAnAbandonedFifthClaimIsExhausted() =
        runTest {
            insertGroup("group", 1, "entry-one", "operation")
            repeat(4) {
                claimAndSet("operation", StoredPendingOperationState.RETRYABLE_FAILURE)
            }
            val owner = lease()
            val fifth = checkNotNull(local.claimNext(owner, now))
            assertEquals(5, fifth.operation.attemptCount)
            assertEquals(false, fifth.attemptBudgetExhausted)
            now += 121.seconds
            val successor = lease()
            assertEquals(true, local.claimNext(successor, now)?.attemptBudgetExhausted)
        }

    @Test
    fun snapshotFailureRollsBackAllRowsAndOperationSettlement() =
        runTest {
            insertGroup("first", 1, "entry-one", "operation-one")
            insertGroup("second", 2, "entry-two", "operation-two")
            val owner = lease()
            val first = checkNotNull(local.claimNext(owner, now))
            val before = checkNotNull(database.syncDao().getEntry("entry-one"))
            val second = checkNotNull(database.syncDao().getEntry("entry-two"))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_snapshot BEFORE UPDATE ON collection_entry WHEN NEW.collection_entry_id = 'entry-two' BEGIN SELECT RAISE(ABORT, 'fixture'); END",
            )
            val failed =
                runCatching {
                    local.commitSnapshot(
                        owner,
                        listOf(before.toSyncEntryState(), second.toSyncEntryState()),
                        setOf(first.operation.id),
                    )
                }
            org.junit.Assert.assertTrue(failed.isFailure)
            assertEquals(before, database.syncDao().getEntry("entry-one"))
            assertEquals(
                StoredPendingOperationState.IN_FLIGHT,
                database.syncDao().getOperation("operation-one")?.lifecycleState,
            )
        }

    @Test
    fun leaseExpiryDuringSnapshotRollsBackInsteadOfReturningAfterCommit() =
        runTest {
            insertGroup("first", 1, "entry-one", "operation-one")
            val owner = lease()
            val candidate = checkNotNull(local.claimNext(owner, now))
            val before = checkNotNull(database.syncDao().getEntry("entry-one"))
            var reads = 0
            val expiring =
                RoomSyncLocalDataSource(
                    database,
                    ProjectSyncEntryUseCase(),
                    object : Clock {
                        override fun now() =
                            if (reads++ ==
                                0
                            ) {
                                now
                            } else {
                                now + 121.seconds
                            }
                    },
                )
            assertEquals(
                SyncCommitResult.LeaseLost,
                expiring.commitSnapshot(owner, listOf(before.toSyncEntryState()), setOf(candidate.operation.id)),
            )
            assertEquals(
                StoredPendingOperationState.IN_FLIGHT,
                database.syncDao().getOperation("operation-one")?.lifecycleState,
            )
            assertEquals(before, database.syncDao().getEntry("entry-one"))
        }

    @Test
    fun partialSnapshotDoesNotDeleteOmittedEntriesAndStaleOwnerCannotWrite() =
        runTest {
            insertGroup("first", 1, "entry-one", "operation-one")
            insertGroup("second", 2, "entry-two", "operation-two")
            val owner = lease()
            val first = checkNotNull(database.syncDao().getEntry("entry-one"))
            val second = database.syncDao().getEntry("entry-two")
            assertEquals(
                SyncCommitResult.Applied,
                local.commitSnapshot(owner, listOf(first.toSyncEntryState()), emptySet()),
            )
            assertEquals(second, database.syncDao().getEntry("entry-two"))
            now += 121.seconds
            local.acquireLease(AccountId("account-sync"), "successor", now, now + 120.seconds)
            assertEquals(
                SyncCommitResult.LeaseLost,
                local.commitSnapshot(owner, listOf(first.toSyncEntryState()), emptySet()),
            )
        }

    @Test
    fun terminalIssueBlocksSameEntryAndSurvivesASecondRunner() =
        runTest {
            insertGroup("first", 1, "entry-one", "operation-one")
            insertGroup("second", 2, "entry-one", "operation-two")
            insertGroup("third", 3, "entry-two", "operation-three")
            val owner = lease()
            val candidate = checkNotNull(local.claimNext(owner, now))
            assertEquals(
                SyncCommitResult.Applied,
                local.recordFailure(owner, candidate, SyncIssueKind.REJECTED, null, null),
            )
            local.releaseLease(owner)
            val next = lease()
            assertEquals(
                "operation-three",
                local
                    .claimNext(next, now)
                    ?.operation
                    ?.id
                    ?.value,
            )
            assertEquals(
                "rejected",
                database
                    .syncDao()
                    .getIssues("account-sync")
                    .single()
                    .kind,
            )
        }

    private suspend fun seed(target: MioDatabase) {
        target.selectedAccountDao().replaceSelectedAccount(
            SelectedAccountEntity(
                selectionKey = "selected",
                accountId = "account-sync",
                connectedProvider = StoredConnectedProvider.ANILIST,
                displayName = "Sync account",
                avatarReference = null,
            ),
            AccountProviderIdEntity(
                accountId = "account-sync",
                provider = StoredConnectedProvider.ANILIST,
                remoteAccountId = "42",
            ),
        )
    }

    private suspend fun insertGroup(
        groupId: String,
        order: Long,
        entryId: String,
        operationId: String,
    ) {
        if (database.collectionEntryDao().getEntry(entryId) == null) {
            val mediaId = if (entryId == "entry-two") "media-two" else "media-one"
            database.mediaDao().upsertMedia(listOf(media(mediaId)))
            database.collectionEntryDao().upsertEntries(listOf(entry(entryId, mediaId)))
        }
        database.pendingOperationDao().insertGroup(
            PendingOperationGroupEntity(
                groupOrder = order,
                operationGroupId = groupId,
                accountId = "account-sync",
                createdAtUtcMs = NOW,
                compensatesGroupId = null,
            ),
        )
        database.pendingOperationDao().insertOperations(
            listOf(
                PendingOperationEntity(
                    operationId = operationId,
                    operationGroupId = groupId,
                    accountId = "account-sync",
                    collectionEntryId = entryId,
                    positionInGroup = 0,
                    kind = StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                    baseProgress = 1,
                    targetProgress = 2,
                    baseStatus = null,
                    targetStatus = null,
                    lifecycleState = StoredPendingOperationState.ENQUEUED,
                    attemptCount = 0,
                    lastAttemptAtUtcMs = null,
                    retryNotBeforeUtcMs = null,
                ),
            ),
        )
    }

    private suspend fun claimAndSet(
        operationId: String,
        state: StoredPendingOperationState,
    ) {
        assertEquals(1, database.syncDao().claimOperation("account-sync", operationId, NOW, NOW - 120.seconds))
        assertEquals(
            1,
            database.syncDao().setOperationState(
                "account-sync",
                operationId,
                state,
                if (state ==
                    StoredPendingOperationState.RETRYABLE_FAILURE
                ) {
                    NOW
                } else {
                    null
                },
            ),
        )
    }

    private fun media(mediaId: String): MediaEntity =
        MediaEntity(
            mediaId = mediaId,
            mediaType = StoredMediaType.ANIME,
            title = "Sync media $mediaId",
            format = StoredMediaFormat.TV,
            publicationStatus = StoredMediaPublicationStatus.RELEASING,
            synopsis = null,
            posterReference = null,
            bannerReference = null,
            episodeTotal = 12,
            chapterTotal = null,
            volumeTotal = null,
        )

    private fun entry(
        entryId: String,
        mediaId: String,
    ): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = entryId,
            accountId = "account-sync",
            mediaId = mediaId,
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 1,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = NOW,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = 1,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = NOW,
        )

    private companion object {
        val NOW = Instant.parse("2026-09-11T10:00:00Z")
    }
}
