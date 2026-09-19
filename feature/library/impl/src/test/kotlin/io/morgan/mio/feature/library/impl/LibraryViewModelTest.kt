package io.morgan.mio.feature.library.impl

import androidx.lifecycle.ViewModelStore
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
import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.queue.ObserveReadyMediaIdsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val now = Instant.parse("2026-09-18T12:00:00Z")

    @Test
    fun initialCurrentAndImmediateMediaStatusAndReadyFiltersKeepImportedRows() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val collection = TestCollectionRepository()
            val media = TestMediaRepository()
            val entries =
                listOf(
                    entry("anime-ready", "account-1", CollectionStatus.CURRENT, updatedAt = 7),
                    entry("manga-current", "account-1", CollectionStatus.CURRENT, updatedAt = 6),
                    entry("anime-planned", "account-1", CollectionStatus.PLANNED, updatedAt = 5),
                    entry("manga-paused", "account-1", CollectionStatus.PAUSED, ProgressUnit.CHAPTER, updatedAt = 4),
                    entry("anime-completed", "account-1", CollectionStatus.COMPLETED, updatedAt = 3),
                    entry("manga-dropped", "account-1", CollectionStatus.DROPPED, ProgressUnit.CHAPTER, updatedAt = 2),
                    entry("anime-repeating", "account-1", CollectionStatus.REPEATING, updatedAt = 1),
                    entry("missing-metadata", "account-1", CollectionStatus.CURRENT, updatedAt = 0),
                )
            collection.entries.value = entries
            entries.filterNot { it.mediaId.value == "missing-metadata" }.forEach { row ->
                val type = if (row.mediaId.value.startsWith("manga")) MediaType.MANGA else MediaType.ANIME
                media.media(row.mediaId.value).value = media(row.mediaId.value, type)
            }
            media.releases("anime-ready").value =
                listOf(release("anime-ready", 2, ReleaseTime.Trusted(now - kotlin.time.Duration.parse("1h"))))
            val (store, viewModel) = viewModel(collection, media)

            try {
                runCurrent()
                assertEquals(LibraryStatusFilter.CURRENT, viewModel.uiState.value.statusFilter)
                assertEquals(3, viewModel.uiState.value.rows.size)
                assertEquals(
                    "missing-metadata",
                    viewModel.uiState.value.rows
                        .single { it.title.isBlank() }
                        .mediaId,
                )
                assertEquals(
                    null,
                    viewModel.uiState.value.rows
                        .single { it.title.isBlank() }
                        .mediaType,
                )

                viewModel.onAction(LibraryAction.SelectMediaFilter(LibraryMediaFilter.ANIME))
                assertEquals(
                    listOf("anime-ready"),
                    viewModel.uiState.value.rows
                        .map(LibraryRowUiModel::mediaId),
                )
                viewModel.onAction(LibraryAction.SelectMediaFilter(LibraryMediaFilter.MANGA))
                assertEquals(
                    listOf("manga-current"),
                    viewModel.uiState.value.rows
                        .map(LibraryRowUiModel::mediaId),
                )

                viewModel.onAction(LibraryAction.SelectStatusFilter(LibraryStatusFilter.ALL))
                assertEquals(3, viewModel.uiState.value.rows.size)
                LibraryStatusFilter.entries
                    .filter { filter -> filter !in setOf(LibraryStatusFilter.ALL, LibraryStatusFilter.READY) }
                    .forEach { filter ->
                        viewModel.onAction(LibraryAction.SelectMediaFilter(LibraryMediaFilter.ALL))
                        viewModel.onAction(LibraryAction.SelectStatusFilter(filter))
                        val rows = viewModel.uiState.value.rows
                        assertTrue(rows.isNotEmpty())
                        assertTrue(rows.all { row -> row.status.name == filter.name })
                    }
                assertTrue(LibraryStatusFilter.REPEATING in viewModel.uiState.value.availableStatusFilters)

                viewModel.onAction(LibraryAction.SelectStatusFilter(LibraryStatusFilter.READY))
                assertEquals(
                    listOf("anime-ready"),
                    viewModel.uiState.value.rows
                        .map(LibraryRowUiModel::mediaId),
                )
                viewModel.onAction(LibraryAction.SelectMediaFilter(LibraryMediaFilter.MANGA))
                assertTrue(viewModel.uiState.value.isFilteredEmpty)
                viewModel.onAction(LibraryAction.ClearFilters)
                assertEquals(LibraryMediaFilter.ALL, viewModel.uiState.value.mediaFilter)
                assertEquals(LibraryStatusFilter.ALL, viewModel.uiState.value.statusFilter)
                assertEquals(entries.size, viewModel.uiState.value.rows.size)

                // Live metadata and collection updates must not require another filter selection.
                media.media("missing-metadata").value = media("missing-metadata", MediaType.MANGA)
                runCurrent()
                val restored =
                    viewModel.uiState.value.rows
                        .single { it.mediaId == "missing-metadata" }
                assertEquals("Title missing-metadata", restored.title)
                assertEquals(LibraryRowMediaType.MANGA, restored.mediaType)
                collection.entries.value = entries.filterNot { it.mediaId.value == "anime-ready" }
                runCurrent()
                assertEquals(entries.size - 1, viewModel.uiState.value.rows.size)
                assertFalse(
                    viewModel.uiState.value.rows
                        .any { it.mediaId == "anime-ready" },
                )
                viewModel.onAction(LibraryAction.SelectStatusFilter(LibraryStatusFilter.READY))
                assertTrue(viewModel.uiState.value.isFilteredEmpty)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun initialReadyRequestSelectsReadyOnceAndAccountsKeepIndependentFilters() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val collection = TestCollectionRepository()
            val media = TestMediaRepository()
            val accountOneEntry = entry("one-ready", "account-1", CollectionStatus.CURRENT)
            val accountOneCompleted = entry("one-completed", "account-1", CollectionStatus.COMPLETED)
            val accountTwoCurrent = entry("two-current", "account-2", CollectionStatus.CURRENT)
            val accountTwoPlanned = entry("two-planned", "account-2", CollectionStatus.PLANNED)
            collection.entries.value =
                listOf(accountOneEntry, accountOneCompleted, accountTwoCurrent, accountTwoPlanned)
            listOf(accountOneEntry, accountOneCompleted, accountTwoCurrent, accountTwoPlanned).forEach { row ->
                media.media(row.mediaId.value).value = media(row.mediaId.value, MediaType.ANIME)
            }
            media.releases("one-ready").value = listOf(release("one-ready", 2, ReleaseTime.Trusted(now)))
            val (storeOne, first) = viewModel(collection, media, accountId = "account-1", initialReady = true)
            val (storeTwo, second) = viewModel(collection, media, accountId = "account-2")

            try {
                runCurrent()
                assertEquals(LibraryStatusFilter.READY, first.uiState.value.statusFilter)
                assertEquals(
                    listOf("one-ready"),
                    first.uiState.value.rows
                        .map(LibraryRowUiModel::mediaId),
                )
                assertEquals(
                    listOf("two-current"),
                    second.uiState.value.rows
                        .map(LibraryRowUiModel::mediaId),
                )
                first.onAction(LibraryAction.SelectStatusFilter(LibraryStatusFilter.ALL))
                assertEquals(2, first.uiState.value.rows.size)
                assertEquals(1, second.uiState.value.rows.size)
                assertEquals(LibraryStatusFilter.CURRENT, second.uiState.value.statusFilter)
            } finally {
                storeOne.clear()
                storeTwo.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun emptyAndFilteredEmptyAreDifferentAndRecoverableObservationFailureKeepsCachedRows() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val emptyCollection = TestCollectionRepository()
            val emptyMedia = TestMediaRepository()
            val (emptyStore, emptyViewModel) = viewModel(emptyCollection, emptyMedia)
            val collection = TestCollectionRepository()
            val media = TestMediaRepository()
            val saved = entry("saved", "account-1", CollectionStatus.COMPLETED)
            collection.entries.value = listOf(saved)
            media.media("saved").value = media("saved", MediaType.MANGA)
            val (store, viewModel) = viewModel(collection, media)

            try {
                runCurrent()
                assertTrue(emptyViewModel.uiState.value.isCollectionEmpty)
                assertFalse(emptyViewModel.uiState.value.isFilteredEmpty)
                assertTrue(viewModel.uiState.value.isFilteredEmpty)
                viewModel.onAction(LibraryAction.ClearFilters)
                assertEquals(
                    listOf("saved"),
                    viewModel.uiState.value.rows
                        .map(LibraryRowUiModel::mediaId),
                )

                collection.failure.value = IOException("temporary Room observation failure")
                runCurrent()
                assertTrue(viewModel.uiState.value.observationFailed)
                assertEquals(
                    listOf("saved"),
                    viewModel.uiState.value.rows
                        .map(LibraryRowUiModel::mediaId),
                )

                collection.failure.value = null
                viewModel.onAction(LibraryAction.RetryObservation)
                runCurrent()
                assertFalse(viewModel.uiState.value.observationFailed)
                assertEquals(
                    listOf("saved"),
                    viewModel.uiState.value.rows
                        .map(LibraryRowUiModel::mediaId),
                )
            } finally {
                emptyStore.clear()
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    private fun viewModel(
        collection: TestCollectionRepository,
        media: TestMediaRepository,
        accountId: String = "account-1",
        initialReady: Boolean = false,
    ): Pair<ViewModelStore, LibraryViewModel> {
        val store = ViewModelStore()
        val viewModel =
            LibraryViewModel(
                accountId = AccountId(accountId),
                collectionRepository = collection,
                mediaRepository = media,
                observeReadyMediaIds = ObserveReadyMediaIdsUseCase(collection, media, fixedClock()),
                initialReady = initialReady,
            )
        store.put("library", viewModel)
        return store to viewModel
    }

    private fun entry(
        mediaId: String,
        accountId: String,
        status: CollectionStatus,
        progressUnit: ProgressUnit = ProgressUnit.EPISODE,
        updatedAt: Int = 0,
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId("entry-$mediaId"),
            accountId = AccountId(accountId),
            mediaId = MediaId(mediaId),
            status = status,
            progress = ProgressValue(1),
            progressUnit = progressUnit,
            total = UnitTotal(12),
            updatedAt = now - kotlin.time.Duration.parse("${updatedAt}h"),
        )

    private fun media(
        id: String,
        type: MediaType,
    ): Media =
        Media(
            id = MediaId(id),
            type = type,
            title = MediaTitle("Title $id"),
            format = MediaFormat.TV,
            publicationStatus = MediaPublicationStatus.RELEASING,
            synopsis = null,
            artwork = Artwork(poster = ImageReference("https://example.test/$id.jpg"), banner = null),
            totals = MediaTotals(UnitTotal(12), UnitTotal(24), null),
            genres = emptyList(),
            relatedMediaIds = emptyList(),
        )

    private fun release(
        mediaId: String,
        number: Int,
        time: ReleaseTime,
    ): Release =
        Release(
            mediaId = MediaId(mediaId),
            unit = ProgressUnit.EPISODE,
            number = UnitNumber(number),
            time = time,
        )

    private fun fixedClock(): Clock =
        object : Clock {
            override fun now(): Instant = now
        }
}

private class TestCollectionRepository : CollectionRepository {
    val entries = MutableStateFlow<List<CollectionEntry>>(emptyList())
    val failure = MutableStateFlow<IOException?>(null)

    override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        combine(entries, failure) { current, error ->
            error?.let { throw it }
            current.filter { item -> item.accountId == accountId }
        }

    override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        entries.combine(failure) { current, error ->
            error?.let { throw it }
            current.firstOrNull { item -> item.id == entryId }
        }

    override fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> =
        entries.combine(failure) { current, error ->
            error?.let { throw it }
            current.firstOrNull { item -> item.accountId == accountId && item.mediaId == mediaId }
        }

    override suspend fun readEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): CollectionEntry? = entries.value.firstOrNull { item -> item.accountId == accountId && item.mediaId == mediaId }

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

    override suspend fun refreshCollection(accountId: AccountId) = Unit
}

private class TestMediaRepository : MediaRepository {
    private val mediaById = mutableMapOf<MediaId, MutableStateFlow<Media?>>()
    private val releasesById = mutableMapOf<MediaId, MutableStateFlow<List<Release>>>()

    fun media(mediaId: String): MutableStateFlow<Media?> =
        mediaById.getOrPut(MediaId(mediaId)) { MutableStateFlow(null) }

    fun releases(mediaId: String): MutableStateFlow<List<Release>> =
        releasesById.getOrPut(MediaId(mediaId)) { MutableStateFlow(emptyList()) }

    override fun observeMedia(mediaId: MediaId): Flow<Media?> = mediaById.getOrPut(mediaId) { MutableStateFlow(null) }

    override fun observeRelatedMedia(mediaId: MediaId): Flow<List<Media>> = flowOf(emptyList())

    override fun observeReleases(mediaId: MediaId): Flow<List<Release>> =
        releasesById.getOrPut(mediaId) { MutableStateFlow(emptyList()) }

    override suspend fun refreshMedia(mediaId: MediaId) = Unit
}
