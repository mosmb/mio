package io.morgan.mio.domain.queue

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTodayQueueUseCaseTest {
    private val accountId = AccountId("account-1")
    private val now = Instant.parse("2026-08-23T12:00:00Z")

    @Test
    fun emptyRoomCollectionEmitsNeutralQueueWithoutRefreshing() =
        runTest {
            val collection = TestCollectionRepository()
            val media = TestMediaRepository()
            val flow = ObserveTodayQueueUseCase(collection, media, fixedClock())(accountId)

            assertEquals(caughtUpQueue(), flow.firstValue())
            assertTrue(media.refreshCalls.isEmpty())
            assertTrue(collection.refreshCalls.isEmpty())
        }

    @Test
    fun roomMediaAndReleasesReplaceTheRecommendationReactively() =
        runTest {
            val collection = TestCollectionRepository()
            val media = TestMediaRepository()
            val entry = entry(mediaId = "media-1", updatedAt = "2026-08-23T10:00:00Z")
            collection.entries.value = listOf(entry)
            media.media("media-1").value = media("media-1")
            val observed = mutableListOf<TodayQueue>()
            val job =
                launch { ObserveTodayQueueUseCase(collection, media, fixedClock())(accountId).collect(observed::add) }

            runCurrent()
            assertNull(observed.last().now)
            assertEquals(
                MediaId("media-1"),
                observed
                    .last()
                    .caughtUp
                    ?.artwork
                    ?.single()
                    ?.mediaId,
            )
            media.releases("media-1").value = listOf(release("media-1", 2))
            runCurrent()
            assertEquals(MediaId("media-1"), observed.last().now?.mediaId)

            val newer = entry(mediaId = "media-2", updatedAt = "2026-08-23T11:00:00Z")
            collection.entries.value = listOf(entry, newer)
            media.media("media-2").value = media("media-2")
            media.releases("media-2").value = listOf(release("media-2", 2))
            runCurrent()
            assertEquals(MediaId("media-2"), observed.last().now?.mediaId)
            assertTrue(media.refreshCalls.isEmpty())
            job.cancel()
        }

    @Test
    fun missingMediaIsDroppedAndRemovingEntriesCancelsObsoleteSubscriptions() =
        runTest {
            val collection = TestCollectionRepository()
            val media = TestMediaRepository()
            val entry = entry(mediaId = "removed")
            collection.entries.value = listOf(entry)
            val emissions = mutableListOf<TodayQueue>()
            val job =
                launch { ObserveTodayQueueUseCase(collection, media, fixedClock())(accountId).collect(emissions::add) }

            runCurrent()
            assertEquals(caughtUpQueue(), emissions.last())
            assertTrue(media.mediaStarts("removed") > 0)
            assertTrue(media.releaseStarts("removed") > 0)

            collection.entries.value = emptyList()
            runCurrent()
            assertTrue(media.mediaCancellations("removed") > 0)
            assertTrue(media.releaseCancellations("removed") > 0)
            val emissionCount = emissions.size
            media.media("removed").value = media("removed")
            media.releases("removed").value = listOf(release("removed", 2))
            runCurrent()
            assertEquals(emissionCount, emissions.size)
            job.cancel()
        }

    @Test
    fun cachedNewRecomputesStaleAfterClockBoundaryOnRoomEmissionWithoutRefresh() =
        runTest {
            val collection = TestCollectionRepository()
            val media = TestMediaRepository()
            val mutableClock = MutableTestClock(now)
            val currentEntry = entry(mediaId = "current", updatedAt = "2026-08-23T11:00:00Z")
            val newEntry = entry(mediaId = "new", updatedAt = "2026-08-23T10:00:00Z")
            collection.entries.value = listOf(currentEntry, newEntry)
            media.media("current").value = media("current")
            media.media("new").value = media("new")
            media.releases("current").value = listOf(release("current", 2, ReleaseTime.Trusted(now)))
            media.releases("new").value = listOf(release("new", 2, ReleaseTime.Trusted(now - 7.days)))
            val observed = mutableListOf<TodayQueue>()
            val job =
                launch {
                    ObserveTodayQueueUseCase(collection, media, mutableClock)(accountId).collect(observed::add)
                }

            runCurrent()
            assertEquals(listOf(MediaId("new")), observed.last().newRecommendations.map { item -> item.mediaId })

            mutableClock.current = now + 1.hours
            media.releases("current").value =
                listOf(
                    release("current", 2, ReleaseTime.Trusted(now)),
                    release("current", 2, ReleaseTime.Trusted(now)),
                )
            runCurrent()

            assertTrue(observed.last().newRecommendations.isEmpty())
            assertEquals(1, observed.last().remainingReadyCount)
            assertTrue(media.refreshCalls.isEmpty())
            assertTrue(collection.refreshCalls.isEmpty())
            job.cancel()
        }

    @Test
    fun outerCollectionCancellationCancelsEveryPerMediaFlow() =
        runTest {
            val collection = TestCollectionRepository()
            val media = TestMediaRepository()
            collection.entries.value = listOf(entry(mediaId = "a"), entry(mediaId = "b"))
            val job = launch { ObserveTodayQueueUseCase(collection, media, fixedClock())(accountId).collect() }
            runCurrent()
            assertTrue(media.mediaStarts("a") > 0)
            assertTrue(media.mediaStarts("b") > 0)
            assertTrue(media.releaseStarts("a") > 0)
            assertTrue(media.releaseStarts("b") > 0)
            job.cancel()
            runCurrent()
            assertTrue(media.mediaCancellations("a") > 0)
            assertTrue(media.mediaCancellations("b") > 0)
            assertTrue(media.releaseCancellations("a") > 0)
            assertTrue(media.releaseCancellations("b") > 0)
        }

    private fun fixedClock(): Clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private fun caughtUpQueue(): TodayQueue =
        TodayQueue(
            now = null,
            newRecommendations = emptyList(),
            remainingReadyCount = 0,
            caughtUp = CaughtUpSummary(artwork = emptyList(), nextRelease = null),
        )

    private fun entry(
        mediaId: String,
        updatedAt: String = "2026-08-23T11:00:00Z",
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId("entry-$mediaId"),
            accountId = accountId,
            mediaId = MediaId(mediaId),
            status = CollectionStatus.CURRENT,
            progress = ProgressValue(1),
            progressUnit = ProgressUnit.EPISODE,
            total = UnitTotal(12),
            updatedAt = Instant.parse(updatedAt),
        )

    private fun media(id: String): Media =
        Media(
            id = MediaId(id),
            type = MediaType.ANIME,
            title = MediaTitle("Title $id"),
            format = MediaFormat.TV,
            publicationStatus = MediaPublicationStatus.RELEASING,
            synopsis = null,
            artwork = Artwork(null, null),
            totals = MediaTotals(UnitTotal(12), null, null),
            genres = emptyList(),
            relatedMediaIds = emptyList(),
        )

    private fun release(
        mediaId: String,
        number: Int,
        time: io.morgan.mio.domain.media.ReleaseTime =
            io.morgan.mio.domain.media.ReleaseTime
                .Trusted(now),
    ): Release =
        Release(
            mediaId = MediaId(mediaId),
            unit = ProgressUnit.EPISODE,
            number = UnitNumber(number),
            time = time,
        )
}

private class MutableTestClock(
    var current: Instant,
) : Clock {
    override fun now(): Instant = current
}

private class TestCollectionRepository : CollectionRepository {
    val entries = MutableStateFlow<List<CollectionEntry>>(emptyList())
    val refreshCalls = mutableListOf<AccountId>()

    override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        entries.map { values -> values.filter { entry -> entry.accountId == accountId } }

    override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        entries.map { values -> values.firstOrNull { entry -> entry.id == entryId } }

    override fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> =
        entries.map { values ->
            values.firstOrNull { entry -> entry.accountId == accountId && entry.mediaId == mediaId }
        }

    override suspend fun refreshCollection(accountId: AccountId) {
        refreshCalls += accountId
    }
}

private class TestMediaRepository : MediaRepository {
    private val mediaStates = mutableMapOf<MediaId, MutableStateFlow<Media?>>()
    private val releaseStates = mutableMapOf<MediaId, MutableStateFlow<List<Release>>>()
    private val mediaStartCounts = mutableMapOf<MediaId, AtomicInteger>()
    private val mediaCancellationCounts = mutableMapOf<MediaId, AtomicInteger>()
    private val releaseStartCounts = mutableMapOf<MediaId, AtomicInteger>()
    private val releaseCancellationCounts = mutableMapOf<MediaId, AtomicInteger>()
    val refreshCalls = mutableListOf<MediaId>()

    fun media(mediaId: String): MutableStateFlow<Media?> =
        mediaStates.getOrPut(MediaId(mediaId)) { MutableStateFlow(null) }

    fun releases(mediaId: String): MutableStateFlow<List<Release>> =
        releaseStates.getOrPut(MediaId(mediaId)) { MutableStateFlow(emptyList()) }

    fun mediaStarts(mediaId: String): Int = mediaStartCounts[MediaId(mediaId)]?.get() ?: 0

    fun mediaCancellations(mediaId: String): Int = mediaCancellationCounts[MediaId(mediaId)]?.get() ?: 0

    fun releaseStarts(mediaId: String): Int = releaseStartCounts[MediaId(mediaId)]?.get() ?: 0

    fun releaseCancellations(mediaId: String): Int = releaseCancellationCounts[MediaId(mediaId)]?.get() ?: 0

    override fun observeMedia(mediaId: MediaId): Flow<Media?> =
        tracked(
            mediaId = mediaId,
            state = mediaStates.getOrPut(mediaId) { MutableStateFlow(null) },
            startCounts = mediaStartCounts,
            cancellationCounts = mediaCancellationCounts,
        )

    override fun observeReleases(mediaId: MediaId): Flow<List<Release>> =
        tracked(
            mediaId = mediaId,
            state = releases(mediaId.value),
            startCounts = releaseStartCounts,
            cancellationCounts = releaseCancellationCounts,
        )

    override fun observeRelatedMedia(mediaId: MediaId): Flow<List<Media>> = flow { emit(emptyList()) }

    override suspend fun refreshMedia(mediaId: MediaId) {
        refreshCalls += mediaId
    }

    private fun <T> tracked(
        mediaId: MediaId,
        state: MutableStateFlow<T>,
        startCounts: MutableMap<MediaId, AtomicInteger>,
        cancellationCounts: MutableMap<MediaId, AtomicInteger>,
    ): Flow<T> =
        flow {
            startCounts.getOrPut(mediaId) { AtomicInteger() }.incrementAndGet()
            try {
                emitAll(state)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                cancellationCounts.getOrPut(mediaId) { AtomicInteger() }.incrementAndGet()
            }
        }
}

private suspend fun <T> Flow<T>.firstValue(): T = first()
