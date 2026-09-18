package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.media.ReleaseTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal object TodayQueueObservation {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCandidates(
        entries: List<CollectionEntry>,
        mediaRepository: MediaRepository,
    ): Flow<List<TodayQueueCandidate>> {
        if (entries.isEmpty()) return flowOf(emptyList())

        return combine(entries.map { entry -> observeCandidate(entry, mediaRepository) }) { candidates ->
            candidates.filterNotNull()
        }
    }

    private fun observeCandidate(
        entry: CollectionEntry,
        mediaRepository: MediaRepository,
    ): Flow<TodayQueueCandidate?> =
        combine(
            mediaRepository.observeMedia(entry.mediaId),
            mediaRepository.observeReleases(entry.mediaId),
        ) { media, releases ->
            media?.let { currentMedia -> TodayQueueCandidate(entry = entry, media = currentMedia, releases = releases) }
        }

    fun localClockReevaluations(
        candidates: List<TodayQueueCandidate>,
        clock: Clock,
    ): Flow<Unit> =
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
                val instant = (release.time as? ReleaseTime.Trusted)?.instant ?: return@mapNotNull null
                when {
                    instant > now -> instant
                    instant >= now - NEW_WINDOW -> instant + NEW_WINDOW + 1.milliseconds
                    else -> null
                }
            }
        }.minOrNull()

    private val MAX_REEVALUATION = 1.minutes
    private val MIN_REEVALUATION = 1.milliseconds
    private val NEW_WINDOW = 7.days
}
