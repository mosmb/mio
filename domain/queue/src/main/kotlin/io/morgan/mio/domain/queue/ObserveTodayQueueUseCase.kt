package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock

public class ObserveTodayQueueUseCase(
    private val collectionRepository: CollectionRepository,
    private val mediaRepository: MediaRepository,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    public operator fun invoke(accountId: AccountId): Flow<TodayQueue> =
        collectionRepository.observeCollection(accountId).flatMapLatest { entries ->
            observeQueue(entries)
        }

    private fun observeQueue(entries: List<CollectionEntry>): Flow<TodayQueue> {
        if (entries.isEmpty()) {
            return flowOf(TodayQueueSelector.select(emptyList(), clock.now()))
        }

        val candidateFlows = entries.map(::observeCandidate)
        return combine(candidateFlows) { candidates ->
            TodayQueueSelector.select(candidates.filterNotNull(), clock.now())
        }
    }

    private fun observeCandidate(entry: CollectionEntry): Flow<TodayQueueCandidate?> =
        combine(
            mediaRepository.observeMedia(entry.mediaId),
            mediaRepository.observeReleases(entry.mediaId),
        ) { media, releases ->
            media?.let { currentMedia -> currentMedia.toCandidate(entry, releases) }
        }

    private fun Media.toCandidate(
        entry: CollectionEntry,
        releases: List<Release>,
    ): TodayQueueCandidate = TodayQueueCandidate(entry = entry, media = this, releases = releases)
}
