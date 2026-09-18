package io.morgan.mio.data.progress

import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.progress.DefaultProgressActivityRepository
import io.morgan.mio.data.progress.local.RoomProgressLocalDataSource
import io.morgan.mio.data.sync.DefaultPendingOperationWriter
import io.morgan.mio.data.sync.PendingOperationWriter
import io.morgan.mio.data.sync.local.RoomPendingOperationLocalDataSource
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.progress.CorrectProgressUseCase
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressWriteResult
import io.morgan.mio.domain.progress.RecordedProgressActivity
import io.morgan.mio.domain.progress.StepProgressUseCase
import io.morgan.mio.domain.progress.UndoProgressUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class DefaultProgressRepositoryTest {
    private lateinit var database: MioDatabase
    private lateinit var repository: DefaultProgressRepository
    private lateinit var clock: RepositoryClock
    private val accountId = AccountId("account-progress")
    private val mediaId = MediaId("media-progress")
    private val baseline = Instant.parse("2026-08-23T12:00:00Z")

    @Before
    fun setUp() =
        runTest {
            database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
            database.selectedAccountDao().replaceSelectedAccount(account(), mapping())
            database.mediaDao().upsertMedia(listOf(media()))
            database.collectionEntryDao().upsertEntries(listOf(entry()))
            clock = RepositoryClock(baseline)
            repository = createRepository()
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun finalIncreaseAtomicallyCompletesAndPreservesRemoteBaseline() =
        runTest {
            val result =
                StepProgressUseCase(repository, clock)(
                    mediaId = mediaId,
                    direction = ProgressDirection.INCREASE,
                    actionId = OperationGroupId("group-final"),
                    undoWindow = 6.seconds,
                )

            val committed = result as ProgressWriteResult.Committed
            val stored = requireNotNull(database.progressDao().getEntryForMedia(accountId.value, mediaId.value))
            val operations = database.pendingOperationDao().getOperationsForGroup("group-final")
            val activity = database.progressDao().getActivity(accountId.value, "group-final")

            assertEquals(12, stored.effectiveProgress)
            assertEquals(StoredCollectionStatus.COMPLETED, stored.effectiveStatus)
            assertEquals(11, stored.remoteProgress)
            assertEquals(StoredCollectionStatus.CURRENT, stored.remoteStatus)
            assertEquals(1, stored.localProgressRevision)
            assertEquals(
                listOf(
                    StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                    StoredPendingOperationKind.SET_STATUS,
                ),
                operations.map {
                    it.kind
                },
            )
            assertEquals(0, operations[0].positionInGroup)
            assertEquals(1, operations[1].positionInGroup)
            assertEquals("group-final:status", operations[1].operationId)
            assertEquals(1, database.openHelper.readableDatabase.count("local_progress_activity"))
            assertEquals(committed.action.id.value, activity?.operationGroupId)
            assertEquals(committed.action.afterRevision, activity?.afterRevision)
            assertEquals(committed.action.afterProgress.value, activity?.afterProgress)
            assertEquals(CollectionStatus.COMPLETED, committed.action.afterStatus)
        }

    @Test
    fun decreaseUsesCorrectionIntentAndNeverChangesTheUnit() =
        runTest {
            database.collectionEntryDao().upsertEntries(listOf(entry(progress = 8)))

            val result =
                StepProgressUseCase(repository, clock)(
                    mediaId,
                    ProgressDirection.DECREASE,
                    OperationGroupId("group-correction"),
                    6.seconds,
                )
            assertTrue(result is ProgressWriteResult.Committed)

            val operation = database.pendingOperationDao().getOperationsForGroup("group-correction").single()
            val stored = requireNotNull(database.progressDao().getEntryForMedia(accountId.value, mediaId.value))
            assertEquals(StoredPendingOperationKind.SET_PROGRESS_CORRECTION, operation.kind)
            assertEquals(8, operation.baseProgress)
            assertEquals(7, operation.targetProgress)
            assertEquals(StoredProgressUnit.EPISODE, stored.progressUnit)
            assertEquals(StoredCollectionStatus.CURRENT, stored.effectiveStatus)
        }

    @Test
    fun exactFinalCorrectionUsesDurableCorrectionAndCompletionGroupAndCanBeUndone() =
        runTest {
            val groupId = OperationGroupId("group-exact-final")
            val result =
                CorrectProgressUseCase(repository, clock)(
                    mediaId = mediaId,
                    targetProgress = 12,
                    actionId = groupId,
                    undoWindow = 6.seconds,
                )
            val committed = result as ProgressWriteResult.Committed
            val operations = database.pendingOperationDao().getOperationsForGroup(groupId.value)
            val activity = requireNotNull(database.progressDao().getActivity(accountId.value, groupId.value))

            assertEquals(12, committed.action.afterProgress.value)
            assertEquals(CollectionStatus.COMPLETED, committed.action.afterStatus)
            assertEquals("correction", activity.direction)
            assertEquals(
                listOf(
                    StoredPendingOperationKind.SET_PROGRESS_CORRECTION,
                    StoredPendingOperationKind.SET_STATUS,
                ),
                operations.map { operation -> operation.kind },
            )
            assertEquals(11, operations.first().baseProgress)
            assertEquals(12, operations.first().targetProgress)

            clock.set(baseline.plus(1.seconds))
            val undone = UndoProgressUseCase(repository, clock)(groupId)
            val restored = requireNotNull(database.progressDao().getEntryForMedia(accountId.value, mediaId.value))
            assertTrue(undone is ProgressWriteResult.Committed)
            assertEquals(11, restored.effectiveProgress)
            assertEquals(StoredCollectionStatus.CURRENT, restored.effectiveStatus)
            assertNotNull(database.progressDao().getActivity(accountId.value, groupId.value)?.undoneAtUtcMs)
        }

    @Test
    fun undoRestoresEffectiveStateTimestampAndRevisionAndCancelsTheWholeGroup() =
        runTest {
            val groupId = OperationGroupId("group-undo")
            val committed =
                StepProgressUseCase(repository, clock)(
                    mediaId,
                    ProgressDirection.INCREASE,
                    groupId,
                    6.seconds,
                ) as ProgressWriteResult.Committed
            clock.set(baseline.plus(1.seconds))

            val undone = UndoProgressUseCase(repository, clock)(groupId)
            val stored = requireNotNull(database.progressDao().getEntryForMedia(accountId.value, mediaId.value))
            val activity = requireNotNull(database.progressDao().getActivity(accountId.value, groupId.value))

            assertTrue(undone is ProgressWriteResult.Committed)
            assertEquals(11, stored.effectiveProgress)
            assertEquals(StoredCollectionStatus.CURRENT, stored.effectiveStatus)
            assertEquals(baseline, stored.effectiveUpdatedAtUtcMs)
            assertEquals(2, stored.localProgressRevision)
            assertNull(database.pendingOperationDao().getGroup(groupId.value))
            assertEquals(committed.action.afterRevision, activity.afterRevision)
            assertNotNull(activity.undoneAtUtcMs)
            assertEquals(groupId.value, database.progressDao().getLatestActivity(accountId.value)?.operationGroupId)
        }

    @Test
    fun recordedActivityIsBoundedLocalAndExcludesUndoAndRemovedEntries() =
        runTest {
            val activityRepository = DefaultProgressActivityRepository(RoomProgressLocalDataSource(database))
            val from = baseline.minus(1.seconds)
            val until = baseline.plus(20.seconds)

            // Collection snapshots update durable progress without creating a local observation.
            database.collectionEntryDao().upsertEntries(listOf(entry(progress = 5)))
            assertEquals(
                emptyList<RecordedProgressActivity>(),
                activityRepository.observeRecordedActivity(accountId, from, until).first(),
            )

            val increaseId = OperationGroupId("activity-increase")
            StepProgressUseCase(repository, clock)(mediaId, ProgressDirection.INCREASE, increaseId, 6.seconds)
            assertEquals(
                listOf(RecordedProgressActivity(baseline, MediaType.ANIME, ProgressDirection.INCREASE)),
                activityRepository.observeRecordedActivity(accountId, from, until).first(),
            )
            assertEquals(
                emptyList<RecordedProgressActivity>(),
                activityRepository
                    .observeRecordedActivity(AccountId("other-account"), from, until)
                    .first(),
            )
            assertEquals(
                emptyList<RecordedProgressActivity>(),
                activityRepository
                    .observeRecordedActivity(accountId, baseline.plus(1.milliseconds), until)
                    .first(),
            )

            clock.set(baseline.plus(1.seconds))
            UndoProgressUseCase(repository, clock)(increaseId)
            assertEquals(
                emptyList<RecordedProgressActivity>(),
                activityRepository.observeRecordedActivity(accountId, from, until).first(),
            )

            clock.set(baseline.plus(2.seconds))
            val correctionId = OperationGroupId("activity-correction")
            CorrectProgressUseCase(repository, clock)(mediaId, 7, correctionId, 6.seconds)
            assertEquals(
                listOf(
                    RecordedProgressActivity(baseline.plus(2.seconds), MediaType.ANIME, ProgressDirection.CORRECTION),
                ),
                activityRepository.observeRecordedActivity(accountId, from, until).first(),
            )

            database.collectionEntryDao().deleteAllForAccount(accountId.value)
            assertEquals(
                emptyList<RecordedProgressActivity>(),
                activityRepository.observeRecordedActivity(accountId, from, until).first(),
            )
        }

    @Test
    fun sameActionIdIsHandledAfterUndoWithoutCreatingAnotherMutation() =
        runTest {
            val groupId = OperationGroupId("group-reopen")
            val first =
                StepProgressUseCase(repository, clock)(
                    mediaId,
                    ProgressDirection.INCREASE,
                    groupId,
                    6.seconds,
                )
            clock.set(baseline.plus(1.seconds))
            UndoProgressUseCase(repository, clock)(groupId)
            val replay =
                StepProgressUseCase(repository, clock)(
                    mediaId,
                    ProgressDirection.INCREASE,
                    groupId,
                    6.seconds,
                )

            assertTrue(first is ProgressWriteResult.Committed)
            assertTrue(replay is ProgressWriteResult.AlreadyHandled)
            assertEquals(11, database.progressDao().getEntryForMedia(accountId.value, mediaId.value)?.effectiveProgress)
            assertEquals(1, database.openHelper.readableDatabase.count("local_progress_activity"))
            assertEquals(0, database.openHelper.readableDatabase.count("pending_operation_group"))
        }

    @Test
    fun sameActionIdCannotBeReusedForAnotherEntryOrAccount() =
        runTest {
            val actionId = OperationGroupId("group-ownership")
            StepProgressUseCase(repository, clock)(mediaId, ProgressDirection.INCREASE, actionId, 6.seconds)

            val otherMediaId = MediaId("media-other-entry")
            database.mediaDao().upsertMedia(listOf(media(otherMediaId.value)))
            database.collectionEntryDao().upsertEntries(
                listOf(
                    entry(
                        collectionEntryId = "entry-other-entry",
                        mediaId = otherMediaId,
                        progress = 2,
                    ),
                ),
            )
            val otherEntryResult =
                StepProgressUseCase(repository, clock)(
                    otherMediaId,
                    ProgressDirection.INCREASE,
                    actionId,
                    6.seconds,
                )
            assertEquals(
                ProgressWriteResult.Rejection.ACTION_ID_REUSED,
                (otherEntryResult as ProgressWriteResult.Rejected).reason,
            )

            insertOtherAccount()
            database.collectionEntryDao().upsertEntries(
                listOf(
                    entry(
                        collectionEntryId = "entry-other-account",
                        accountId = OTHER_ACCOUNT_ID,
                        progress = 4,
                    ),
                ),
            )
            val otherAccountResult =
                StepProgressUseCase(createRepository(account = OTHER_ACCOUNT_ID), clock)(
                    mediaId,
                    ProgressDirection.INCREASE,
                    actionId,
                    6.seconds,
                )
            assertEquals(
                ProgressWriteResult.Rejection.ACTION_ID_REUSED,
                (otherAccountResult as ProgressWriteResult.Rejected).reason,
            )
            assertEquals(
                4,
                database.progressDao().getEntryForMedia(OTHER_ACCOUNT_ID.value, mediaId.value)?.effectiveProgress,
            )
            assertEquals(
                actionId.value,
                database.progressDao().getActivity(accountId.value, actionId.value)?.operationGroupId,
            )
        }

    @Test
    fun enqueueFailureRollsBackEffectiveWriteIntentAndActivity() =
        runTest {
            val delegate =
                DefaultPendingOperationWriter(
                    accountId = accountId,
                    localDataSource = RoomPendingOperationLocalDataSource(database),
                )
            val failingRepository = createRepository(writer = ThrowAfterEnqueueWriter(delegate))

            val failure =
                expectFailure {
                    StepProgressUseCase(failingRepository, clock)(
                        mediaId,
                        ProgressDirection.INCREASE,
                        OperationGroupId("group-enqueue-failure"),
                        6.seconds,
                    )
                }
            assertTrue(failure is IllegalStateException)
            assertEquals(11, database.progressDao().getEntryForMedia(accountId.value, mediaId.value)?.effectiveProgress)
            assertEquals(0, database.openHelper.readableDatabase.count("pending_operation_group"))
            assertEquals(0, database.openHelper.readableDatabase.count("local_progress_activity"))
        }

    @Test
    fun cancellationRollsBackEffectiveStateAndEnqueuedRows() =
        runTest {
            val enqueued = kotlinx.coroutines.CompletableDeferred<Unit>()
            val delegate = DefaultPendingOperationWriter(accountId, RoomPendingOperationLocalDataSource(database))
            val writer =
                object : PendingOperationWriter {
                    override suspend fun enqueue(group: io.morgan.mio.domain.sync.PendingOperationGroup) {
                        delegate.enqueue(group)
                        enqueued.complete(Unit)
                        kotlinx.coroutines.awaitCancellation()
                    }

                    override suspend fun cancelUndelivered(groupId: OperationGroupId) =
                        delegate.cancelUndelivered(groupId)

                    override suspend fun undo(
                        groupId: OperationGroupId,
                        compensation: io.morgan.mio.domain.sync.PendingOperationGroup,
                    ): Boolean = delegate.undo(groupId, compensation)
                }
            val cancellableRepository = createRepository(writer = writer)
            val job =
                launch {
                    StepProgressUseCase(
                        cancellableRepository,
                        clock,
                    )(mediaId, ProgressDirection.INCREASE, OperationGroupId("cancelled-transaction"), 6.seconds)
                }
            enqueued.await()
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(11, database.progressDao().getEntryForMedia(accountId.value, mediaId.value)?.effectiveProgress)
            assertEquals(0, database.openHelper.readableDatabase.count("pending_operation_group"))
            assertEquals(0, database.openHelper.readableDatabase.count("pending_operation"))
            assertEquals(0, database.openHelper.readableDatabase.count("local_progress_activity"))
        }

    @Test
    fun activityInsertFailureRollsBackTheEarlierWrites() =
        runTest {
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER progress_activity_failure " +
                    "BEFORE INSERT ON local_progress_activity BEGIN " +
                    "SELECT RAISE(ABORT, 'activity failure'); END",
            )
            try {
                val failure =
                    expectFailure {
                        StepProgressUseCase(repository, clock)(
                            mediaId,
                            ProgressDirection.INCREASE,
                            OperationGroupId("group-activity-failure"),
                            6.seconds,
                        )
                    }
                assertTrue(failure is SQLiteException)
                assertEquals(
                    11,
                    database.progressDao().getEntryForMedia(accountId.value, mediaId.value)?.effectiveProgress,
                )
                assertEquals(0, database.openHelper.readableDatabase.count("pending_operation_group"))
                assertEquals(0, database.openHelper.readableDatabase.count("local_progress_activity"))
            } finally {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER progress_activity_failure")
            }
        }

    @Test
    fun undoFailureAfterCancellationRestoresThePendingGroupAndEffectiveState() =
        runTest {
            val actionId = OperationGroupId("group-undo-rollback")
            StepProgressUseCase(repository, clock)(mediaId, ProgressDirection.INCREASE, actionId, 6.seconds)
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER progress_undo_failure " +
                    "BEFORE UPDATE OF local_progress_revision ON collection_entry " +
                    "WHEN OLD.effective_progress = 12 BEGIN " +
                    "SELECT RAISE(ABORT, 'undo failure'); END",
            )
            try {
                val failure = expectFailure { UndoProgressUseCase(repository, clock)(actionId) }
                assertTrue(failure is SQLiteException)
                assertEquals(
                    12,
                    database.progressDao().getEntryForMedia(accountId.value, mediaId.value)?.effectiveProgress,
                )
                assertNotNull(database.pendingOperationDao().getGroup(actionId.value))
                assertNull(database.progressDao().getActivity(accountId.value, actionId.value)?.undoneAtUtcMs)
            } finally {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER progress_undo_failure")
            }
        }

    @Test
    fun twoRepositoryInstancesRacingOnOneEntryDoNotLoseAUnit() =
        runTest {
            database.collectionEntryDao().upsertEntries(
                listOf(
                    entry(progress = 0).copy(
                        remoteProgress = 0,
                        remoteTotal = null,
                        effectiveTotal = null,
                    ),
                ),
            )
            val firstRepository = createRepository()
            val secondRepository = createRepository()

            val results =
                listOf(firstRepository, secondRepository)
                    .mapIndexed { index, instance ->
                        async(Dispatchers.Default) {
                            StepProgressUseCase(instance, clock)(
                                mediaId = mediaId,
                                direction = ProgressDirection.INCREASE,
                                actionId = OperationGroupId("group-race-$index"),
                                undoWindow = 6.seconds,
                            )
                        }
                    }.awaitAll()

            assertTrue(results.all { result -> result is ProgressWriteResult.Committed })
            val stored = requireNotNull(database.progressDao().getEntryForMedia(accountId.value, mediaId.value))
            assertEquals(2, stored.effectiveProgress)
            assertEquals(2L, stored.localProgressRevision)
            assertEquals(2, database.openHelper.readableDatabase.count("pending_operation_group"))
            assertEquals(2, database.openHelper.readableDatabase.count("local_progress_activity"))
        }

    @Test
    fun sameActionIdRemainsHandledAfterReopeningAFileBackedDatabase() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val databaseName = "mio-progress-reopen-${System.nanoTime()}.db"
            val fileDatabase = MioDatabaseFactory.create(context, databaseName)
            try {
                fileDatabase.selectedAccountDao().replaceSelectedAccount(account(), mapping())
                fileDatabase.mediaDao().upsertMedia(listOf(media()))
                fileDatabase.collectionEntryDao().upsertEntries(listOf(entry()))
                val firstRepository = createRepository(database = fileDatabase)
                val first =
                    StepProgressUseCase(firstRepository, clock)(
                        mediaId,
                        ProgressDirection.INCREASE,
                        OperationGroupId("group-file-reopen"),
                        6.seconds,
                    )
                assertTrue(first is ProgressWriteResult.Committed)
                fileDatabase.close()

                val reopened = MioDatabaseFactory.create(context, databaseName)
                try {
                    val replay =
                        StepProgressUseCase(createRepository(database = reopened), clock)(
                            mediaId,
                            ProgressDirection.INCREASE,
                            OperationGroupId("group-file-reopen"),
                            6.seconds,
                        )
                    assertTrue(replay is ProgressWriteResult.AlreadyHandled)
                    assertEquals(1, reopened.openHelper.readableDatabase.count("local_progress_activity"))
                    assertEquals(1, reopened.openHelper.readableDatabase.count("pending_operation_group"))
                } finally {
                    reopened.close()
                }
            } finally {
                if (fileDatabase.isOpen) fileDatabase.close()
                context.deleteDatabase(databaseName)
            }
        }

    private fun createRepository(
        database: MioDatabase = this.database,
        writer: PendingOperationWriter? = null,
        account: AccountId = accountId,
    ): DefaultProgressRepository {
        val localDataSource = RoomProgressLocalDataSource(database)
        val resolvedWriter =
            writer ?: DefaultPendingOperationWriter(
                accountId = account,
                localDataSource = RoomPendingOperationLocalDataSource(database),
            )
        return DefaultProgressRepository(account, database, localDataSource, resolvedWriter, clock)
    }

    private fun entry(
        collectionEntryId: String = "entry-progress",
        accountId: AccountId = this.accountId,
        mediaId: MediaId = this.mediaId,
        progress: Int = 11,
    ): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = collectionEntryId,
            accountId = accountId.value,
            mediaId = mediaId.value,
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 11,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = baseline,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = progress,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = baseline,
        )

    private fun account() =
        SelectedAccountEntity(
            selectionKey = "selected",
            accountId = accountId.value,
            connectedProvider = StoredConnectedProvider.ANILIST,
            displayName = "Progress account",
            avatarReference = null,
        )

    private fun mapping() =
        AccountProviderIdEntity(
            accountId = accountId.value,
            provider = StoredConnectedProvider.ANILIST,
            remoteAccountId = "remote-progress",
        )

    private fun media() = media(mediaId.value)

    private fun media(id: String) =
        MediaEntity(
            mediaId = id,
            mediaType = StoredMediaType.ANIME,
            title = "Progress title",
            format = StoredMediaFormat.TV,
            publicationStatus = StoredMediaPublicationStatus.RELEASING,
            synopsis = null,
            posterReference = null,
            bannerReference = null,
            episodeTotal = 12,
            chapterTotal = null,
            volumeTotal = null,
        )

    private suspend fun insertOtherAccount() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO selected_account " +
                "(selection_key, account_id, connected_provider, display_name, avatar_reference) " +
                "VALUES ('other-selection', '${OTHER_ACCOUNT_ID.value}', 'anilist', 'Other account', NULL)",
        )
    }

    private suspend fun expectFailure(block: suspend () -> Unit): Throwable {
        var failure: Throwable? = null
        try {
            block()
        } catch (throwable: Throwable) {
            failure = throwable
        }
        return failure ?: error("Expected operation to fail")
    }

    private class ThrowAfterEnqueueWriter(
        private val delegate: PendingOperationWriter,
    ) : PendingOperationWriter {
        override suspend fun enqueue(group: io.morgan.mio.domain.sync.PendingOperationGroup) {
            delegate.enqueue(group)
            error("test enqueue failure")
        }

        override suspend fun cancelUndelivered(groupId: OperationGroupId): Boolean = delegate.cancelUndelivered(groupId)

        override suspend fun undo(
            groupId: OperationGroupId,
            compensation: io.morgan.mio.domain.sync.PendingOperationGroup,
        ): Boolean = delegate.undo(groupId, compensation)
    }

    private companion object {
        val OTHER_ACCOUNT_ID = AccountId("account-other")
    }
}

private class RepositoryClock(
    private var value: Instant,
) : Clock {
    override fun now(): Instant = value

    fun set(instant: Instant) {
        value = instant
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
    query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
