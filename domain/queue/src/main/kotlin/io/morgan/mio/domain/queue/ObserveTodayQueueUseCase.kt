package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeQueue(entries: List<CollectionEntry>): Flow<TodayQueue> {
        if (entries.isEmpty()) {
            return flowOf(TodayQueueSelector.select(emptyList(), clock.now()))
        }

        val candidateFlows = entries.map(::observeCandidate)
        return combine(candidateFlows) { candidates -> candidates.filterNotNull() }
            .flatMapLatest { candidates ->
                localClockReevaluations(candidates).map { TodayQueueSelector.select(candidates, clock.now()) }
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

    private fun localClockReevaluations(candidates: List<TodayQueueCandidate>): Flow<Unit> =
        flow {
            emit(Unit)
            while (currentCoroutineContext().isActive) {
                val now = clock.now()
                val nextBoundary = candidates.nextLocalBoundary(now)
                val untilBoundary = nextBoundary?.let { boundary -> boundary - now }
                val wait =
                    (untilBoundary ?: MAX_REEVALUATION)
                        .coerceAtLeast(MIN_REEVALUATION)
                        .coerceAtMost(MAX_REEVALUATION)
                delay(wait)
                emit(Unit)
            }
        }

    private fun List<TodayQueueCandidate>.nextLocalBoundary(now: Instant): Instant? =
        flatMap { candidate ->
            candidate.releases.mapNotNull { release ->
                val instant =
                    (release.time as? io.morgan.mio.domain.media.ReleaseTime.Trusted)?.instant
                        ?: return@mapNotNull null
                when {
                    instant > now -> instant
                    instant >= now - NEW_WINDOW -> instant + NEW_WINDOW + 1.milliseconds
                    else -> null
                }
            }
        }.minOrNull()

    private companion object {
        val MAX_REEVALUATION = 1.minutes
        val MIN_REEVALUATION = 1.milliseconds
        val NEW_WINDOW = 7.days
    }
}
