package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

public class ObserveReadyMediaIdsUseCase(
    private val collectionRepository: CollectionRepository,
    private val mediaRepository: MediaRepository,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    public operator fun invoke(accountId: AccountId): Flow<Set<MediaId>> =
        collectionRepository
            .observeCollection(accountId)
            .flatMapLatest { entries ->
                val currentEntries = entries.filter { entry -> entry.status == CollectionStatus.CURRENT }
                if (currentEntries.isEmpty()) {
                    flowOf(emptySet())
                } else {
                    TodayQueueObservation
                        .observeCandidates(currentEntries, mediaRepository)
                        .flatMapLatest { candidates ->
                            if (candidates.isEmpty()) {
                                flowOf(emptySet())
                            } else {
                                TodayQueueObservation.localClockReevaluations(candidates, clock).map {
                                    candidates
                                        .mapNotNull { candidate ->
                                            ReadyCalculator
                                                .calculate(candidate, clock.now())
                                                ?.candidate
                                                ?.media
                                                ?.id
                                        }.toSet()
                                }
                            }
                        }
                }
            }.distinctUntilChanged()
}
