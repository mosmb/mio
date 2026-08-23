package io.morgan.mio.feature.media.impl

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

            assertEquals(MediaDetailUiState.Loading, viewModel.uiState.value)
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
            assertEquals(MediaDetailUiState.Error, viewModel.uiState.value)

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

            assertEquals(MediaDetailUiState.Loading, viewModel.uiState.value)
            assertEquals(listOf(mediaId), repository.refreshCalls)
        }

    private fun createViewModel(repository: TestMediaRepository): MediaDetailViewModel =
        MediaDetailViewModel(
            mediaId = mediaId,
            accountId = accountId,
            mediaRepository = repository,
            collectionRepository = TestCollectionRepository(),
        )

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
