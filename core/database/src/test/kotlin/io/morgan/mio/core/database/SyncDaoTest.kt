package io.morgan.mio.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.entity.SyncIssueEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
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
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class SyncDaoTest {
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

    @Test
    fun leaseIsFencedAcrossTwoFileBackedConnectionsAndExpiresForTakeover() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "task20-sync-${UUID.randomUUID()}.db"
            val first = MioDatabaseFactory.create(context, name)
            val second = MioDatabaseFactory.create(context, name)
            try {
                seed(first)
                first.syncDao().tryAcquireLease("account-sync", "owner-a", NOW, NOW + 120.seconds)
                assertEquals("owner-a", first.syncDao().getLease("account-sync")?.ownerToken)

                second.syncDao().tryAcquireLease("account-sync", "owner-b", NOW, NOW + 120.seconds)
                assertEquals("owner-a", second.syncDao().getLease("account-sync")?.ownerToken)
                assertEquals(
                    0,
                    second.syncDao().renewLease("account-sync", "owner-b", NOW, NOW + 120.seconds),
                )

                val takeoverAt = NOW + 121.seconds
                second.syncDao().tryAcquireLease("account-sync", "owner-b", takeoverAt, takeoverAt + 120.seconds)
                assertEquals("owner-b", first.syncDao().getLease("account-sync")?.ownerToken)
                assertEquals(
                    0,
                    first.syncDao().releaseLease("account-sync", "owner-a", takeoverAt),
                )
                assertEquals(
                    1,
                    second.syncDao().renewLease("account-sync", "owner-b", takeoverAt, takeoverAt + 120.seconds),
                )
            } finally {
                first.close()
                second.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun candidateOrderingBlocksOnlyTheSameEntryAfterATerminalFailure() =
        runTest {
            insertGroup("group-one", 1, "entry-one", "operation-one")
            insertGroup("group-two", 2, "entry-one", "operation-two")
            insertGroup("group-three", 4, "entry-two", "operation-three")

            assertEquals(
                "operation-one",
                database.syncDao().getNextCandidate("account-sync", NOW, NOW - 120.seconds)?.operationId,
            )
            claimAndSet("operation-one", StoredPendingOperationState.DELIVERED)
            assertEquals(
                "operation-two",
                database.syncDao().getNextCandidate("account-sync", NOW, NOW - 120.seconds)?.operationId,
            )

            claimAndSet("operation-two", StoredPendingOperationState.TERMINAL_FAILURE)
            insertGroup("group-blocked", 3, "entry-one", "operation-blocked")
            assertNull(
                database.syncDao().getNextCandidate("account-sync", NOW, NOW - 120.seconds)?.takeIf {
                    it.operationId ==
                        "operation-two"
                },
            )
            assertEquals(
                "operation-three",
                database.syncDao().getNextCandidate("account-sync", NOW, NOW - 120.seconds)?.operationId,
            )
        }

    @Test
    fun scheduleAcknowledgementUsesCapturedGenerationAndIssueRowsAreObservable() =
        runTest {
            database.syncDao().requestDispatch("account-sync")
            assertEquals(1L, database.syncDao().getDispatchRequest("account-sync")?.requestedGeneration)
            database.syncDao().requestDispatch("account-sync")
            assertEquals(2L, database.syncDao().getDispatchRequest("account-sync")?.requestedGeneration)
            assertEquals(0, database.syncDao().acknowledgeDispatch("account-sync", 1L))
            assertEquals(1, database.syncDao().acknowledgeDispatch("account-sync", 2L))
            val schedule = database.syncDao().getDispatchRequest("account-sync")
            assertNotNull(schedule)
            assertEquals(2L, schedule?.requestedGeneration)
            assertEquals(2L, schedule?.dispatchedGeneration)

            database.mediaDao().upsertMedia(listOf(media("media-sync")))
            database.collectionEntryDao().upsertEntries(listOf(entry("entry-sync", "media-sync")))
            database.syncDao().upsertIssue(issue("issue-sync", "entry-sync"))

            val observed = database.syncDao().observeIssuesForAccount("account-sync").first()
            assertEquals(listOf("issue-sync"), observed.map { it.issueId })
            assertEquals(1, database.syncDao().getIssues("account-sync").size)
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
            database.syncDao().setOperationState("account-sync", operationId, state, null),
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

    private fun issue(
        issueId: String,
        entryId: String,
    ): SyncIssueEntity =
        SyncIssueEntity(
            issueId = issueId,
            accountId = "account-sync",
            collectionEntryId = entryId,
            operationGroupId = "group-issue",
            operationId = "operation-issue",
            kind = "conflict",
            baseProgress = 1,
            baseStatus = null,
            targetProgress = 2,
            targetStatus = null,
            progressUnit = StoredProgressUnit.EPISODE,
            localProgress = 1,
            localStatus = StoredCollectionStatus.CURRENT,
            localTotal = 12,
            localUpdatedAtUtcMs = NOW,
            localRevision = 0,
            remoteProgress = 3,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = NOW,
            detectedAtUtcMs = NOW,
        )

    private companion object {
        val NOW = Instant.parse("2026-09-11T10:00:00Z")
    }
}
