package io.morgan.mio.feature.today.impl

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
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
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.queue.ObserveTodayQueueUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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

            assertEquals(TodayUiState.Loading, viewModel.uiState.value)
            val observer = backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            assertEquals(TodayUiState.Empty, viewModel.uiState.value)
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

            val first = viewModel.uiState.value as TodayUiState.Content
            assertEquals(2, first.now.nextUnit)
            assertTrue(repositories.mediaRefreshCalls.isEmpty())
            assertTrue(repositories.collectionRefreshCalls.isEmpty())

            repositories.entries.value = listOf(entry(progress = 2))
            repositories.releaseState.value = listOf(release(3))
            runCurrent()

            val replacement = viewModel.uiState.value as TodayUiState.Content
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

    private fun TestRepositories.viewModel(): TodayViewModel =
        TodayViewModel(
            accountId = accountId,
            observeTodayQueue = ObserveTodayQueueUseCase(this, this, fixedClock()),
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
