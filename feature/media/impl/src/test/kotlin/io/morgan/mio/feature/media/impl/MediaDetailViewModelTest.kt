package io.morgan.mio.feature.media.impl

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.progress.ObserveProgressUndoUseCase
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressChange
import io.morgan.mio.domain.progress.ProgressRepository
import io.morgan.mio.domain.progress.ProgressSnapshot
import io.morgan.mio.domain.progress.ProgressUndo
import io.morgan.mio.domain.progress.ProgressWriteResult
import io.morgan.mio.domain.progress.StepProgressUseCase
import io.morgan.mio.domain.progress.UndoProgressUseCase
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.RetrySyncIssueUseCase
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncRecoveryRepository
import io.morgan.mio.domain.sync.SyncRecoveryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class MediaDetailViewModelTest {
    private val mediaId = MediaId("media-1")
    private val accountId = AccountId("account-1")
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cachedRoomMediaWinsWhileOneRefreshIsStillRunningAndRelatedUpdatesStayLive() =
        runTest {
            val repository = TestMediaRepository(media = sampleMedia())
            val refreshGate = kotlinx.coroutines.CompletableDeferred<Unit>()
            repository.refreshBlock = { refreshGate.await() }
            val viewModel = createViewModel(repository)
            val subscription = backgroundScope.launch { viewModel.uiState.collect {} }

            runCurrent()
            assertTrue(viewModel.uiState.value is MediaDetailUiState.Content)
            assertEquals(listOf(mediaId), repository.refreshCalls)

            repository.related.value = listOf(sampleMedia("related-1"))
            runCurrent()
            val content = viewModel.uiState.value as MediaDetailUiState.Content
            assertEquals(listOf("related-1"), content.relatedTitles.map { title -> title.id })

            refreshGate.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is MediaDetailUiState.Content)
            subscription.cancel()
        }

    @Test
    fun successfulRefreshWithoutRoomEmissionRemainsLoadingAndDoesNotFlashError() =
        runTest {
            val repository = TestMediaRepository()
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }

            advanceUntilIdle()

            assertEquals(MediaDetailUiState.Loading(), viewModel.uiState.value)
            assertEquals(listOf(mediaId), repository.refreshCalls)
        }

    @Test
    fun retryIsTheOnlyRecoveryActionAndRendersContentAfterRoomEmits() =
        runTest {
            val repository = TestMediaRepository()
            var attempts = 0
            repository.refreshBlock = {
                attempts += 1
                if (attempts == 1) throw IllegalStateException("operational fixture")
            }
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(MediaDetailUiState.Error(), viewModel.uiState.value)

            viewModel.onAction(MediaDetailAction.Retry)
            repository.media.value = sampleMedia()
            advanceUntilIdle()

            assertEquals(2, attempts)
            assertTrue(viewModel.uiState.value is MediaDetailUiState.Content)
        }

    @Test
    fun cancellationDoesNotBecomeAnErrorState() =
        runTest {
            val repository = TestMediaRepository()
            repository.refreshBlock = { throw CancellationException("cancelled fixture") }
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }

            advanceUntilIdle()

            assertEquals(MediaDetailUiState.Loading(), viewModel.uiState.value)
            assertEquals(listOf(mediaId), repository.refreshCalls)
        }

    @Test
    fun retryTargetsTheFailedGestureAfterANewerGestureAndDoesNotReplayHaptics() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val now = fixedClock().now()
            val failure = kotlinx.coroutines.CompletableDeferred<Unit>()
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
            val viewModel = createViewModel(TestMediaRepository(media = sampleMedia()), progress)
            var haptics = 0
            backgroundScope.launch { viewModel.routineHaptics.collect { haptics++ } }
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            val step = MediaDetailAction.Step("media-1", MediaDetailProgressDirection.INCREASE, 6_000)
            viewModel.onAction(step)
            runCurrent()
            viewModel.onAction(step)
            runCurrent()
            assertEquals(1, haptics)
            failure.complete(Unit)
            runCurrent()
            viewModel.onAction(MediaDetailAction.RetryProgress)
            runCurrent()
            assertEquals(3, requests.size)
            assertEquals(requests[0].actionId, requests[2].actionId)
            assertTrue(requests[0].actionId != requests[1].actionId)
            assertEquals(2, haptics)
            viewModel.onAction(MediaDetailAction.RetryProgress)
            runCurrent()
            assertEquals(3, requests.size)
            assertEquals(2, haptics)
        }

    private fun createViewModel(
        repository: TestMediaRepository,
        progress: ProgressRepository = TestProgressRepository(),
    ): MediaDetailViewModel =
        MediaDetailViewModel(
            mediaId = mediaId,
            accountId = accountId,
            mediaRepository = repository,
            collectionRepository = TestCollectionRepository(),
            syncRecoveryRepository = EmptySyncRecoveryRepository(),
            retrySyncIssue = RetrySyncIssueUseCase(EmptySyncRecoveryRepository()),
            stepProgress = StepProgressUseCase(progress, fixedClock()),
            undoProgress = UndoProgressUseCase(progress, fixedClock()),
            observeUndoUseCase = ObserveProgressUndoUseCase(progress, fixedClock()),
        )

    private fun entry(progress: Int) =
        CollectionEntry(
            id =
                io.morgan.mio.domain.model
                    .CollectionEntryId("entry-1"),
            accountId = accountId,
            mediaId = mediaId,
            status = io.morgan.mio.domain.collection.CollectionStatus.CURRENT,
            progress =
                io.morgan.mio.domain.model
                    .ProgressValue(progress),
            progressUnit = io.morgan.mio.domain.model.ProgressUnit.EPISODE,
            total =
                io.morgan.mio.domain.model
                    .UnitTotal(12),
            updatedAt = fixedClock().now(),
        )

    private fun fixedClock(): kotlin.time.Clock =
        object : kotlin.time.Clock {
            override fun now(): kotlin.time.Instant = kotlin.time.Instant.parse("2026-08-23T12:00:00Z")
        }

    private fun sampleMedia(id: String = mediaId.value): Media =
        Media(
            id = MediaId(id),
            type = io.morgan.mio.domain.model.MediaType.ANIME,
            title =
                io.morgan.mio.domain.model
                    .MediaTitle("Title $id"),
            format = io.morgan.mio.domain.media.MediaFormat.TV,
            publicationStatus = io.morgan.mio.domain.media.MediaPublicationStatus.RELEASING,
            synopsis = "Synopsis",
            artwork =
                io.morgan.mio.domain.media
                    .Artwork(null, null),
            totals =
                io.morgan.mio.domain.media
                    .MediaTotals(null, null, null),
            genres = emptyList(),
            relatedMediaIds = emptyList(),
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

private class TestMediaRepository(
    media: Media? = null,
) : MediaRepository {
    val media = MutableStateFlow(media)
    val related = MutableStateFlow<List<Media>>(emptyList())
    val refreshCalls = mutableListOf<MediaId>()
    var refreshBlock: suspend () -> Unit = {}

    override fun observeMedia(mediaId: MediaId): Flow<Media?> = media

    override fun observeRelatedMedia(mediaId: MediaId): Flow<List<Media>> = related

    override fun observeReleases(mediaId: MediaId): Flow<List<Release>> = MutableStateFlow(emptyList())

    override suspend fun refreshMedia(mediaId: MediaId) {
        refreshCalls += mediaId
        refreshBlock()
    }
}

private class TestCollectionRepository : CollectionRepository {
    private val entry = MutableStateFlow<CollectionEntry?>(null)

    override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        entry.map { value -> listOfNotNull(value) }.distinctUntilChanged()

    override fun observeEntry(entryId: io.morgan.mio.domain.model.CollectionEntryId): Flow<CollectionEntry?> = entry

    override fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> = entry

    override suspend fun refreshCollection(accountId: AccountId): Unit = Unit
}
