package io.morgan.mio.feature.today.impl

import io.morgan.mio.domain.collection.CollectionAction
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.collection.CollectionStatusChange
import io.morgan.mio.domain.collection.CollectionWriteResult
import io.morgan.mio.domain.media.Artwork
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.media.MediaTotals
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.progress.ObserveProgressUndoUseCase
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressChange
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressRepository
import io.morgan.mio.domain.progress.ProgressSnapshot
import io.morgan.mio.domain.progress.ProgressUndo
import io.morgan.mio.domain.progress.ProgressWriteResult
import io.morgan.mio.domain.progress.StepProgressUseCase
import io.morgan.mio.domain.progress.UndoProgressUseCase
import io.morgan.mio.domain.queue.ObserveTodayQueueUseCase
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncRecoveryRepository
import io.morgan.mio.domain.sync.SyncRecoveryState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val accountId = AccountId("account-1")
    private val now = Instant.parse("2026-08-23T12:00:00Z")

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsLoadingAndReachesNeutralEmptyStateFromRoom() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repositories = TestRepositories()
            val viewModel = repositories.viewModel()

            assertEquals(TodayUiState.Loading(), viewModel.uiState.value)
            val observer = backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            assertTrue(viewModel.uiState.value is TodayUiState.CaughtUp)
            observer.cancel()
        }

    @Test
    fun roomReplacementUpdatesContentWithoutARefreshOrProviderState() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repositories = TestRepositories()
            repositories.entries.value = listOf(entry(progress = 1))
            repositories.mediaState.value = media()
            repositories.releaseState.value = listOf(release(2))
            val viewModel = repositories.viewModel()
            val observer = backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            val first = viewModel.uiState.value as TodayUiState.Populated
            assertEquals(2, first.now.nextUnit)
            assertTrue(repositories.mediaRefreshCalls.isEmpty())
            assertTrue(repositories.collectionRefreshCalls.isEmpty())

            repositories.entries.value = listOf(entry(progress = 2))
            repositories.releaseState.value = listOf(release(3))
            runCurrent()

            val replacement = viewModel.uiState.value as TodayUiState.Populated
            assertEquals(3, replacement.now.nextUnit)
            observer.cancel()
        }

    @Test
    fun stoppingTheOnlyCollectorCancelsTheRoomObservation() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repositories = TestRepositories()
            val viewModel = repositories.viewModel()
            val observer = backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            observer.cancel()

            advanceTimeBy(5_001)
            runCurrent()

            assertTrue(repositories.collectionCancellations.get() > 0)
        }

    @Test
    fun touchKeepsIdentityButUsesFreshNextUnitAndReleasesCaughtUpState() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repositories = TestRepositories()
            repositories.entries.value = listOf(entry(1))
            repositories.mediaState.value = media()
            repositories.releaseState.value = listOf(release(2), release(3))
            val viewModel = repositories.viewModel()
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            viewModel.onAction(TodayAction.InteractionChanged("media-1", true))
            runCurrent()
            repositories.entries.value = listOf(entry(2))
            runCurrent()
            val updated = viewModel.uiState.value as TodayUiState.Populated
            assertEquals(2, updated.now.progress)
            assertEquals(3, updated.now.nextUnit)
            repositories.entries.value = listOf(entry(3))
            runCurrent()
            val pinned = viewModel.uiState.value as TodayUiState.Populated
            assertEquals(3, pinned.now.progress)
            assertEquals(null, pinned.now.nextUnit)
            viewModel.onAction(TodayAction.InteractionChanged("media-1", false))
            runCurrent()
            assertTrue(viewModel.uiState.value is TodayUiState.CaughtUp)
        }

    @Test
    fun leavingDestinationAndAccountRemovalReleaseTouchedContent() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repositories = TestRepositories()
            repositories.entries.value = listOf(entry(1))
            repositories.mediaState.value = media()
            repositories.releaseState.value = listOf(release(2))
            val viewModel = repositories.viewModel()
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            viewModel.onAction(TodayAction.InteractionChanged("media-1", true))
            repositories.entries.value = listOf(entry(2))
            runCurrent()
            assertTrue(viewModel.uiState.value is TodayUiState.Populated)
            viewModel.onAction(TodayAction.VisibilityChanged(false))
            runCurrent()
            assertTrue(viewModel.uiState.value is TodayUiState.CaughtUp)
            viewModel.onAction(TodayAction.VisibilityChanged(true))
            runCurrent()
            assertTrue(viewModel.uiState.value is TodayUiState.CaughtUp)
            repositories.entries.value = listOf(entry(1))
            runCurrent()
            viewModel.onAction(TodayAction.InteractionChanged("media-1", true))
            runCurrent()
            repositories.entries.value = emptyList()
            runCurrent()
            assertTrue(viewModel.uiState.value is TodayUiState.CaughtUp)
        }

    @Test
    fun retryTargetsTheFailedGestureAfterANewerGestureAndDoesNotReplayHaptics() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val failure = CompletableDeferred<Unit>()
            val requests = mutableListOf<ProgressChange>()
            val handled = mutableMapOf<OperationGroupId, ProgressAction>()
            val progress =
                object : ProgressRepository {
                    override suspend fun readSnapshot(mediaId: MediaId) = ProgressSnapshot(entry(1), 0)

                    override suspend fun findAction(actionId: OperationGroupId) = handled[actionId]

                    override fun observeLatestAction() = flowOf<ProgressAction?>(null)

                    override suspend fun readLatestAction(): ProgressAction? = null

                    override suspend fun undo(change: ProgressUndo) = ProgressWriteResult.Stale

                    override suspend fun commit(change: ProgressChange): ProgressWriteResult {
                        requests += change
                        if (requests.size == 1) {
                            failure.await()
                            error("First gesture failed after the second gesture")
                        }
                        val action =
                            ProgressAction(
                                id = change.actionId,
                                order = requests.size.toLong(),
                                accountId = accountId,
                                entryId = change.before.entry.id,
                                mediaId = change.before.entry.mediaId,
                                direction = change.direction,
                                unit = change.before.entry.progressUnit,
                                beforeProgress = change.before.entry.progress,
                                afterProgress = change.targetProgress,
                                beforeStatus = change.before.entry.status,
                                afterStatus = change.targetStatus,
                                beforeUpdatedAt = now,
                                afterUpdatedAt = now,
                                afterRevision = 1,
                                createdAt = now,
                                undoUntil = now + 6.seconds,
                                undoneAt = null,
                            )
                        handled[change.actionId] = action
                        return ProgressWriteResult.Committed(action)
                    }
                }
            val viewModel = TestRepositories().viewModel(progress)
            var haptics = 0
            backgroundScope.launch { viewModel.routineHaptics.collect { haptics++ } }
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            val step = TodayAction.Step("media-1", TodayProgressDirection.INCREASE, 6_000)
            viewModel.onAction(step)
            runCurrent()
            viewModel.onAction(step)
            runCurrent()
            assertEquals(1, haptics)
            failure.complete(Unit)
            runCurrent()
            viewModel.onAction(TodayAction.RetryProgress)
            runCurrent()
            assertEquals(3, requests.size)
            assertEquals(requests[0].actionId, requests[2].actionId)
            assertTrue(requests[0].actionId != requests[1].actionId)
            assertEquals(2, haptics)
            viewModel.onAction(TodayAction.RetryProgress)
            runCurrent()
            assertEquals(3, requests.size)
            assertEquals(2, haptics)
        }

    @Test
    fun unresolvedWriteAndMotionEachProtectIdentityUntilTheirOwnCompletion() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repositories = TestRepositories()
            repositories.entries.value = listOf(entry(1))
            repositories.mediaState.value = media()
            repositories.releaseState.value = listOf(release(2))
            val gate = CompletableDeferred<Unit>()
            val progress =
                object : ProgressRepository {
                    override suspend fun readSnapshot(mediaId: MediaId) = ProgressSnapshot(entry(1), 0)

                    override suspend fun findAction(actionId: OperationGroupId): ProgressAction? = null

                    override fun observeLatestAction() = flowOf<ProgressAction?>(null)

                    override suspend fun readLatestAction(): ProgressAction? = null

                    override suspend fun undo(change: ProgressUndo) = ProgressWriteResult.Stale

                    override suspend fun commit(change: ProgressChange): ProgressWriteResult {
                        repositories.entries.value = listOf(entry(2))
                        gate.await()
                        val action =
                            ProgressAction(
                                id = change.actionId,
                                order = 1,
                                accountId = accountId,
                                entryId = change.before.entry.id,
                                mediaId = change.before.entry.mediaId,
                                direction = change.direction,
                                unit = change.before.entry.progressUnit,
                                beforeProgress = change.before.entry.progress,
                                afterProgress = change.targetProgress,
                                beforeStatus = change.before.entry.status,
                                afterStatus = change.targetStatus,
                                beforeUpdatedAt = now,
                                afterUpdatedAt = now,
                                afterRevision = 1,
                                createdAt = now,
                                undoUntil = now + 6.seconds,
                                undoneAt = null,
                            )
                        return ProgressWriteResult.Committed(action)
                    }
                }
            val viewModel = repositories.viewModel(progress)
            var haptics = 0
            backgroundScope.launch { viewModel.uiState.collect() }
            backgroundScope.launch { viewModel.routineHaptics.collect { haptics++ } }
            runCurrent()
            assertEquals(0, haptics)
            viewModel.onAction(TodayAction.MotionChanged("media-1", generation = 1L, active = true))
            viewModel.onAction(TodayAction.Step("media-1", TodayProgressDirection.INCREASE, 6_000))
            runCurrent()
            viewModel.onAction(TodayAction.InteractionChanged("media-1", false))
            runCurrent()
            assertEquals(2, (viewModel.uiState.value as TodayUiState.Populated).now.progress)
            assertEquals(0, haptics)
            gate.complete(Unit)
            runCurrent()
            assertEquals(1, haptics)
            advanceTimeBy(219)
            runCurrent()
            assertTrue(viewModel.uiState.value is TodayUiState.Populated)
            viewModel.onAction(TodayAction.MotionChanged("media-1", generation = 1L, active = false))
            runCurrent()
            assertTrue(viewModel.uiState.value is TodayUiState.CaughtUp)
        }

    @Test
    fun completionHoldsCardUntilStampFinishesThenReceiptKeepsUndo() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repositories = TestRepositories()
            repositories.entries.value = listOf(entry(11))
            repositories.mediaState.value = media()
            repositories.releaseState.value = listOf(release(12))
            val latest = MutableStateFlow<ProgressAction?>(null)
            val progress =
                object : ProgressRepository by TestProgressRepository() {
                    override fun observeLatestAction() = latest
                }
            val viewModel = repositories.viewModel(progress)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            viewModel.onAction(TodayAction.InteractionChanged("media-1", true))
            latest.value =
                ProgressAction(
                    id = OperationGroupId("completion"),
                    order = 1,
                    direction = ProgressDirection.INCREASE,
                    accountId = accountId,
                    entryId = CollectionEntryId("entry-1"),
                    mediaId = MediaId("media-1"),
                    unit = ProgressUnit.EPISODE,
                    beforeProgress = ProgressValue(11),
                    afterProgress = ProgressValue(12),
                    beforeStatus = CollectionStatus.CURRENT,
                    afterStatus = CollectionStatus.COMPLETED,
                    beforeUpdatedAt = now,
                    afterUpdatedAt = now,
                    afterRevision = 1,
                    createdAt = now,
                    undoUntil = now + 6.seconds,
                    undoneAt = null,
                )
            repositories.entries.value = listOf(entry(12).copy(status = CollectionStatus.COMPLETED))
            runCurrent()
            viewModel.onAction(TodayAction.InteractionChanged("media-1", false))
            runCurrent()
            assertEquals(12, (viewModel.uiState.value as TodayUiState.Populated).now.progress)
            viewModel.onAction(TodayAction.CompletionMotionFinished("obsolete"))
            runCurrent()
            assertTrue(viewModel.uiState.value is TodayUiState.Populated)
            viewModel.onAction(TodayAction.CompletionMotionFinished("completion"))
            runCurrent()
            assertEquals("completion", (viewModel.uiState.value as TodayUiState.CaughtUp).latestUndo?.groupId)
            // Room's restored state reverses the receipt without replaying the completion.
            latest.value = null
            repositories.entries.value = listOf(entry(11))
            runCurrent()
            assertEquals(11, (viewModel.uiState.value as TodayUiState.Populated).now.progress)
        }

    private fun TestRepositories.viewModel(progress: ProgressRepository = TestProgressRepository()): TodayViewModel =
        TodayViewModel(
            accountId = accountId,
            observeTodayQueue = ObserveTodayQueueUseCase(this, this, fixedClock()),
            collectionRepository = this,
            mediaRepository = this,
            stepProgress = StepProgressUseCase(progress, fixedClock()),
            undoProgress = UndoProgressUseCase(progress, fixedClock()),
            observeUndoUseCase = ObserveProgressUndoUseCase(progress, fixedClock()),
            syncRecoveryRepository = EmptySyncRecoveryRepository(),
            retrySyncIssue =
                io.morgan.mio.domain.sync
                    .RetrySyncIssueUseCase(EmptySyncRecoveryRepository()),
        )

    private fun fixedClock(): Clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private fun entry(progress: Int): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId("entry-1"),
            accountId = accountId,
            mediaId = MediaId("media-1"),
            status = CollectionStatus.CURRENT,
            progress = ProgressValue(progress),
            progressUnit = ProgressUnit.EPISODE,
            total = UnitTotal(12),
            updatedAt = now,
        )

    private fun media(): Media =
        Media(
            id = MediaId("media-1"),
            type = MediaType.ANIME,
            title = MediaTitle("Title"),
            format = MediaFormat.TV,
            publicationStatus = MediaPublicationStatus.RELEASING,
            synopsis = null,
            artwork = Artwork(null, null),
            totals = MediaTotals(UnitTotal(12), null, null),
            genres = emptyList(),
            relatedMediaIds = emptyList(),
        )

    private fun release(number: Int): Release =
        Release(
            mediaId = MediaId("media-1"),
            unit = ProgressUnit.EPISODE,
            number = UnitNumber(number),
            time = ReleaseTime.Trusted(now),
        )
}

private class EmptySyncRecoveryRepository : SyncRecoveryRepository {
    override fun observeState() = flowOf(SyncRecoveryState(accountIssue = null, entryIssues = emptyList()))

    override suspend fun readIssue(operationId: OperationId): SyncIssue? = null

    override suspend fun rearm(issue: SyncIssue): Boolean = false

    override suspend fun resolveConflict(
        issue: SyncIssue,
        resolution: PendingOperationGroup,
    ): Boolean = false

    override suspend fun recoverAuthentication(
        accountId: AccountId,
        issueIds: Set<String>,
    ): Boolean = false
}

private class TestProgressRepository : ProgressRepository {
    override suspend fun readSnapshot(mediaId: MediaId): ProgressSnapshot? = null

    override suspend fun findAction(actionId: OperationGroupId): ProgressAction? = null

    override fun observeLatestAction() = flowOf<ProgressAction?>(null)

    override suspend fun readLatestAction(): ProgressAction? = null

    override suspend fun commit(change: ProgressChange): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.MISSING_ENTRY)

    override suspend fun undo(change: ProgressUndo): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)
}

private class TestRepositories :
    CollectionRepository,
    MediaRepository {
    val entries = MutableStateFlow<List<CollectionEntry>>(emptyList())
    val mediaState = MutableStateFlow<Media?>(null)
    val releaseState = MutableStateFlow<List<Release>>(emptyList())
    val collectionCancellations = AtomicInteger()
    val mediaRefreshCalls = mutableListOf<MediaId>()
    val collectionRefreshCalls = mutableListOf<AccountId>()

    override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        flow {
            try {
                emitAll(entries.map { values -> values.filter { entry -> entry.accountId == accountId } })
                awaitCancellation()
            } finally {
                collectionCancellations.incrementAndGet()
            }
        }

    override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        entries.map { values -> values.firstOrNull { entry -> entry.id == entryId } }

    override fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> =
        entries.map { values ->
            values.firstOrNull { entry -> entry.accountId == accountId && entry.mediaId == mediaId }
        }

    override suspend fun readEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): CollectionEntry? =
        entries.value.firstOrNull { entry ->
            entry.accountId == accountId && entry.mediaId == mediaId
        }

    override fun observeWritableStatuses(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<List<CollectionStatus>> = flowOf(CollectionStatus.entries.toList())

    override suspend fun findAction(actionId: OperationGroupId): CollectionAction? = null

    override fun observeLatestAction(): Flow<CollectionAction?> = flowOf(null)

    override suspend fun readLatestAction(): CollectionAction? = null

    override suspend fun commitStatus(change: CollectionStatusChange): CollectionWriteResult =
        CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.MISSING_ENTRY)

    override suspend fun undo(actionId: OperationGroupId): CollectionWriteResult =
        CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNDO_UNAVAILABLE)

    override fun observeMedia(mediaId: MediaId): Flow<Media?> = mediaState

    override fun observeRelatedMedia(mediaId: MediaId): Flow<List<Media>> = flow { emit(emptyList()) }

    override fun observeReleases(mediaId: MediaId): Flow<List<Release>> = releaseState

    override suspend fun refreshMedia(mediaId: MediaId) {
        mediaRefreshCalls += mediaId
    }

    override suspend fun refreshCollection(accountId: AccountId) {
        collectionRefreshCalls += accountId
    }
}
