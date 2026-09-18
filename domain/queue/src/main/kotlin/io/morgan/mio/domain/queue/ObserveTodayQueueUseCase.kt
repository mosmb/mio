package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

public class ObserveTodayQueueUseCase(
    private val collectionRepository: CollectionRepository,
    private val mediaRepository: io.morgan.mio.domain.media.MediaRepository,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    public operator fun invoke(accountId: AccountId): Flow<TodayQueue> =
        collectionRepository.observeCollection(accountId).flatMapLatest { entries ->
            if (entries.isEmpty()) {
                flowOf(TodayQueueSelector.select(emptyList(), clock.now()))
            } else {
                TodayQueueObservation.observeCandidates(entries, mediaRepository).flatMapLatest { candidates ->
                    TodayQueueObservation
                        .localClockReevaluations(candidates, clock)
                        .map { TodayQueueSelector.select(candidates, clock.now()) }
                }
            }
        }
}
