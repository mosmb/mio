package io.morgan.mio.data.sync

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.entity.SyncAccountIssueEntity
import io.morgan.mio.core.database.entity.SyncIssueEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.sync.local.RoomSyncRecoveryLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.sync.ProgressConflictChoice
import io.morgan.mio.domain.sync.ResolveProgressConflictUseCase
import io.morgan.mio.domain.sync.RetrySyncIssueUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class DefaultSyncRecoveryRepositoryTest {
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
    fun doubleRetryRearmsOnce() =
        runTest {
            seedOperationAndIssue()
            val retry = RetrySyncIssueUseCase(repository())

            assertTrue(retry(OperationId(OPERATION_ID)))
            assertFalse(retry(OperationId(OPERATION_ID)))

            val operation = checkNotNull(database.syncDao().getOperation(OPERATION_ID))
            assertEquals(StoredPendingOperationState.ENQUEUED, operation.lifecycleState)
            assertEquals(0, operation.attemptCount)
            assertNull(operation.lastAttemptAtUtcMs)
            assertNull(operation.retryNotBeforeUtcMs)
            assertNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
        }

    @Test
    fun staleIssueCannotRearmNewFailure() =
        runTest {
            seedOperationAndIssue()
            val captured = checkNotNull(repository().readIssue(OperationId(OPERATION_ID)))
            database.inTransaction {
                database.syncDao().deleteIssue(ACCOUNT_ID.value, ISSUE_ID)
                database.syncDao().upsertIssue(issue(issueId = "issue-replaced", detectedAt = NOW + 1.seconds))
            }

            assertFalse(repository().rearm(captured))
            assertEquals("issue-replaced", database.syncDao().getIssue(ACCOUNT_ID.value, "issue-replaced")?.issueId)
            assertEquals(
                StoredPendingOperationState.RETRYABLE_FAILURE,
                database.syncDao().getOperation(OPERATION_ID)?.lifecycleState,
            )
            assertNull(database.syncDao().getDispatchRequest(ACCOUNT_ID.value))
        }

    @Test
    fun retryDoesNotMutateProgressOrActivity() =
        runTest {
            seedOperationAndIssue(withActivity = true)
            val entryBefore = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            val activityBefore = checkNotNull(database.syncDao().getActivity(ACCOUNT_ID.value, GROUP_ID))

            assertTrue(RetrySyncIssueUseCase(repository())(OperationId(OPERATION_ID)))

            assertEquals(entryBefore, database.syncDao().getEntry(ENTRY_ID))
            assertEquals(activityBefore, database.syncDao().getActivity(ACCOUNT_ID.value, GROUP_ID))
            assertEquals(2, database.syncDao().getEntry(ENTRY_ID)?.effectiveProgress)
            assertEquals(3L, database.syncDao().getEntry(ENTRY_ID)?.localProgressRevision)
        }

    @Test
    fun retrySignalsWorkAtomically() =
        runTest {
            seedOperationAndIssue()
            val issue = checkNotNull(repository().readIssue(OperationId(OPERATION_ID)))
            assertNull(database.syncDao().getDispatchRequest(ACCOUNT_ID.value))

            assertTrue(repository().rearm(issue))

            val request = checkNotNull(database.syncDao().getDispatchRequest(ACCOUNT_ID.value))
            assertEquals(1L, request.requestedGeneration)
            assertEquals(0L, request.dispatchedGeneration)
            assertEquals(
                StoredPendingOperationState.ENQUEUED,
                database.syncDao().getOperation(OPERATION_ID)?.lifecycleState,
            )
            assertNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
        }

    @Test
    fun rollbackKeepsIssueAndGeneration() =
        runTest {
            seedOperationAndIssue()
            val issue = checkNotNull(repository().readIssue(OperationId(OPERATION_ID)))

            try {
                database.inTransaction {
                    assertTrue(repository().rearm(issue))
                    error("force outer transaction rollback")
                }
            } catch (expected: IllegalStateException) {
                assertEquals("force outer transaction rollback", expected.message)
            }

            assertNotNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
            assertEquals(
                StoredPendingOperationState.RETRYABLE_FAILURE,
                database.syncDao().getOperation(OPERATION_ID)?.lifecycleState,
            )
            assertNull(database.syncDao().getDispatchRequest(ACCOUNT_ID.value))
        }

    @Test
    fun reconnectClearsOnlyCapturedAuthenticationIssues() =
        runTest {
            seedOperationAndIssue(kind = "authentication")
            database.syncDao().upsertAccountIssue(
                SyncAccountIssueEntity("account-auth", ACCOUNT_ID.value, "authentication", NOW),
            )
            database.syncDao().upsertIssue(
                issue(
                    issueId = "issue-unrelated",
                    operationId = "operation-unrelated",
                    groupId = "group-unrelated",
                    entryId = OTHER_ENTRY_ID,
                    kind = "rejected",
                ),
            )
            insertOperation(
                operationId = "operation-unrelated",
                groupId = "group-unrelated",
                entryId = OTHER_ENTRY_ID,
                state = StoredPendingOperationState.TERMINAL_FAILURE,
            )

            assertTrue(repository().recoverAuthentication(ACCOUNT_ID, setOf("account-auth", ISSUE_ID)))

            assertNull(database.syncDao().getAccountIssue(ACCOUNT_ID.value))
            assertNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
            assertNotNull(database.syncDao().getIssue(ACCOUNT_ID.value, "issue-unrelated"))
            assertEquals(
                StoredPendingOperationState.ENQUEUED,
                database.syncDao().getOperation(OPERATION_ID)?.lifecycleState,
            )
            assertEquals(
                StoredPendingOperationState.TERMINAL_FAILURE,
                database.syncDao().getOperation("operation-unrelated")?.lifecycleState,
            )
            assertEquals(1L, database.syncDao().getDispatchRequest(ACCOUNT_ID.value)?.requestedGeneration)
        }

    @Test
    fun newerAuthIssueSurvivesOldReconnect() =
        runTest {
            database.syncDao().upsertAccountIssue(
                SyncAccountIssueEntity("auth-new", ACCOUNT_ID.value, "authentication", NOW + 1.seconds),
            )

            assertFalse(repository().recoverAuthentication(ACCOUNT_ID, setOf("auth-captured-before-replacement")))

            assertEquals("auth-new", database.syncDao().getAccountIssue(ACCOUNT_ID.value)?.issueId)
            assertNull(database.syncDao().getDispatchRequest(ACCOUNT_ID.value))
        }

    @Test
    fun unrelatedEntryContinuesAfterOneIssueIsRecovered() =
        runTest {
            seedOperationAndIssue(kind = "authentication")
            insertOperation(
                operationId = "operation-unrelated",
                groupId = "group-unrelated",
                entryId = OTHER_ENTRY_ID,
                state = StoredPendingOperationState.ENQUEUED,
            )
            database.syncDao().upsertIssue(
                issue(
                    issueId = "issue-unrelated",
                    operationId = "operation-unrelated",
                    groupId = "group-unrelated",
                    entryId = OTHER_ENTRY_ID,
                    kind = "rejected",
                ),
            )
            database.syncDao().upsertAccountIssue(
                SyncAccountIssueEntity("account-auth", ACCOUNT_ID.value, "authentication", NOW),
            )

            assertTrue(repository().recoverAuthentication(ACCOUNT_ID, setOf("account-auth", ISSUE_ID)))

            assertEquals(
                StoredPendingOperationState.ENQUEUED,
                database.syncDao().getOperation(OPERATION_ID)?.lifecycleState,
            )
            assertEquals(
                StoredPendingOperationState.ENQUEUED,
                database.syncDao().getOperation("operation-unrelated")?.lifecycleState,
            )
            assertNotNull(database.syncDao().getIssue(ACCOUNT_ID.value, "issue-unrelated"))
        }

    @Test
    fun resolvingConflictCancelsObsoleteEntryWorkAndLetsUnrelatedEntriesContinue() =
        runTest {
            seedProgressConflict()
            insertOperation(
                operationId = "operation-unrelated",
                groupId = "group-unrelated",
                entryId = OTHER_ENTRY_ID,
                state = StoredPendingOperationState.ENQUEUED,
            )
            val repository = repository()
            val issue = checkNotNull(repository.readIssue(OperationId(OPERATION_ID)))
            val resolve = ResolveProgressConflictUseCase(repository, RecoveryClock(NOW + 1.seconds))

            assertEquals(9, issue.local.progress.value)
            assertEquals(7, issue.remote?.progress?.value)
            assertTrue(
                resolve(
                    expectedIssue = issue,
                    choice = ProgressConflictChoice.KEEP_DEVICE,
                    groupId = OperationGroupId("resolution-device-value"),
                ),
            )

            assertEquals(
                StoredPendingOperationState.CANCELLED,
                database.syncDao().getOperation(OPERATION_ID)?.lifecycleState,
            )
            assertNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
            val resolvedOperation = checkNotNull(database.syncDao().getOperation("resolution-device-value:progress"))
            assertEquals(StoredPendingOperationKind.SET_PROGRESS_CORRECTION, resolvedOperation.kind)
            assertEquals(7, resolvedOperation.baseProgress)
            assertEquals(9, resolvedOperation.targetProgress)
            assertEquals(
                StoredPendingOperationState.ENQUEUED,
                database.syncDao().getOperation("operation-unrelated")?.lifecycleState,
            )
            assertEquals(1L, database.syncDao().getDispatchRequest(ACCOUNT_ID.value)?.requestedGeneration)

            val otherEntryCandidate =
                database.syncDao().getNextCandidate(ACCOUNT_ID.value, NOW + 1.seconds, NOW - 120.seconds)
            assertEquals("operation-unrelated", otherEntryCandidate?.operationId)
            assertEquals(
                1,
                database.syncDao().claimOperation(
                    ACCOUNT_ID.value,
                    "operation-unrelated",
                    NOW + 1.seconds,
                    NOW - 120.seconds,
                ),
            )
            database.syncDao().setOperationState(
                ACCOUNT_ID.value,
                "operation-unrelated",
                StoredPendingOperationState.DELIVERED,
                null,
            )
            assertEquals(
                "resolution-device-value:progress",
                database.syncDao().getNextCandidate(ACCOUNT_ID.value, NOW + 2.seconds, NOW - 120.seconds)?.operationId,
            )
        }

    @Test
    fun convergedProgressCanBeAcknowledgedWithoutWritingBack() =
        runTest {
            seedProgressConflict()
            val entry = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            database.collectionEntryDao().upsertEntries(listOf(entry.copy(remoteProgress = entry.effectiveProgress)))
            val repository = repository()
            val issue = checkNotNull(repository.readIssue(OperationId(OPERATION_ID)))
            assertTrue(
                ResolveProgressConflictUseCase(repository, RecoveryClock(NOW + 1.seconds))(
                    issue,
                    ProgressConflictChoice.KEEP_DEVICE,
                    OperationGroupId("converged-choice"),
                ),
            )
            assertEquals(
                StoredPendingOperationState.DELIVERED,
                database.syncDao().getOperation("converged-choice:progress")?.lifecycleState,
            )
            assertNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
        }

    @Test
    fun convergedProgressStillPreservesTheChosenDeviceCompletion() =
        runTest {
            seedProgressConflict()
            val entry = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            database.collectionEntryDao().upsertEntries(
                listOf(
                    entry.copy(
                        remoteProgress = 12,
                        effectiveProgress = 12,
                        remoteStatus = StoredCollectionStatus.CURRENT,
                        effectiveStatus = StoredCollectionStatus.COMPLETED,
                    ),
                ),
            )
            val repository = repository()
            val issue = checkNotNull(repository.readIssue(OperationId(OPERATION_ID)))
            assertTrue(
                ResolveProgressConflictUseCase(repository, RecoveryClock(NOW + 1.seconds))(
                    issue,
                    ProgressConflictChoice.KEEP_DEVICE,
                    OperationGroupId("converged-completion"),
                ),
            )
            assertEquals(StoredCollectionStatus.COMPLETED, database.syncDao().getEntry(ENTRY_ID)?.effectiveStatus)
            assertEquals(
                StoredPendingOperationState.DELIVERED,
                database.syncDao().getOperation("converged-completion:progress")?.lifecycleState,
            )
            val status = checkNotNull(database.syncDao().getOperation("converged-completion:status"))
            assertEquals(StoredPendingOperationState.ENQUEUED, status.lifecycleState)
            assertEquals(StoredCollectionStatus.COMPLETED, status.targetStatus)
            assertEquals(
                status.operationId,
                database.syncDao().getNextCandidate(ACCOUNT_ID.value, NOW + 2.seconds, NOW - 120.seconds)?.operationId,
            )
        }

    @Test
    fun providerStatusChoicePreservesProgressWork() =
        runTest {
            seedProgressConflict(status = true)
            val entry = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            database.collectionEntryDao().upsertEntries(
                listOf(
                    entry.copy(
                        remoteStatus = StoredCollectionStatus.PAUSED,
                        effectiveStatus = StoredCollectionStatus.COMPLETED,
                    ),
                ),
            )
            val persistedIssue = checkNotNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
            database.syncDao().upsertIssue(
                persistedIssue.copy(
                    baseProgress = null,
                    targetProgress = null,
                    baseStatus = StoredCollectionStatus.CURRENT,
                    targetStatus = StoredCollectionStatus.COMPLETED,
                    localStatus = StoredCollectionStatus.COMPLETED,
                    remoteStatus = StoredCollectionStatus.PAUSED,
                ),
            )
            insertOperation("later-progress", "later-progress-group", ENTRY_ID, StoredPendingOperationState.ENQUEUED)
            val repository = repository()
            val issue = checkNotNull(repository.readIssue(OperationId(OPERATION_ID)))
            assertTrue(
                ResolveProgressConflictUseCase(repository, RecoveryClock(NOW + 1.seconds))(
                    issue,
                    ProgressConflictChoice.USE_PROVIDER,
                    OperationGroupId("status-choice"),
                ),
            )
            assertEquals(StoredCollectionStatus.PAUSED, database.syncDao().getEntry(ENTRY_ID)?.effectiveStatus)
            assertEquals(
                StoredPendingOperationState.ENQUEUED,
                database.syncDao().getOperation("later-progress")?.lifecycleState,
            )
            val choice = checkNotNull(database.syncDao().getOperation("status-choice:status"))
            assertEquals(StoredPendingOperationKind.SET_STATUS_CORRECTION, choice.kind)
            assertEquals(StoredPendingOperationState.DELIVERED, choice.lifecycleState)
            assertNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
        }

    @Test
    fun keepingFinalDeviceProgressRetainsItsCompletionIntent() =
        runTest {
            seedProgressConflict()
            val entry = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            database.collectionEntryDao().upsertEntries(
                listOf(
                    entry.copy(
                        effectiveProgress = 12,
                        effectiveStatus = StoredCollectionStatus.COMPLETED,
                    ),
                ),
            )
            val repository = repository()
            val issue = checkNotNull(repository.readIssue(OperationId(OPERATION_ID)))
            assertTrue(
                ResolveProgressConflictUseCase(repository, RecoveryClock(NOW + 1.seconds))(
                    issue,
                    ProgressConflictChoice.KEEP_DEVICE,
                    OperationGroupId("keep-completion"),
                ),
            )
            assertEquals(StoredCollectionStatus.COMPLETED, database.syncDao().getEntry(ENTRY_ID)?.effectiveStatus)
            val status = checkNotNull(database.syncDao().getOperation("keep-completion:status"))
            assertEquals(StoredPendingOperationKind.SET_STATUS_CORRECTION, status.kind)
            assertEquals(StoredCollectionStatus.COMPLETED, status.targetStatus)
            assertEquals(
                "keep-completion:progress",
                database
                    .syncDao()
                    .getDependenciesForOperation(status.operationId)
                    .single()
                    .dependsOnOperationId,
            )
        }

    @Test
    fun persistedConflictUsesItsNewerProviderSnapshotBeforeAnyRefresh() =
        runTest {
            seedProgressConflict()
            val entry = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            database.collectionEntryDao().upsertEntries(
                listOf(entry.copy(remoteProgress = 6, remoteUpdatedAtUtcMs = NOW - 1.seconds)),
            )
            val repository = repository()
            val issue = checkNotNull(repository.readIssue(OperationId(OPERATION_ID)))
            assertEquals(7, issue.remote?.progress?.value)
            assertEquals(
                issue,
                repository
                    .observeState()
                    .first()
                    .entryIssues
                    .single(),
            )
            assertTrue(
                ResolveProgressConflictUseCase(repository, RecoveryClock(NOW + 1.seconds))(
                    issue,
                    ProgressConflictChoice.USE_PROVIDER,
                    OperationGroupId("old-conflict-provider"),
                ),
            )
            val resolved = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            assertEquals(7, resolved.remoteProgress)
            assertEquals(7, resolved.effectiveProgress)
            assertEquals(
                StoredPendingOperationState.DELIVERED,
                database.syncDao().getOperation("old-conflict-provider:progress")?.lifecycleState,
            )
            assertNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
        }

    @Test
    fun staleResolutionLeavesPendingWorkUntouched() =
        runTest {
            seedProgressConflict()
            val repository = repository()
            val issue = checkNotNull(repository.readIssue(OperationId(OPERATION_ID)))
            val entry = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            val resolve = ResolveProgressConflictUseCase(repository, RecoveryClock(NOW + 1.seconds))
            for (changed in listOf(entry.copy(remoteProgress = 8), entry.copy(effectiveProgress = 10))) {
                database.collectionEntryDao().upsertEntries(listOf(changed))
                assertFalse(resolve(issue, ProgressConflictChoice.USE_PROVIDER, OperationGroupId("stale-resolution")))
                assertNotNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
                assertEquals(
                    StoredPendingOperationState.TERMINAL_FAILURE,
                    database.syncDao().getOperation(OPERATION_ID)?.lifecycleState,
                )
                assertNull(database.syncDao().getOperation("stale-resolution:progress"))
            }
        }

    @Test
    fun choosingProviderProjectsTheChosenValueBeforeNetworkDelivery() =
        runTest {
            seedProgressConflict()
            val repository = repository()
            val issue = checkNotNull(repository.readIssue(OperationId(OPERATION_ID)))
            assertTrue(
                ResolveProgressConflictUseCase(repository, RecoveryClock(NOW + 1.seconds))(
                    issue,
                    ProgressConflictChoice.USE_PROVIDER,
                    OperationGroupId("resolution-provider-value"),
                ),
            )
            val entry = checkNotNull(database.syncDao().getEntry(ENTRY_ID))
            assertEquals(7, entry.effectiveProgress)
            assertEquals(5L, entry.localProgressRevision)
            assertEquals(
                StoredPendingOperationState.DELIVERED,
                database.syncDao().getOperation("resolution-provider-value:progress")?.lifecycleState,
            )
            assertNull(database.syncDao().getNextCandidate(ACCOUNT_ID.value, NOW + 2.seconds, NOW - 120.seconds))
            assertNull(database.syncDao().getIssue(ACCOUNT_ID.value, ISSUE_ID))
        }

    private fun repository(): DefaultSyncRecoveryRepository =
        DefaultSyncRecoveryRepository(ACCOUNT_ID, RoomSyncRecoveryLocalDataSource(database))

    private suspend fun seedOperationAndIssue(
        kind: String = "rejected",
        withActivity: Boolean = false,
    ) {
        database.mediaDao().upsertMedia(listOf(media(MEDIA_ID), media(OTHER_MEDIA_ID)))
        database.collectionEntryDao().upsertEntries(
            listOf(entry(ENTRY_ID, MEDIA_ID), entry(OTHER_ENTRY_ID, OTHER_MEDIA_ID)),
        )
        database.pendingOperationDao().insertGroup(
            PendingOperationGroupEntity(
                operationGroupId = GROUP_ID,
                accountId = ACCOUNT_ID.value,
                createdAtUtcMs = NOW,
                compensatesGroupId = null,
            ),
        )
        insertOperation(OPERATION_ID, GROUP_ID, ENTRY_ID, StoredPendingOperationState.RETRYABLE_FAILURE)
        database.syncDao().upsertIssue(issue(kind = kind))
        if (withActivity) {
            database.progressDao().insertActivity(
                LocalProgressActivityEntity(
                    operationGroupId = GROUP_ID,
                    accountId = ACCOUNT_ID.value,
                    collectionEntryId = ENTRY_ID,
                    mediaId = MEDIA_ID,
                    direction = "increase",
                    progressUnit = StoredProgressUnit.EPISODE,
                    beforeProgress = 1,
                    afterProgress = 2,
                    beforeStatus = StoredCollectionStatus.CURRENT,
                    afterStatus = StoredCollectionStatus.CURRENT,
                    beforeUpdatedAtUtcMs = NOW - 1.seconds,
                    afterUpdatedAtUtcMs = NOW,
                    afterRevision = 3,
                    createdAtUtcMs = NOW,
                    undoUntilUtcMs = NOW + 6.seconds,
                    undoneAtUtcMs = null,
                ),
            )
        }
    }

    private suspend fun seedProgressConflict(status: Boolean = false) {
        database.mediaDao().upsertMedia(listOf(media(MEDIA_ID)))
        database.collectionEntryDao().upsertEntries(
            listOf(
                entry(ENTRY_ID, MEDIA_ID).copy(
                    remoteProgress = 7,
                    effectiveProgress = 9,
                    localProgressRevision = 4,
                ),
            ),
        )
        database.pendingOperationDao().insertGroup(
            PendingOperationGroupEntity(
                operationGroupId = GROUP_ID,
                accountId = ACCOUNT_ID.value,
                createdAtUtcMs = NOW,
                compensatesGroupId = null,
            ),
        )
        database.pendingOperationDao().insertOperations(
            listOf(
                PendingOperationEntity(
                    operationId = OPERATION_ID,
                    operationGroupId = GROUP_ID,
                    accountId = ACCOUNT_ID.value,
                    collectionEntryId = ENTRY_ID,
                    positionInGroup = 0,
                    kind =
                        if (status) {
                            StoredPendingOperationKind.SET_STATUS
                        } else {
                            StoredPendingOperationKind.SET_PROGRESS_CORRECTION
                        },
                    baseProgress = if (status) null else 5,
                    targetProgress = if (status) null else 9,
                    baseStatus = if (status) StoredCollectionStatus.CURRENT else null,
                    targetStatus = if (status) StoredCollectionStatus.COMPLETED else null,
                    lifecycleState = StoredPendingOperationState.TERMINAL_FAILURE,
                    attemptCount = 1,
                    lastAttemptAtUtcMs = NOW,
                    retryNotBeforeUtcMs = null,
                ),
            ),
        )
        database.syncDao().upsertIssue(
            issue(kind = "conflict").copy(
                baseProgress = 5,
                targetProgress = 9,
                localProgress = 9,
                localRevision = 4,
                remoteProgress = 7,
                remoteStatus = StoredCollectionStatus.CURRENT,
                remoteTotal = 12,
                remoteUpdatedAtUtcMs = NOW,
            ),
        )
    }

    private suspend fun insertOperation(
        operationId: String,
        groupId: String,
        entryId: String,
        state: StoredPendingOperationState,
    ) {
        if (database.pendingOperationDao().getGroup(groupId) == null) {
            database.pendingOperationDao().insertGroup(
                PendingOperationGroupEntity(
                    operationGroupId = groupId,
                    accountId = ACCOUNT_ID.value,
                    createdAtUtcMs = NOW,
                    compensatesGroupId = null,
                ),
            )
        }
        if (database.syncDao().getEntry(entryId) == null) {
            val mediaId = if (entryId == ENTRY_ID) MEDIA_ID else OTHER_MEDIA_ID
            if (database.mediaDao().getMedia(mediaId) == null) database.mediaDao().upsertMedia(listOf(media(mediaId)))
            database.collectionEntryDao().upsertEntries(listOf(entry(entryId, mediaId)))
        }
        database.pendingOperationDao().insertOperations(
            listOf(
                PendingOperationEntity(
                    operationId = operationId,
                    operationGroupId = groupId,
                    accountId = ACCOUNT_ID.value,
                    collectionEntryId = entryId,
                    positionInGroup = 0,
                    kind = StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                    baseProgress = 1,
                    targetProgress = 2,
                    baseStatus = null,
                    targetStatus = null,
                    lifecycleState = state,
                    attemptCount = if (state == StoredPendingOperationState.ENQUEUED) 0 else 1,
                    lastAttemptAtUtcMs = if (state == StoredPendingOperationState.ENQUEUED) null else NOW,
                    retryNotBeforeUtcMs =
                        if (state == StoredPendingOperationState.RETRYABLE_FAILURE) NOW + 1.seconds else null,
                ),
            ),
        )
    }

    private fun media(mediaId: String): MediaEntity =
        MediaEntity(
            mediaId = mediaId,
            mediaType = StoredMediaType.ANIME,
            title = "Fixture $mediaId",
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
            accountId = ACCOUNT_ID.value,
            mediaId = mediaId,
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 1,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = NOW,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = 2,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = NOW,
            localProgressRevision = 3,
        )

    private fun issue(
        issueId: String = ISSUE_ID,
        operationId: String = OPERATION_ID,
        groupId: String = GROUP_ID,
        entryId: String = ENTRY_ID,
        kind: String = "rejected",
        detectedAt: Instant = NOW,
    ): SyncIssueEntity =
        SyncIssueEntity(
            issueId = issueId,
            accountId = ACCOUNT_ID.value,
            collectionEntryId = entryId,
            operationGroupId = groupId,
            operationId = operationId,
            kind = kind,
            baseProgress = 1,
            baseStatus = null,
            targetProgress = 2,
            targetStatus = null,
            progressUnit = StoredProgressUnit.EPISODE,
            localProgress = 2,
            localStatus = StoredCollectionStatus.CURRENT,
            localTotal = 12,
            localUpdatedAtUtcMs = NOW,
            localRevision = 3,
            remoteProgress = null,
            remoteStatus = null,
            remoteTotal = null,
            remoteUpdatedAtUtcMs = null,
            detectedAtUtcMs = detectedAt,
        )

    private companion object {
        val ACCOUNT_ID = AccountId("account-recovery")
        const val REMOTE_ACCOUNT_ID = "42"
        const val MEDIA_ID = "media-recovery"
        const val OTHER_MEDIA_ID = "media-unrelated"
        const val ENTRY_ID = "entry-recovery"
        const val OTHER_ENTRY_ID = "entry-unrelated"
        const val GROUP_ID = "group-recovery"
        const val OPERATION_ID = "operation-recovery"
        const val ISSUE_ID = "operation-recovery:sync"
        val NOW = Instant.parse("2026-09-16T12:00:00Z")
    }

    private class RecoveryClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
